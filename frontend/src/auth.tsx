import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, tokenStore } from './api'
import type { AuthResponse, User } from './types'

interface AuthState {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName: string) => Promise<void>
  logout: () => Promise<void>
}

const Ctx = createContext<AuthState | null>(null)
const USER_KEY = 'interviewos.user'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => {
    try { return tokenStore.get() ? (JSON.parse(localStorage.getItem(USER_KEY) ?? 'null') as User | null) : null } catch { return null }
  })
  const [loading] = useState(false)

  const accept = useCallback((r: AuthResponse) => {
    tokenStore.set(r.token)
    localStorage.setItem(USER_KEY, JSON.stringify(r.user))
    setUser(r.user)
  }, [])

  const clear = useCallback(() => { tokenStore.clear(); localStorage.removeItem(USER_KEY); setUser(null) }, [])

  useEffect(() => {
    window.addEventListener('interviewos:unauthorized', clear)
    return () => window.removeEventListener('interviewos:unauthorized', clear)
  }, [clear])

  const value = useMemo<AuthState>(() => ({
    user, loading,
    login: async (email, password) => accept((await api.post<AuthResponse>('/auth/login', { email, password })).data),
    register: async (email, password, displayName) => accept((await api.post<AuthResponse>('/auth/register', { email, password, displayName })).data),
    logout: async () => { try { await api.post('/auth/logout') } finally { clear() } },
  }), [user, loading, accept, clear])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useAuth(): AuthState {
  const v = useContext(Ctx)
  if (!v) throw new Error('useAuth must be used inside AuthProvider')
  return v
}
