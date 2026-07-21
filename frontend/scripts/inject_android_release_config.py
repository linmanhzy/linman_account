#!/usr/bin/env python3
"""CI 安卓打包注入：对 `tauri android init` 重新生成的 build.gradle.kts 做必要回填。

由于 `tauri android init` 会抹掉一切手工改动，本脚本在 init 之后运行，强制保证：

1) release 构建块放行明文 HTTP：
       manifestPlaceholders["usesCleartextTraffic"] = "true"
   否则 Android 在 release 包里禁止 http://，App 连不上 http 后端（服务器后端只提供 http）。

2) defaultConfig 也放行明文 HTTP（防御不同 Tauri 模板差异；有些模板 defaultConfig 不带）。

3) release 签名块读取 app/keystore.properties（由 CI 通过 Secret 注入），
   并保证 release 块引用该签名配置，否则 release 包未签名、Android 直接拒绝安装。

本脚本只做纯字符串改写，不依赖 Android / Gradle / NDK 工具链，可被单元测试直接调用。
用法：python3 inject_android_release_config.py <build.gradle.kts 路径>
"""
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


def inject(path):
    s = open(path, encoding="utf-8").read()

    # ---- 1) 删除任何已有的 signingConfigs 块（无论 init 生成哪种模板）----
    # 同时吃掉块后的连续空行，保证重复注入字节级幂等（避免残留空行导致结果不一致）。
    # 注意：本脚本注入的块以一行业务注释（// A2: ...）开头，删除时要连注释行一起清掉，
    # 否则重复注入会留下重复注释。
    idx = s.find("signingConfigs {")
    if idx != -1:
        # 定位包含 signingConfigs { 的整行行首
        nl = s.rfind("\n", 0, idx)
        start = nl + 1
        # 若紧邻上一行是本项目注入的 // A2 注释，则连注释行一起删除
        # 注意：用 rfind("\n", 0, nl) 取到注释行“之前”的换行，而非 start 前那个（那是注释行末尾）。
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


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: python3 inject_android_release_config.py <build.gradle.kts 路径>", file=sys.stderr)
        sys.exit(1)
    inject(sys.argv[1])
