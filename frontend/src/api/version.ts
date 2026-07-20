import client from './client'

/**
 * 获取系统版本号（从后端 /api/version 公开接口读取）。
 * 拦截器自动解包 { code, message, data } 后直接返回字符串，如 "1.0.0"。
 */
export async function getVersion(): Promise<string> {
  return client.get('/api/version')
}
