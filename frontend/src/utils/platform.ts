// 平台判断：区分「Tauri 移动端」与「浏览器/桌面」。
// Tauri v2 会在 window.__TAURI_INTERNALS__ 注入 platform 字段：
//   windows / linux / macos 为桌面；android / ios 为移动端。
// 浏览器（vite dev）下该对象不存在，因此天然回退到 Web 布局。

function tauriPlatform(): string | undefined {
  const w = window as unknown as { __TAURI_INTERNALS__?: { platform?: string } }
  return w.__TAURI_INTERNALS__?.platform
}

/** 当前是否运行在 Tauri 移动端（Android / iOS）。 */
export function isTauriMobile(): boolean {
  const p = tauriPlatform()
  return p === 'android' || p === 'ios'
}

/**
 * 是否启用移动端视图（底部 Tab Bar + 安全区适配）。
 * 满足以下任一即可：
 *  1. 真实 Tauri 移动端环境；
 *  2. 浏览器调试时 URL 带 ?mobile=1（便于在 Web 下预览移动布局，无需 Android 设备）。
 */
export function isMobileView(): boolean {
  if (isTauriMobile()) return true
  try {
    return new URLSearchParams(window.location.search).get('mobile') === '1'
  } catch {
    return false
  }
}
