#!/bin/bash
# ============================================================
# 安卓打包脚本（Linux / CI 专用）
# 作用：在 GitHub Actions (Ubuntu) 上构建「联网版」APK 并自动签名。
#   - VITE_API_BASE 取自 .env.server（公网后端地址），写死进 APK
#   - 签名信息来自 GitHub Secrets（ANDROID_KEY_*），从 base64 还原 keystore
# 用法（本地也可）：bash build_android.sh
# ============================================================
set -euo pipefail

# 切到脚本所在目录（即 frontend/）
cd "$(dirname "$0")"

# ---------- 1) 注入后端地址（SERVER 模式） ----------
if [ -z "${VITE_API_BASE:-}" ] && [ -f .env.server ]; then
  VITE_API_BASE=$(grep '^VITE_API_BASE=' .env.server | cut -d= -f2-)
fi
if [ -z "${VITE_API_BASE:-}" ]; then
  echo "错误：找不到 VITE_API_BASE（请在 .env.server 配置或传入该环境变量）" >&2
  exit 1
fi
export VITE_API_BASE
echo "==> [1/4] 后端地址 = $VITE_API_BASE （SERVER 模式）"

# ---------- 2) 初始化安卓工程（生成 src-tauri/gen/android） ----------
echo "==> [2/4] 初始化安卓工程 (tauri android init)"
npx tauri android init

# ---------- 2.5) 根据发布版本号生成递增 versionCode（保证覆盖安装可升级） ----------
if [ -n "${APP_VERSION:-}" ]; then
  VER=${APP_VERSION#v}                         # 去掉前缀 v → 1.2.0
  IFS='.' read -r MAJ MIN PAT <<< "$VER"
  MAJ=${MAJ:-0}; MIN=${MIN:-0}; PAT=${PAT:-0}
  CODE=$(( MAJ*10000 + MIN*100 + PAT ))        # 1.2.0 → 10200，单调递增
  TP="$PWD/src-tauri/gen/android/app/tauri.properties"
  if [ -f "$TP" ]; then
    sed -i "/^tauri.android.versionCode=/d; /^tauri.android.versionName=/d" "$TP"
  fi
  printf 'tauri.android.versionCode=%s\n' "$CODE" >> "$TP"
  printf 'tauri.android.versionName=%s\n' "$VER" >> "$TP"
  echo "==> versionCode=$CODE  versionName=$VER"
fi

# ---------- 3) 注入签名配置 ----------
: "${ANDROID_KEY_BASE64:?缺少 Secret ANDROID_KEY_BASE64}"
: "${ANDROID_KEY_ALIAS:?缺少 Secret ANDROID_KEY_ALIAS}"
: "${ANDROID_KEY_PASSWORD:?缺少 Secret ANDROID_KEY_PASSWORD}"

APP_DIR="$PWD/src-tauri/gen/android/app"
mkdir -p "$APP_DIR"

# 从 base64 还原 keystore 文件
echo "$ANDROID_KEY_BASE64" | base64 -d > "$APP_DIR/release-key.jks"

# 生成 keystore.properties（与 gen/android/app/build.gradle.kts 中签名块读取的字段一致）
cat > "$APP_DIR/keystore.properties" <<EOF
keyAlias=$ANDROID_KEY_ALIAS
keyPassword=$ANDROID_KEY_PASSWORD
storePassword=$ANDROID_KEY_PASSWORD
storeFile=release-key.jks
EOF
echo "==> [3/4] 已写入签名配置 (alias=$ANDROID_KEY_ALIAS)"

# ---------- 3.5) 把 release 签名配置 + 明文 HTTP 放行写回 build.gradle.kts ----------
# 关键：tauri android init 会重新生成 gen/android，抹掉任何手工改动。
# 若不写回签名块，release 包未签名 → Android 直接拒绝安装；
# 若不写回 release 的 usesCleartextTraffic="true"，release 包会禁止 http://，
# App 连不上只提供 http 的服务器后端（该问题此前静默存在，靠 Tauri 模板 defaultConfig 继承兜底）。
# 注入逻辑抽到 scripts/inject_android_release_config.py，可单测（见 tests/）。
APP_GRADLE="$APP_DIR/build.gradle.kts"
if [ ! -f "$APP_GRADLE" ]; then
  echo "错误：未找到 $APP_GRADLE，tauri android init 可能失败" >&2
  exit 1
fi
python3 scripts/inject_android_release_config.py "$APP_GRADLE"

# ---------- 4) 构建 APK ----------
echo "==> [4/4] 构建 APK (--split-per-abi)"
npx tauri android build --split-per-abi
echo "BUILD DONE"
