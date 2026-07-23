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
NSC_ATTR = 'android:networkSecurityConfig="@xml/network_security_config"'
# 标记注释（用于幂等性检测）
NSC_COMMENT = '<!-- A2: networkSecurityConfig -->'


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

    open(path, "w", encoding="utf-8").write(s)
    print(f"==> 已注入 release 签名与明文 HTTP 放行：{path}")


MIXED_CONTENT_LINE = "settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW"


def _inject_webview_mixed_content(app_dir):
    """
    在 RustWebView.kt 的 init 块中注入混合内容放行设置。

    Tauri v2 生成的 RustWebView.kt 设置了各种 WebView 参数
    （javaScriptEnabled、domStorageEnabled 等），但唯独没有设置
    mixedContentMode。WebView 默认值是 MIXED_CONTENT_NEVER_ALLOW，
    导致从 https://tauri.localhost 向 http:// 后端发请求时被 WebView
    渲染引擎静默拦截 → axios 收到 Network Error，请求从未离开手机。

    本函数在 init 块末尾插入：
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

    幂等：已存在则跳过。
    """
    # 在 java 目录树上搜索 RustWebView.kt
    java_dir = os.path.join(app_dir, "src", "main", "java")
    webview_path = None
    for root, dirs, files in os.walk(java_dir):
        if "RustWebView.kt" in files:
            webview_path = os.path.join(root, "RustWebView.kt")
            break

    if not webview_path:
        print("==> [警告] 未找到 RustWebView.kt，跳过 WebView mixedContentMode 注入")
        return

    content = open(webview_path, encoding="utf-8").read()

    # 幂等检查
    if MIXED_CONTENT_LINE in content:
        print(f"==> WebView mixedContentMode 已设置，跳过：{webview_path}")
        return

    # 在 init 块的最后一个 settings 行之后插入
    # 目标位置：settings.javaScriptCanOpenWindowsAutomatically = true 之后
    marker = "settings.javaScriptCanOpenWindowsAutomatically = true"
    pos = content.find(marker)
    if pos == -1:
        # 回退到 mediaPlaybackRequiresUserGesture（以防未来 Tauri 模板变化）
        marker = "settings.mediaPlaybackRequiresUserGesture = false"
        pos = content.find(marker)
    if pos == -1:
        print(f"==> [警告] RustWebView.kt 结构未知，无法注入 mixedContentMode")
        return

    # 找到 marker 所在行的末尾
    end_of_line = content.find("\n", pos)
    if end_of_line == -1:
        print(f"==> [警告] RustWebView.kt 格式异常")
        return

    indent = "        "  # 8 空格缩进，与现有 settings 行对齐
    new_line = f"\n{indent}{MIXED_CONTENT_LINE}"
    content = content[:end_of_line] + new_line + content[end_of_line:]

    open(webview_path, "w", encoding="utf-8").write(content)
    print(f"==> 已注入 WebView mixedContentMode → {webview_path}")


def _print_verification(app_dir):
    """
    在 inject_all 完成后输出「已注入证据」，便于在 GitHub Actions 日志中
    直观确认 APK 实际包含的注入项。
    """
    print("=" * 60)
    print("==> [构建证据] 注入后实际内容（APK 里将包含这些）")
    print("=" * 60)

    # 1) AndroidManifest.xml 的 <application> 关键属性
    manifest_path = os.path.join(app_dir, "src", "main", "AndroidManifest.xml")
    if os.path.isfile(manifest_path):
        content = open(manifest_path, encoding="utf-8").read()
        for line in content.split("\n"):
            if "usesCleartextTraffic" in line or "networkSecurityConfig" in line:
                print(f"  [manifest] {line.strip()}")

    # 2) network_security_config.xml 是否存在
    nsc_path = os.path.join(app_dir, "src", "main", "res", "xml", "network_security_config.xml")
    if os.path.isfile(nsc_path):
        print(f"  [nsc] OK res/xml/network_security_config.xml 已生成")
    else:
        print(f"  [nsc] FAIL 未生成!")

    # 3) RustWebView.kt 的关键 settings 行
    java_dir = os.path.join(app_dir, "src", "main", "java")
    for root, dirs, files in os.walk(java_dir):
        if "RustWebView.kt" in files:
            wp = os.path.join(root, "RustWebView.kt")
            wc = open(wp, encoding="utf-8").read()
            for line in wc.split("\n"):
                if "settings." in line and "= " in line:
                    print(f"  [webview] {line.strip()}")
            break

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

    # 5) 输出注入证据（在 GitHub Actions 日志中可直接看到 APK 实际内容）
    _print_verification(app_dir)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: python3 inject_android_release_config.py <build.gradle.kts 路径>", file=sys.stderr)
        sys.exit(1)
    inject_all(sys.argv[1])
