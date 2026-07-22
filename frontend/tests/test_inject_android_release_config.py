"""针对 CI 安卓打包注入逻辑的单元测试。

验证 build_android.sh / build_debug_server.bat 在 `tauri android init` 重新生成
android 工程之后，强制回填的内容确实生效：

  1) release 块放行明文 HTTP（否则 Android release 包禁止 http://）；
  2) release 签名块读取 app/keystore.properties（否则 release 包未签名装不上）；
  3) network_security_config.xml 生成 + AndroidManifest 注入
     （解决 Tauri v2 WebView 混合内容拦截导致的 Network Error）。

不依赖 Android/Gradle/NDK，纯字符串改写校验，
直接 `python3 test_inject_android_release_config.py` 即可运行。
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

# 模拟 Tauri v2 生成的 AndroidManifest.xml
ANDROID_MANIFEST_TEMPLATE = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.linman_account_book"
        android:usesCleartextTraffic="${usesCleartextTraffic}">
        <activity
            android:configChanges="orientation|keyboardHidden|keyboard|screenSize|locale"
            android:launchMode="singleTask"
            android:label="@string/main_activity_title"
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
'''


def _write_gradle(tmp_path, content):
    p = tmp_path / "build.gradle.kts"
    with open(p, "w", encoding="utf-8") as f:
        f.write(content)
    return p


def _setup_app_dir(tmp_root, gradle_content=TAURI_TEMPLATE, manifest_content=ANDROID_MANIFEST_TEMPLATE):
    """创建模拟的 android/app 目录结构。"""
    app_dir = os.path.join(tmp_root, "app")
    os.makedirs(os.path.join(app_dir, "src", "main", "res", "xml"), exist_ok=True)

    # 写 build.gradle.kts
    gradle_path = os.path.join(app_dir, "build.gradle.kts")
    with open(gradle_path, "w", encoding="utf-8") as f:
        f.write(gradle_content)

    # 写 AndroidManifest.xml
    manifest_path = os.path.join(app_dir, "src", "main", "AndroidManifest.xml")
    with open(manifest_path, "w", encoding="utf-8") as f:
        f.write(manifest_content)

    return gradle_path, manifest_path, app_dir


# ====== 原有 build.gradle.kts 测试 ======

def test_release_block_gets_cleartext(tmp_path):
    path = _write_gradle(tmp_path, TAURI_TEMPLATE)
    inj.inject(str(path))
    out = open(path, encoding="utf-8").read()
    i, j = inj._find_block(out, 'getByName("release") {')
    assert inj.CLEARTEXT_LINE in out[i:j + 1], "release 块内必须出现明文 HTTP 放行"


def test_default_config_gets_cleartext_when_missing(tmp_path):
    path = _write_gradle(tmp_path, TAURI_TEMPLATE_NO_DEFAULT_CLEARTEXT)
    inj.inject(str(path))
    out = open(path, encoding="utf-8").read()
    i, j = inj._find_block(out, "defaultConfig {")
    assert inj.CLEARTEXT_LINE in out[i:j + 1], "defaultConfig 缺失明文放行时必须兜底补上"


def test_idempotent_on_already_injected(tmp_path):
    path = _write_gradle(tmp_path, TAURI_TEMPLATE)
    inj.inject(str(path))
    first = open(path, encoding="utf-8").read()
    inj.inject(str(path))
    second = open(path, encoding="utf-8").read()
    # 重复注入不得产生重复行
    ri, rj = inj._find_block(second, 'getByName("release") {')
    rel_block = second[ri:rj + 1]
    assert rel_block.count(inj.CLEARTEXT_LINE) == 1, "release 块内明文放行行不能重复"
    assert second.count('create("release")') == 1, "release 签名配置不能重复"
    assert first == second, "重复注入必须幂等"


def test_signing_block_present_and_before_buildtypes(tmp_path):
    path = _write_gradle(tmp_path, TAURI_TEMPLATE)
    inj.inject(str(path))
    out = open(path, encoding="utf-8").read()
    assert "signingConfigs {" in out
    assert "app/keystore.properties" in out
    assert out.index("signingConfigs {") < out.index("buildTypes {")


def test_old_signing_block_is_replaced(tmp_path):
    path = _write_gradle(tmp_path, TAURI_TEMPLATE_WITH_SIGNING)
    inj.inject(str(path))
    out = open(path, encoding="utf-8").read()
    assert 'keyAlias = "old-wrong-alias"' not in out, "旧的 signingConfigs 必须被清掉"
    assert out.count('create("release")') == 1, "只能有一个 release 签名配置"
    assert "app/keystore.properties" in out


def test_release_block_still_references_signing(tmp_path):
    path = _write_gradle(tmp_path, TAURI_TEMPLATE)
    inj.inject(str(path))
    out = open(path, encoding="utf-8").read()
    assert 'signingConfig = signingConfigs.getByName("release")' in out


# ====== 新增 network_security_config 测试 ======

def test_nsc_xml_is_created(tmp_path):
    """验证 network_security_config.xml 被正确生成。"""
    gradle_path, manifest_path, app_dir = _setup_app_dir(str(tmp_path._root))
    inj.inject_all(gradle_path)

    nsc_path = os.path.join(app_dir, "src", "main", "res", "xml", "network_security_config.xml")
    assert os.path.isfile(nsc_path), "network_security_config.xml 必须被创建"

    content = open(nsc_path, encoding="utf-8").read()
    assert "cleartextTrafficPermitted" in content, "必须包含 cleartextTrafficPermitted"
    assert "network-security-config" in content, "必须是有效的 network-security-config XML"


def test_nsc_injected_into_manifest(tmp_path):
    """验证 AndroidManifest.xml 中被注入了 networkSecurityConfig 属性。"""
    gradle_path, manifest_path, app_dir = _setup_app_dir(str(tmp_path._root))
    inj.inject_all(gradle_path)

    manifest = open(manifest_path, encoding="utf-8").read()
    assert inj.NSC_ATTR in manifest, (
        "AndroidManifest.xml 必须包含 android:networkSecurityConfig 属性"
    )


def test_nsc_injection_idempotent(tmp_path):
    """验证重复注入 networkSecurityConfig 不会产生重复属性。"""
    gradle_path, manifest_path, app_dir = _setup_app_dir(str(tmp_path._root))
    inj.inject_all(gradle_path)
    first = open(manifest_path, encoding="utf-8").read()
    inj.inject_all(gradle_path)
    second = open(manifest_path, encoding="utf-8").read()

    # NSC_ATTR 必须出现在 manifest 中，且只出现一次
    assert inj.NSC_ATTR in second, "注入后必须包含 networkSecurityConfig"
    assert second.count(inj.NSC_ATTR) == 1, "重复注入不能产生重复的 networkSecurityConfig 属性"
    assert first == second, "重复 inject_all 必须幂等"


def test_inject_all_also_does_gradle_injection(tmp_path):
    """验证 inject_all 同时执行了 build.gradle.kts 注入。"""
    gradle_path, manifest_path, app_dir = _setup_app_dir(str(tmp_path._root))
    inj.inject_all(gradle_path)

    out = open(gradle_path, encoding="utf-8").read()
    assert "signingConfigs {" in out, "inject_all 必须注入签名块"
    i, j = inj._find_block(out, 'getByName("release") {')
    assert inj.CLEARTEXT_LINE in out[i:j + 1], "inject_all 必须放行 release HTTP"


# ------- 不依赖 pytest 的直跑入口 -------
if __name__ == "__main__":
    import traceback

    # 简易 tmp_path shim
    class Tmp:
        def __init__(self, root):
            self._root = root

        def __truediv__(self, name):
            return os.path.join(self._root, name)

        def __str__(self):
            return self._root

    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    passed = 0
    failed = 0
    for t in tests:
        tmp = tempfile.mkdtemp(prefix="inj_test_")
        tmp_obj = Tmp(tmp)
        try:
            t(tmp_obj)
            print(f"PASS  {t.__name__}")
            passed += 1
        except Exception:
            print(f"FAIL  {t.__name__}")
            traceback.print_exc()
            failed += 1
    print(f"\n==== {passed} passed, {failed} failed ====")
    sys.exit(1 if failed else 0)
