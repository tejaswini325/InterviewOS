import axios from 'axios'

const TOKEN_KEY = 'interviewos.token'

// The JWT lives in localStorage so a refresh keeps the session. Trade-off: readable by any XSS; mitigated by
// the API never rendering untrusted HTML and by short token expiry (see docs/ARCHITECTURE.md).
export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

export const api = axios.create({ baseURL: API_BASE_URL, timeout: 20000 })
api.interceptors.request.use((cfg) => {
  const t = tokenStore.get()
  if (t) cfg.headers.Authorization = `Bearer ${t}`
  return cfg
})

api.interceptors.response.use(
  (r) => r,
  (err) => {
    const url: string = err?.config?.url ?? ''
    if (err?.response?.status === 401 && !url.startsWith('/auth/')) {
      tokenStore.clear()
      window.dispatchEvent(new Event('interviewos:unauthorized'))
    }
    return Promise.reject(err)
  },
)

export interface ApiProblem { status?: number; code?: string; message: string; fieldErrors?: Record<string, string>; extra?: Record<string, unknown> }

export function describeError(e: unknown): ApiProblem {
  if (axios.isAxiosError(e)) {
    if (!e.response) return { message: 'Cannot reach the server. Check your connection and try again.' }
    const d = e.response.data as Partial<ApiProblem> | undefined
    return { status: e.response.status, code: d?.code, message: d?.message ?? 'Something went wrong.', fieldErrors: d?.fieldErrors, extra: d?.extra }
  }
  return { message: 'Something went wrong.' }
}
