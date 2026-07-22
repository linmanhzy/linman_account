import axios, { type AxiosInstance } from 'axios'
import { resolveApiBase, showFatalErrorOverlay } from './apiBase'

const TOKEN_KEY = 'lm_token'

// ============================================================
// 计算运行时 API_BASE
// ============================================================

let API_BASE: string
try {
  API_BASE = resolveApiBase(
    import.meta.env.VITE_API_BASE as string | undefined,
    import.meta.env.DEV,
  )
} catch (err: unknown) {
  // 抛出时立即尝试注入 DOM 错误覆盖层
  const msg = err instanceof Error ? err.message : String(err)
  console.error('[林蛮记账] API_BASE 解析失败:', msg)

  // 尝试注入 DOM 可见覆盖层（浏览器/WebView 环境）
  try {
    showFatalErrorOverlay(msg)
  } catch {
    // DOM 操作失败（如 Node.js 环境），忽略
  }

  // 回退到空字符串，让后续的 API 调用也带上报错
  // （axios 会抛 "Invalid URL" 但至少 console 和 DOM 已经有线索了）
  API_BASE = ''
}

export { API_BASE, resolveApiBase, showFatalErrorOverlay }

// ============================================================
// Token 管理
// ============================================================

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

// ============================================================
// 401 跳转处理器（由 AuthProvider 注册）
// ============================================================

let unauthorizedHandler: (() => void) | null = null
export function setUnauthorizedHandler(fn: () => void) {
  unauthorizedHandler = fn
}

// ============================================================
// Axios 客户端
// ============================================================

const raw = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
})

// 请求拦截：自动带 JWT
raw.interceptors.request.use((config) => {
  const token = tokenStore.get()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：剥离 { code, message, data } 外壳
raw.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body?.data
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      tokenStore.clear()
      localStorage.removeItem('lm_profile')
      if (unauthorizedHandler) {
        unauthorizedHandler()
      } else {
        window.location.href = '/login'
      }
    }
    const msg = error.response?.data?.message || error.message || '网络错误，请确认后端已启动'
    return Promise.reject(new Error(msg))
  },
)

// 类型：让 get/post/put/delete 直接返回 T（已被拦截器解包）
type ApiClient = Omit<AxiosInstance, 'get' | 'post' | 'put' | 'delete'> & {
  get: <T = unknown>(url: string, config?: Parameters<AxiosInstance['get']>[1]) => Promise<T>
  post: <T = unknown>(url: string, data?: unknown, config?: Parameters<AxiosInstance['post']>[2]) => Promise<T>
  put: <T = unknown>(url: string, data?: unknown, config?: Parameters<AxiosInstance['put']>[2]) => Promise<T>
  delete: <T = unknown>(url: string, config?: Parameters<AxiosInstance['delete']>[1]) => Promise<T>
}

const client = raw as unknown as ApiClient

export default client
