import axios, { type AxiosInstance } from 'axios'

const TOKEN_KEY = 'lm_token'
export const API_BASE = import.meta.env.VITE_API_BASE || 'http://127.0.0.1:8080'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
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
      window.location.href = '/login'
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
