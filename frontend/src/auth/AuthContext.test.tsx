import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, useAuth } from './AuthContext'

function Probe() {
  const auth = useAuth()
  return (
    <div>
      <span>{auth.signedIn ? 'in' : 'out'}</span>
      <button type="button" onClick={() => void auth.login('a@b.c', 'secret12')}>
        login
      </button>
      <button type="button" onClick={() => void auth.register('a@b.c', 'secret12', 'Ada')}>
        register
      </button>
      <button type="button" onClick={() => auth.logout()}>
        logout
      </button>
    </div>
  )
}

describe('AuthProvider', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('stores a token after login and clears it on logout', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        status: 200,
        ok: true,
        text: async () => JSON.stringify({ accessToken: 'abc.def', expiresAt: '2026-08-14T14:00:00Z' }),
      }),
    )
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <AuthProvider>
            <Probe />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(screen.getByText('out')).toBeInTheDocument()
    await userEvent.click(screen.getByText('login'))
    expect(await screen.findByText('in')).toBeInTheDocument()
    await userEvent.click(screen.getByText('logout'))
    expect(screen.getByText('out')).toBeInTheDocument()
  })

  it('stores a token after register', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        status: 201,
        ok: true,
        text: async () => JSON.stringify({ accessToken: 'abc.def', expiresAt: '2026-08-14T14:00:00Z' }),
      }),
    )
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <AuthProvider>
            <Probe />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    )
    await userEvent.click(screen.getByText('register'))
    expect(await screen.findByText('in')).toBeInTheDocument()
  })
})
