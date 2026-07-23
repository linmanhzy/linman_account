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
  // 情形1：Vite 编译期变量未定义（Docker 构建未传 ARG → VITE_API_BASE=undefined）
  //        nginx 反代架构下前端应走相对路径 /api/xxx，回落空字符串让 axios 用相对路径
  //        与情形4（用户显式设空）区分开：
  //          - undefined = 没传变量
  //          - 空字符串 = 用户传了但值为空 = 配置错误
  if (viteApiBase === undefined) {
    // DEV 模式（npm run dev）VITE_API_BASE 未设 → 连本机后端
    if (isDev) {
      return 'http://127.0.0.1:8080'
    }
    // PROD 模式（Docker 构建 / nginx 反代）VITE_API_BASE 未设 → 用相对路径
    return ''
  }

  const configured = (viteApiBase || '').trim()
  if (configured) {
    return configured
  }

  // DEV 模式 + 空字符串 → 连本机后端
  if (isDev) {
    return 'http://127.0.0.1:8080'
  }

  // 情形4：PROD 模式 + 用户显式设为空串 = 明确配置错误
  // 之前回落到空字符串 → 后续 axios 构造 URL 时抛 "Failed to construct 'URL': invalid URL"
  // 用户看到的是毫无线索的英文报错，无法排查。
  // 现在改为在初始阶段就抛出中文详细错误（无 ASCII art 边框，节省屏幕空间）。
  throw new Error(
    '[林蛮记账] 后端地址未配置\n' +
    '\n' +
    '构建时未注入 VITE_API_BASE 环境变量。\n' +
    '\n' +
    '本地构建：\n' +
    '  · 修改 frontend/.env 中的 VITE_API_BASE\n' +
    '  · 或 set VITE_API_BASE=http://你的IP:8080\n' +
    '  · 然后重跑构建脚本\n' +
    '\n' +
    'CD 构建（服务器版）：\n' +
    '  · 在 GitHub Actions 运行 CD 时填入 server_api_base\n' +
    '  · 默认值：http://47.104.152.25:8080\n' +
    '\n' +
    'Debug 快速构建（连服务器测试）：\n' +
    '  · 双击 build_debug_server.bat 即可',
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
    'max-height: 60vh',          // 不允许长报错占满整个屏幕
    'overflow-y: auto',          // 超长内容可滚动
    'z-index: 99999',
    'background: #a8071a',
    'color: #fff',
    'font-family: "PingFang SC", "Microsoft YaHei", sans-serif',
    'font-size: 13px',
    'line-height: 1.6',
    'padding: 12px 44px 12px 20px', // 右侧 44px 留给关闭按钮
    'white-space: pre-wrap',
    'word-break: break-all',
    'border-bottom: 3px solid #5c0011',
    'box-shadow: 0 2px 12px rgba(0,0,0,0.5)',
    'box-sizing: border-box',
  ].join(';')

  // 关闭按钮（绝对定位在右上角）
  const closeBtn = document.createElement('button')
  closeBtn.setAttribute('data-linman-close', '1')
  closeBtn.setAttribute('aria-label', '关闭错误提示')
  closeBtn.textContent = '\u00D7' // ×
  closeBtn.style.cssText = [
    'position: absolute',
    'top: 8px',
    'right: 12px',
    'width: 24px',
    'height: 24px',
    'padding: 0',
    'border: 0',
    'border-radius: 4px',
    'background: rgba(255,255,255,0.15)',
    'color: #fff',
    'font-size: 18px',
    'line-height: 22px',
    'text-align: center',
    'cursor: pointer',
    'font-family: inherit',
  ].join(';')
  closeBtn.addEventListener('click', () => {
    overlay.remove()
  })

  // 错误内容
  const content = document.createElement('div')
  content.textContent = message

  overlay.appendChild(closeBtn)
  overlay.appendChild(content)

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
