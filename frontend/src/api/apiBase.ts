/**
 * API_BASE 解析与错误展示（纯函数，不依赖 axios/Vite 运行时）
 *
 * 从 client.ts 中提取，以便单元测试可以直接导入而不触发 axios 副作用。
 */

/**
 * 解析 API 基地址。
 *
 * 优先级：VITE_API_BASE 显式配置 > DEV 模式默认 localhost
 *
 * @param viteApiBase  import.meta.env.VITE_API_BASE 的值
 * @param isDev        import.meta.env.DEV
 * @returns 合法的 HTTP URL 字符串
 * @throws  在 PROD 模式下未配置 VITE_API_BASE 时抛详细错误（修复 "Failed to construct 'URL'" 根因）
 */
export function resolveApiBase(viteApiBase: string | undefined, isDev: boolean): string {
  const configured = (viteApiBase || '').trim()
  if (configured) {
    return configured
  }

  // DEV 模式（npm run dev）：默认连本机后端
  if (isDev) {
    return 'http://127.0.0.1:8080'
  }

  // PROD 模式（vite build / tauri android build）：未配置 VITE_API_BASE 是严重错误
  // 之前回落到空字符串 → 后续 axios 构造 URL 时抛 "Failed to construct 'URL': invalid URL"
  // 用户看到的是毫无线索的英文报错，无法排查。
  // 现在改为在初始阶段就抛出中文详细错误。
  throw new Error(
    '\n' +
    '╔══════════════════════════════════════════════════════╗\n' +
    '║  [林蛮记账] 后端地址未配置                           ║\n' +
    '╠══════════════════════════════════════════════════════╣\n' +
    '║  构建时未注入 VITE_API_BASE 环境变量。               ║\n' +
    '║                                                      ║\n' +
    '║  本地构建：                                          ║\n' +
    '║    修改 frontend/.env 中的 VITE_API_BASE             ║\n' +
    '║    或 set VITE_API_BASE=http://你的IP:8080           ║\n' +
    '║    然后重跑构建脚本                                   ║\n' +
    '║                                                      ║\n' +
    '║  CD 构建（服务器版）：                                ║\n' +
    '║    在 GitHub Actions 运行 CD 时填入 server_api_base   ║\n' +
    '║    默认值：http://47.104.152.25:8080                  ║\n' +
    '║                                                      ║\n' +
    '║  Debug 快速构建（连服务器测试）：                     ║\n' +
    '║    双击 build_debug_server.bat 即可                   ║\n' +
    '╚══════════════════════════════════════════════════════╝\n',
  )
}

/**
 * 在 DOM 中显示红色错误覆盖层。
 *
 * 对于手机 App 用户，console.error 不可见（没有 DevTools）。
 * 此函数将错误注入 <body> 顶部，确保用户能看到报错内容。
 */
export function showFatalErrorOverlay(message: string): void {
  // 避免重复注入
  if (typeof document === 'undefined') return
  if (document.getElementById('__linman_fatal_error__')) {
    return
  }

  const overlay = document.createElement('div')
  overlay.id = '__linman_fatal_error__'
  overlay.style.cssText = [
    'position: fixed',
    'top: 0',
    'left: 0',
    'right: 0',
    'z-index: 99999',
    'background: #a8071a',
    'color: #fff',
    'font-family: "PingFang SC", "Microsoft YaHei", sans-serif',
    'font-size: 13px',
    'line-height: 1.6',
    'padding: 16px 20px',
    'white-space: pre-wrap',
    'word-break: break-all',
    'border-bottom: 3px solid #5c0011',
    'box-shadow: 0 2px 12px rgba(0,0,0,0.5)',
  ].join(';')

  overlay.textContent = message

  // 确保 overlay 插入在最前面
  if (document.body) {
    document.body.insertBefore(overlay, document.body.firstChild)
  } else {
    // body 尚未就绪时等 DOM 加载后注入
    document.addEventListener('DOMContentLoaded', () => {
      if (document.body && !document.getElementById('__linman_fatal_error__')) {
        document.body.insertBefore(overlay, document.body.firstChild)
      }
    })
  }
}
