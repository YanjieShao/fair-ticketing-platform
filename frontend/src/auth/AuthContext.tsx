import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { api, getToken, setToken } from '../api/client'
import type { TokenResponse } from '../api/types'
import { isAdminToken } from './roles'

type AuthContextValue = {
  token: string | null
  signedIn: boolean
  isAdmin: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => getToken())

  const value = useMemo<AuthContextValue>(
    () => ({
      token,
      signedIn: Boolean(token),
      isAdmin: isAdminToken(token),
      async login(email, password) {
        const issued = await api<TokenResponse>('/api/auth/login', {
          method: 'POST',
          body: JSON.stringify({ email, password }),
        })
        setToken(issued.accessToken)
        setTokenState(issued.accessToken)
      },
      async register(email, password, displayName) {
        const issued = await api<TokenResponse>('/api/auth/register', {
          method: 'POST',
          body: JSON.stringify({ email, password, displayName }),
        })
        setToken(issued.accessToken)
        setTokenState(issued.accessToken)
      },
      logout() {
        setToken(null)
        setTokenState(null)
      },
    }),
    [token],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
