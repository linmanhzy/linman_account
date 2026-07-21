"""针对 CI 安卓打包注入逻辑的单元测试。

验证 build_android.sh 在 `tauri android init` 重新生成 build.gradle.kts 之后，
强制回填的两件事确实生效：
  1) release 块放行明文 HTTP（否则 Android release 包禁止 http://，App 连不上 http 后端）；
  2) release 签名块读取 app/keystore.properties（否则 release 包未签名装不上）。

不依赖 Android/Gradle/NDK，纯字符串改写校验，直接 `python3 test_inject_android_release_config.py` 即可运行。
"""
import os
import sys
import tempfile

SCRIPT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "scripts"))
sys.path.insert(0, SCRIPT_DIR)

import inject_android_release_config as inj  # noqa: E402


# ---- 模拟 `tauri android init` 生成的模板（release 块无明文放行、无自定义签名块）----
TAURI_TEMPLATE = """import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    compileSdk = 36
    namespace = "com.wangxinchen.dawang"
    defaultConfig {
        manifestPlaceholders["usesCleartextTraffic"] = "true"
        applicationId = "com.wangxinchen.dawang"
        minSdk = 24
        targetSdk = 36
    }
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
"""

# 部分 Tauri 版本模板：defaultConfig 也没有明文放行，必须兜底补上
TAURI_TEMPLATE_NO_DEFAULT_CLEARTEXT = """android {
    compileSdk = 36
    defaultConfig {
        applicationId = "com.x.y"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
"""

# 自带 signingConfigs 块的模板：旧块应被清掉，避免遗留错误 keyAlias
TAURI_TEMPLATE_WITH_SIGNING = """android {
    defaultConfig {
        manifestPlaceholders["usesCleartextTraffic"] = "true"
    }
    signingConfigs {
        create("release") {
            keyAlias = "old-wrong-alias"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
"""


def _write(tmp_path, content):
    p = tmp_path / "build.gradle.kts"
    with open(p, "w", encoding="utf-8") as f:
        f.write(content)
    return p


def test_release_block_gets_cleartext(tmp_path):
    path = _write(tmp_path, TAURI_TEMPLATE)
    inj.inject(path)
    out = open(path, encoding="utf-8").read()
    i, j = inj._find_block(out, 'getByName("release") {')
    assert inj.CLEARTEXT_LINE in out[i:j + 1], "release 块内必须出现明文 HTTP 放行"


def test_default_config_gets_cleartext_when_missing(tmp_path):
    path = _write(tmp_path, TAURI_TEMPLATE_NO_DEFAULT_CLEARTEXT)
    inj.inject(path)
    out = open(path, encoding="utf-8").read()
    i, j = inj._find_block(out, "defaultConfig {")
    assert inj.CLEARTEXT_LINE in out[i:j + 1], "defaultConfig 缺失明文放行时必须兜底补上"


def test_idempotent_on_already_injected(tmp_path):
    path = _write(tmp_path, TAURI_TEMPLATE)
    inj.inject(path)
    first = open(path, encoding="utf-8").read()
    inj.inject(path)
    second = open(path, encoding="utf-8").read()
    # 重复注入不得产生重复行：明文放行在 release 块内只能出现一次
    ri, rj = inj._find_block(second, 'getByName("release") {')
    rel_block = second[ri:rj + 1]
    assert rel_block.count(inj.CLEARTEXT_LINE) == 1, "release 块内明文放行行不能重复"
    # release 签名配置只能有一个
    assert second.count('create("release")') == 1, "release 签名配置不能重复"
    # 两次结果应一致（字节级幂等）
    assert first == second, "重复注入必须幂等"


def test_signing_block_present_and_before_buildtypes(tmp_path):
    path = _write(tmp_path, TAURI_TEMPLATE)
    inj.inject(path)
    out = open(path, encoding="utf-8").read()
    assert "signingConfigs {" in out
    assert "app/keystore.properties" in out
    assert out.index("signingConfigs {") < out.index("buildTypes {")


def test_old_signing_block_is_replaced(tmp_path):
    path = _write(tmp_path, TAURI_TEMPLATE_WITH_SIGNING)
    inj.inject(path)
    out = open(path, encoding="utf-8").read()
    assert 'keyAlias = "old-wrong-alias"' not in out, "旧的 signingConfigs 必须被清掉"
    assert out.count('create("release")') == 1, "只能有一个 release 签名配置"
    assert "app/keystore.properties" in out


def test_release_block_still_references_signing(tmp_path):
    path = _write(tmp_path, TAURI_TEMPLATE)
    inj.inject(path)
    out = open(path, encoding="utf-8").read()
    assert 'signingConfig = signingConfigs.getByName("release")' in out


# ------- 不依赖 pytest 的直跑入口 -------
if __name__ == "__main__":
    import traceback

    # 简易 tmp_path shim
    class Tmp:
        def __init__(self, root):
            self._root = root

        def __truediv__(self, name):
            return os.path.join(self._root, name)

    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    passed = 0
    failed = 0
    for t in tests:
        tmp = tempfile.mkdtemp(prefix="inj_test_")
        try:
            t(Tmp(tmp))
            print(f"PASS  {t.__name__}")
            passed += 1
        except Exception:
            print(f"FAIL  {t.__name__}")
            traceback.print_exc()
            failed += 1
    print(f"\n==== {passed} passed, {failed} failed ====")
    sys.exit(1 if failed else 0)
