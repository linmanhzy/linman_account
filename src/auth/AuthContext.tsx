import React, { createContext, useContext, useState, useCallback } from 'react'
import { tokenStore } from '../api/client'
import { login as apiLogin, register as apiRegister, LoginResult } from '../api/auth'

interface AuthValue {
  token: string | null
  username: string | null
  login: (username: string, password: string) => Promise<void>
  register: (username: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(() => tokenStore.get())
  const [username, setUsername] = useState<string | null>(null)

  const apply = (res: LoginResult) => {
    tokenStore.set(res.token)
    setToken(res.token)
    setUsername(res.username)
  }

  const login = useCallback(async (u: string, p: string) => {
    apply(await apiLogin(u, p))
  }, [])

  const register = useCallback(async (u: string, p: string) => {
    apply(await apiRegister(u, p))
  }, [])

  const logout = useCallback(() => {
    tokenStore.clear()
    setToken(null)
    setUsername(null)
  }, [])

  return (
    <AuthContext.Provider value={{ token, username, login, register, logout }}>
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
