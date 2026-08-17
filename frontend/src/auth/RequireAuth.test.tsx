import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider } from './AuthContext'
import { RequireAdmin } from './RequireAdmin'
import { RequireAuth } from './RequireAuth'

function renderAt(path: string, token: string | null) {
  if (token) {
    localStorage.setItem('ft.accessToken', token)
  } else {
    localStorage.clear()
  }
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/login" element={<p>Sign in</p>} />
            <Route path="/" element={<p>Home</p>} />
            <Route
              path="/orders"
              element={
                <RequireAuth>
                  <p>Orders</p>
                </RequireAuth>
              }
            />
            <Route
              path="/admin"
              element={
                <RequireAdmin>
                  <p>Admin</p>
                </RequireAdmin>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

function tokenWithRoles(roles: string[]): string {
  const payload = btoa(JSON.stringify({ roles })).replace(/\+/g, '-').replace(/\//g, '_')
  return `header.${payload}.sig`
}

describe('route guards', () => {
  it('sends anonymous buyers to login', () => {
    renderAt('/orders', null)
    expect(screen.getByText('Sign in')).toBeInTheDocument()
  })

  it('lets a signed-in buyer through', () => {
    renderAt('/orders', tokenWithRoles(['USER']))
    expect(screen.getByText('Orders')).toBeInTheDocument()
  })

  it('keeps a non-admin off the dashboard', () => {
    renderAt('/admin', tokenWithRoles(['USER']))
    expect(screen.getByText('Home')).toBeInTheDocument()
  })

  it('lets an admin into the dashboard', () => {
    renderAt('/admin', tokenWithRoles(['ADMIN']))
    expect(screen.getByText('Admin')).toBeInTheDocument()
  })
})
