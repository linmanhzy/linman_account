import client from './client'

export interface LoginResult {
  token: string
  username: string
  role: string
  userId: number
}

export async function login(username: string, password: string): Promise<LoginResult> {
  const data = await client.post<LoginResult>('/api/auth/login', { username, password })
  return data
}

export async function register(username: string, password: string): Promise<LoginResult> {
  const data = await client.post<LoginResult>('/api/auth/register', { username, password })
  return data
}
