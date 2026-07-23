#!/usr/bin/env python3
"""CI 安卓打包注入：对 `tauri android init` 重新生成的 android 工程做必要回填。

由于 `tauri android init` 会抹掉一切手工改动，本脚本在 init 之后运行，强制保证：

1) release 构建块放行明文 HTTP：
       manifestPlaceholders["usesCleartextTraffic"] = "true"
   否则 Android 在 release 包里禁止 http://，App 连不上 http 后端（服务器后端只提供 http）。

2) defaultConfig 也放行明文 HTTP（防御不同 Tauri 模板差异；有些模板 defaultConfig 不带）。

3) release 签名块读取 app/keystore.properties（由 CI 通过 Secret 注入），
   并保证 release 块引用该签名配置，否则 release 包未签名、Android 直接拒绝安装。

4) **NEW** 生成 res/xml/network_security_config.xml 并注入 AndroidManifest.xml：
   Tauri v2 的 WebView 源是 https://tauri.localhost（HTTPS 来源），
   从 HTTPS 页面 fetch http:// 后端会触发 WebView 的「混合内容（Mixed Content）」拦截，
   请求在 WebView 层被静默阻止（axios 报 Network Error），根本未到达网络层。
   Android 的 network_security_config 可以精确覆盖 WebView 的混合内容策略，
   允许特定域（或全局）的明文 HTTP 流量。

本脚本只做纯字符串改写，不依赖 Android / Gradle / NDK 工具链，可被单元测试直接调用。
用法：python3 inject_android_release_config.py <build.gradle.kts 路径>
"""

import os
import sys


SIGNING_BLOCK = '''    // A2: release 签名配置（CI 自动注入，勿手改）
    signingConfigs {
        create("release") {
            val keystorePropsFile = rootProject.file("app/keystore.properties")
            if (keystorePropsFile.exists()) {
                val props = Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                storeFile = rootProject.file("app/" + props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
            }
        }
    }
'''

CLEARTEXT_LINE = 'manifestPlaceholders["usesCleartextTraffic"] = "true"'

# 通过 Gradle manifestPlaceholders 让 networkSecurityConfig 引用存活
# （不依赖 Python 改 AndroidManifest.xml，因为 tauri android build 可能重新 init 抹掉）
NSC_PLACEHOLDER_LINE = 'manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config"'

NETWORK_SECURITY_CONFIG_XML = '''<?xml version="1.0" encoding="utf-8"?>
<!-- A2: 自动注入，勿手改 -->
<!--
  核心作用：Tauri v2 WebView 源是 https://tauri.localhost，
  从 HTTPS 页面向 http:// 后端发请求会被 WebView 的「Mixed Content」策略拦截。
  Android 的 network_security_config 可以覆盖 WebView 的混合内容策略，
  仅靠 manifest 的 usesCleartextTraffic 不够。

  详见 docs/debug-build-troubleshooting.md §混合内容拦截
-->
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
'''

# AndroidManifest.xml 的 <application> 标签中要注入的属性
# 使用 manifestPlaceholder 占位符语法，配合 build.gradle.kts 中的
# manifestPlaceholders["networkSecurityConfig"] 在 Gradle 构建时解析。
# 这样即使 tauri android build 内部重新 init 抹掉该属性，
# 只要 build.gradle.kts 中有对应的 manifestPlaceholders 配置（通过 Python 注入），
# Gradle 就会在构建时填充。
NSC_ATTR = 'android:networkSecurityConfig="${networkSecurityConfig}"'


def _find_block(s, marker):
    """找到以 marker 开头的代码块，返回 (左花括号索引, 右花括号索引)；找不到返回 None。"""
    start = s.find(marker)
    if start == -1:
        return None
    i = s.index("{", start)
    depth = 0
    j = i
    while j < len(s):
        if s[j] == "{":
            depth += 1
        elif s[j] == "}":
            depth -= 1
            if depth == 0:
                break
        j += 1
    return (i, j)


def _ensure_cleartext_in_block(s, marker):
    """确保以 marker 开头的代码块体内包含 CLEARTEXT_LINE（幂等）。"""
    pos = _find_block(s, marker)
    if pos is None:
        return s
    i, j = pos
    if CLEARTEXT_LINE in s[i:j + 1]:
        return s  # 已存在，跳过
    # 在 '{' 之后插入（8 空格缩进，与块内其他语句对齐）
    return s[:i + 1] + "\n        " + CLEARTEXT_LINE + s[i + 1:]


def _ensure_nsc_placeholder_in_block(s, marker):
    """
    确保以 marker 开头的代码块体内包含 NSC_PLACEHOLDER_LINE（幂等）。
    这是 Gradle manifestPlaceholders 方式的核心 ——
    不依赖磁盘上的 AndroidManifest.xml（会被 Tauri re-init 抹掉），
    而是在 build.gradle.kts 中配置，Gradle 构建时动态填充。
    """
    pos = _find_block(s, marker)
    if pos is None:
        return s
    i, j = pos
    if NSC_PLACEHOLDER_LINE in s[i:j + 1]:
        return s  # 已存在，跳过
    return s[:i + 1] + "\n        " + NSC_PLACEHOLDER_LINE + s[i + 1:]


GRADLE_NSC_TASK = '''
// A2: generate network_security_config.xml（防止被 tauri re-init 抹掉）
afterEvaluate {
    // 在 Gradle 配置阶段直接写文件（早于所有 task，不会被 tauri 重新 init 影响）
    val nscXmlDir = file("src/main/res/xml")
    nscXmlDir.mkdirs()
    val nscFile = file("$nscXmlDir/network_security_config.xml")
    // 始终覆盖写，确保内容不会被 tauri init 还原为空模板
    nscFile.writeText("""<?xml version="1.0" encoding="utf-8"?>
<!-- A2: Gradle 构建期自动生成，勿手改 -->
<!--
  核心作用：Tauri v2 WebView 源是 https://tauri.localhost，
  从 HTTPS 页面向 http:// 后端发请求会被 WebView 的「Mixed Content」策略拦截。
  Android 的 network_security_config 可以覆盖 WebView 的混合内容策略，
  仅靠 manifest 的 usesCleartextTraffic 不够。
-->
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>""")
    println("==> [Gradle] 已确保 network_security_config.xml 存在（Gradle 构建期生成）")
}
'''


def _inject_nsc_gradle_task(app_dir):
    """
    向 build.gradle.kts 中注入 Gradle afterEvaluate 钩子，
    在构建配置阶段生成 network_security_config.xml。

    原因：tauri android build 可能在内部重新调用 tauri android init，
    这会抹掉 Python 脚本写入的 res/xml/network_security_config.xml。
    Gradle 的 afterEvaluate 在 init 之后、任务执行之前运行，
    能确保 XML 文件始终存在。
    """
    gradle_path = os.path.join(app_dir, "build.gradle.kts")
    content = open(gradle_path, encoding="utf-8").read()

    MARKER = "// A2: generate network_security_config.xml"
    if MARKER in content:
        print(f"==> Gradle NSC 生成任务已存在，跳过")
        return

    new_content = content.rstrip() + "\n" + GRADLE_NSC_TASK + "\n"
    open(gradle_path, "w", encoding="utf-8").write(new_content)
    print(f"==> 已注入 Gradle NSC 生成任务 → {gradle_path}")


def _inject_nsc_into_manifest(manifest_path, app_dir):
    """
    向 AndroidManifest.xml 的 <application> 标签注入
    android:networkSecurityConfig="@xml/network_security_config"。

    幂等：如果标签内已存在该属性，跳过。
    """
    if not os.path.isfile(manifest_path):
        print(f"==> [警告] 未找到 AndroidManifest.xml：{manifest_path}，跳过 networkSecurityConfig 注入")
        return

    s = open(manifest_path, encoding="utf-8").read()

    # 幂等检查：已注入则跳过
    if NSC_ATTR in s:
        print(f"==> networkSecurityConfig 已存在，跳过：{manifest_path}")
        return

    # 找到 <application 标签的结束 '>'
    app_start = s.find("<application")
    if app_start == -1:
        print(f"==> [警告] 未找到 <application 标签：{manifest_path}")
        return

    # 从 <application 开始找到标签结束的 '>'（注意：可能跨多行）
    tag_end = s.find(">", app_start)
    if tag_end == -1:
        print(f"==> [警告] <application 标签格式异常：{manifest_path}")
        return

    # 在 '>' 之前插入属性
    # 格式：在 '>' 前加一个换行 + 缩进 + 属性
    attr_line = f'\n        {NSC_ATTR}'
    s = s[:tag_end] + attr_line + s[tag_end:]

    open(manifest_path, "w", encoding="utf-8").write(s)
    print(f"==> 已注入 networkSecurityConfig → {manifest_path}")


def _write_network_security_config(app_dir):
    """
    创建 res/xml/network_security_config.xml（幂等：已存在则跳过）。
    """
    res_xml_dir = os.path.join(app_dir, "src", "main", "res", "xml")
    config_file = os.path.join(res_xml_dir, "network_security_config.xml")

    if os.path.isfile(config_file):
        # 幂等：已存在但内容可能不同，始终覆盖以确保正确
        pass

    os.makedirs(res_xml_dir, exist_ok=True)
    open(config_file, "w", encoding="utf-8").write(NETWORK_SECURITY_CONFIG_XML)
    print(f"==> 已写入 network_security_config.xml → {config_file}")


def inject(path):
    """
    主注入函数：修改 build.gradle.kts。

    参数：
        path: build.gradle.kts 的路径
              （如 src-tauri/gen/android/app/build.gradle.kts）

    同时会自动推断并处理同目录下的 AndroidManifest.xml。
    """
    s = open(path, encoding="utf-8").read()

    # ---- 1) 删除任何已有的 signingConfigs 块（无论 init 生成哪种模板）----
    idx = s.find("signingConfigs {")
    if idx != -1:
        nl = s.rfind("\n", 0, idx)
        start = nl + 1
        prev_nl = s.rfind("\n", 0, nl)
        if prev_nl != -1 and s[prev_nl + 1:start].lstrip().startswith("// A2"):
            start = prev_nl + 1
        depth = 0
        i = s.index("{", idx)
        j = i
        while j < len(s):
            if s[j] == "{":
                depth += 1
            elif s[j] == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        k = j + 1
        while k < len(s) and s[k] in "\r\n":
            k += 1
        s = s[:start] + s[k:]

    # ---- 2) 在 buildTypes { 之前插入签名块 ----
    anchor = "    buildTypes {"
    if anchor in s:
        s = s.replace(anchor, SIGNING_BLOCK + "\n\n" + anchor, 1)

    # ---- 3) 确保 release 块引用签名配置 ----
    ref = 'signingConfig = signingConfigs.getByName("release")'
    if 'getByName("release")' in s and ref not in s:
        a = s.index('        getByName("release") {')
        k = a + len('        getByName("release") {')
        s = s[:k] + "\n            " + ref + s[k:]

    # ---- 4) 放行明文 HTTP：release 块 与 defaultConfig 块都要有 ----
    s = _ensure_cleartext_in_block(s, 'getByName("release") {')
    s = _ensure_cleartext_in_block(s, "defaultConfig {")

    # ---- 5) 注入 networkSecurityConfig（Gradle manifestPlaceholders 方式）----
    # 核心：不直接改 AndroidManifest.xml（会被 tauri android build 内部 re-init 抹掉），
    # 而是通过 build.gradle.kts 的 manifestPlaceholders 在 Gradle 构建时动态填充。
    # XML 文件的生成由 _inject_nsc_gradle_task() 负责（afterEvaluate 写文件），
    # AndroidManifest.xml 的属性注入由 _inject_nsc_into_manifest() 负责。
    s = _ensure_nsc_placeholder_in_block(s, "defaultConfig {")

    open(path, "w", encoding="utf-8").write(s)
    print(f"==> 已注入 release 签名、明文 HTTP 放行、networkSecurityConfig (Gradle 模式)：{path}")


MIXED_CONTENT_LINE = "settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW"


GRADLE_MIXED_CONTENT_TASK = '''
// A2: inject mixedContentMode (WebView 独立混合内容限制 -> ALWAYS_ALLOW)
// 注意：不移除 "Release" 匹配，因为 debug APK 也需要此注入。
//       debug 构建时 Kotlin 编译任务名包含 "Debug"，必须也能匹配。
afterEvaluate {
    tasks.matching { it.name.contains("compileKotlin", ignoreCase = true) }.configureEach {
        doFirst {
            val namespace = android.namespace ?: "com.wangxinchen.dawang"
            val pkgPath = namespace.replace(".", "/")
            val candidates = listOf(
                file("src/main/java/$pkgPath/generated/RustWebView.kt"),
                file("src/main/kotlin/$pkgPath/generated/RustWebView.kt")
            )
            for (f in candidates) {
                if (f.exists()) {
                    val txt = f.readText()
                    val line = "settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW"
                    if (!txt.contains(line)) {
                        val anchor = "settings.javaScriptCanOpenWindowsAutomatically = true"
                        if (!txt.contains(anchor)) {
                            throw GradleException("A2 注入失败: RustWebView.kt 中未找到锚点字符串 '$anchor'，Tauri 可能已升级。请更新 inject_android_release_config.py 中的锚点。")
                        }
                        f.writeText(
                            txt.replaceFirst(
                                anchor,
                                anchor + "\\n        $line"
                            )
                        )
                        // A2: verify mixedContentMode 写入真正生效（防止 replaceFirst 锚点匹配后静默失败）
                        if (!f.readText().contains(line)) {
                            throw GradleException("A2 注入失败: 虽然找到锚点，但写入后仍未找到 mixedContentMode。请检查 RustWebView.kt 格式是否变动。")
                        }
                        println("==> [Gradle] 已注入 WebView mixedContentMode -> " + f.absolutePath)
                    } else {
                        println("==> [Gradle] mixedContentMode 已存在，跳过")
                    }
                    break
                }
            }
        }
    }
}
'''


def _inject_webview_mixed_content(app_dir):
    """
    通过在 build.gradle.kts 中注入 Gradle 任务，在 Kotlin 编译前修改 RustWebView.kt。

    RustWebView.kt 不是 tauri android init 产生的，而是 Gradle 配置阶段动态生成的。
    因此 Python 脚本跑在 init 之后、Gradle 之前，永远找不到该文件。
    改用 Gradle 任务的 doFirst 钩子，在 Kotlin 编译前那一刻注入。
    """
    gradle_path = os.path.join(app_dir, "build.gradle.kts")
    content = open(gradle_path, encoding="utf-8").read()

    # 幂等检查
    MARKER = "// A2: inject mixedContentMode"
    if MARKER in content:
        print(f"==> Gradle mixedContentMode 任务已存在，跳过")
        return

    # 追加到文件末尾
    new_content = content.rstrip() + "\n" + GRADLE_MIXED_CONTENT_TASK + "\n"
    open(gradle_path, "w", encoding="utf-8").write(new_content)
    print(f"==> 已注入 Gradle mixedContentMode 任务 → {gradle_path}")


def _print_verification(app_dir):
    """
    在 inject_all 完成后输出「已注入证据」，便于在 GitHub Actions 日志中
    直观确认 APK 实际包含的注入项。
    """
    print("=" * 60)
    print("==> [构建证据] 注入后实际内容（APK 里将包含这些）")
    print("==")

    # 1) build.gradle.kts 注入（签名 + usesCleartextTraffic）
    gradle_path2 = os.path.join(app_dir, "build.gradle.kts")
    if os.path.isfile(gradle_path2):
        content = open(gradle_path2, encoding="utf-8").read()
        if "create(\"release\")" in content and "usesCleartextTraffic" in content:
            print(f"  [gradle] OK 签名块 + usesCleartextTraffic 已注入")

        # 1.5) build.gradle.kts → manifestPlaceholders["networkSecurityConfig"]（Gradle 方式）
        if 'manifestPlaceholders["networkSecurityConfig"]' in content:
            print(f"  [gradle] OK manifestPlaceholders[networkSecurityConfig] 已注入（Gradle 模式）")
        else:
            print(f"  [gradle] FAIL manifestPlaceholders[networkSecurityConfig] 未注入!")

        # 1.6) build.gradle.kts → Gradle afterEvaluate NSC 生成任务
        if "// A2: generate network_security_config.xml" in content:
            print(f"  [gradle] OK Gradle NSC 生成任务已注入（afterEvaluate 写文件）")
        else:
            print(f"  [gradle] FAIL Gradle NSC 生成任务未注入!")

    # 2) network_security_config.xml 是否存在（磁盘文件，作为备份）
    nsc_path = os.path.join(app_dir, "src", "main", "res", "xml", "network_security_config.xml")
    if os.path.isfile(nsc_path):
        print(f"  [nsc] OK res/xml/network_security_config.xml 已生成（磁盘文件）")
    else:
        print(f"  [nsc] WARN 磁盘文件未生成（将依赖 Gradle afterEvaluate 在构建期生成）")

    # 3) AndroidManifest.xml 的 <application> 关键属性
    manifest_path = os.path.join(app_dir, "src", "main", "AndroidManifest.xml")
    if os.path.isfile(manifest_path):
        content = open(manifest_path, encoding="utf-8").read()
        for line in content.split("\n"):
            if "usesCleartextTraffic" in line or "networkSecurityConfig" in line:
                print(f"  [manifest] {line.strip()}")

    # 4) Gradle mixedContentMode 任务是否已注入
    gradle_path2 = os.path.join(app_dir, "build.gradle.kts")
    if os.path.isfile(gradle_path2):
        gc = open(gradle_path2, encoding="utf-8").read()
        if "A2: inject mixedContentMode" in gc:
            print(f"  [webview] OK Gradle mixedContentMode 任务已注入 (将在 Kotlin 编译前执行)")
        else:
            print(f"  [webview] FAIL Gradle 任务未注入!")
    else:
        print(f"  [webview] FAIL build.gradle.kts 不存在")

    print("=" * 60)


def inject_all(build_gradle_path):
    """
    执行完整注入流程（以上所有步骤）。

    参数：
        build_gradle_path: build.gradle.kts 的路径

    自动推断的路径：
        - app_dir: build.gradle.kts 所在目录（即 android/app/）
        - AndroidManifest.xml: app_dir/src/main/AndroidManifest.xml
        - res/xml/network_security_config.xml: app_dir/src/main/res/xml/
        - RustWebView.kt: app_dir/src/main/java/**/generated/RustWebView.kt
    """
    build_gradle_path = os.path.abspath(build_gradle_path)
    app_dir = os.path.dirname(build_gradle_path)
    manifest_path = os.path.join(app_dir, "src", "main", "AndroidManifest.xml")

    # 1) 修改 build.gradle.kts（签名 + 明文 HTTP）
    inject(build_gradle_path)

    # 2) 生成 network_security_config.xml
    _write_network_security_config(app_dir)

    # 3) 注入到 AndroidManifest.xml
    _inject_nsc_into_manifest(manifest_path, app_dir)

    # 4) 注入 WebView mixedContentMode（解决混合内容拦截）
    _inject_webview_mixed_content(app_dir)

    # 5) 注入 Gradle afterEvaluate 生成 network_security_config.xml（防止 tauri re-init 抹掉）
    _inject_nsc_gradle_task(app_dir)

    # 6) 输出注入证据（在 GitHub Actions 日志中可直接看到 APK 实际内容）
    _print_verification(app_dir)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: python3 inject_android_release_config.py <build.gradle.kts 路径>", file=sys.stderr)
        sys.exit(1)
    inject_all(sys.argv[1])
