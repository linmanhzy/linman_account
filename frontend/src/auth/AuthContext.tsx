import React, { createContext, useContext, useState, useCallback, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { tokenStore, setUnauthorizedHandler } from '../api/client'
import { login as apiLogin, register as apiRegister, LoginResult } from '../api/auth'

interface AuthValue {
  token: string | null
  username: string | null
  role: string | null
  login: (username: string, password: string) => Promise<LoginResult>
  register: (username: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthValue | null>(null)
const PROFILE_KEY = 'lm_profile'

function loadProfile(): { username: string | null; role: string | null } {
  try {
    const raw = localStorage.getItem(PROFILE_KEY)
    if (raw) return JSON.parse(raw)
  } catch { /* ignore */ }
  return { username: null, role: null }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const saved = loadProfile()
  const [token, setToken] = useState<string | null>(() => tokenStore.get())
  const [username, setUsername] = useState<string | null>(saved.username)
  const [role, setRole] = useState<string | null>(saved.role)

  const navigate = useNavigate()
  const location = useLocation()

  // 401 时由 client.ts 调用：跳到登录页并记住来源，避免整页刷新丢表单
  useEffect(() => {
    setUnauthorizedHandler(() => {
      navigate('/login', { replace: true, state: { from: location.pathname } })
    })
    return () => setUnauthorizedHandler(() => {})
  }, [navigate, location])

  const apply = (res: LoginResult) => {
    tokenStore.set(res.token)
    localStorage.setItem(PROFILE_KEY, JSON.stringify({ username: res.username, role: res.role }))
    setToken(res.token)
    setUsername(res.username)
    setRole(res.role)
  }

  const login = useCallback(async (u: string, p: string): Promise<LoginResult> => {
    const result = await apiLogin(u, p)
    apply(result)
    return result
  }, [])

  const register = useCallback(async (u: string, p: string) => {
    apply(await apiRegister(u, p))
  }, [])

  const logout = useCallback(() => {
    tokenStore.clear()
    localStorage.removeItem(PROFILE_KEY)
    setToken(null)
    setUsername(null)
    setRole(null)
  }, [])

  return (
    <AuthContext.Provider value={{ token, username, role, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth 必须在 AuthProvider 内部使用')
  }
  return ctx
}
