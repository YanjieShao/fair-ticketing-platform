import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'
import type { ReactNode } from 'react'
import { RequireAuth } from './RequireAuth'

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { isAdmin } = useAuth()

  return (
    <RequireAuth>
      {isAdmin ? children : <Navigate to="/" replace />}
    </RequireAuth>
  )
}
