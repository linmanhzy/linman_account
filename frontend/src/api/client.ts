import axios, { type AxiosInstance } from 'axios'

const TOKEN_KEY = 'lm_token'

// API 地址解析策略：
// - 显式配置 (.env 中设了 VITE_API_BASE)：用配置值
// - dev (npm run dev)：默认 http://127.0.0.1:8080，连本机后端
// - prod (CD 构建)：默认空字符串 = 相对路径，浏览器走当前 origin，
//   由部署环境的 nginx 反代 /api/ 到后端（避免 CDN/反代域名变更要重新构建）
//   .env/.env.production 在根 .gitignore 里被排除，所以生产构建拿不到本地开发用的
//   局域网 IP，必须用相对路径作为兜底。
const _devDefault = 'http://127.0.0.1:8080'
const _prodDefault = ''
export const API_BASE = import.meta.env.VITE_API_BASE
  || (import.meta.env.DEV ? _devDefault : _prodDefault)

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

// 401 跳转处理器：由 AuthProvider 注册（携带当前路由，登录后可跳回原页面）。
// 未注册时回退到整页跳转，保证向后兼容。
let unauthorizedHandler: (() => void) | null = null
export function setUnauthorizedHandler(fn: () => void) {
  unauthorizedHandler = fn
}

const raw = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
})

// 响应拦截：统一剥离 { code, message, data } 外壳，非 0 视为错误。
// 拦截器在运行时已把返回值替换为 data，这里把类型声明为“直接返回数据”，
// 省去每个调用处再拆包。
raw.interceptors.request.use((config) => {
  const token = tokenStore.get()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

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
      // 优先走注入式跳转（保留当前路由，避免整页刷新丢表单）；
      // 未注册 handler 时回退整页跳转。
      if (unauthorizedHandler) {
        unauthorizedHandler()
      } else {
        window.location.href = '/login'
      }
    }
    const msg = error.response?.data?.message || error.message || '网络错误，请确认后端已启动'
    return Promise.reject(new Error(msg))
  }
)

// 让 TS 认为 get/post/put/delete 直接返回 T（已被拦截器解包）
type ApiClient = Omit<AxiosInstance, 'get' | 'post' | 'put' | 'delete'> & {
  get: <T = unknown>(url: string, config?: Parameters<AxiosInstance['get']>[1]) => Promise<T>
  post: <T = unknown>(url: string, data?: unknown, config?: Parameters<AxiosInstance['post']>[2]) => Promise<T>
  put: <T = unknown>(url: string, data?: unknown, config?: Parameters<AxiosInstance['put']>[2]) => Promise<T>
  delete: <T = unknown>(url: string, config?: Parameters<AxiosInstance['delete']>[1]) => Promise<T>
}

const client = raw as unknown as ApiClient

export default client
