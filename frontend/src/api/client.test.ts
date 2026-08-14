import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, getToken, setToken } from './client'

describe('api client', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('attaches the stored bearer token', async () => {
    setToken('abc.def')
    const fetchMock = vi.fn().mockResolvedValue({
      status: 200,
      ok: true,
      text: async () => JSON.stringify({ id: 1 }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await api('/api/events/1')

    const headers = fetchMock.mock.calls[0][1].headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer abc.def')
  })

  it('turns a structured failure into an ApiError the UI can branch on', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        status: 409,
        ok: false,
        statusText: 'Conflict',
        text: async () => JSON.stringify({ code: 'SOLD_OUT', message: 'Not enough tickets' }),
      }),
    )

    await expect(api('/api/orders', { method: 'POST', body: '{}' })).rejects.toMatchObject({
      name: 'ApiError',
      code: 'SOLD_OUT',
      status: 409,
    })
  })

  it('drops a rejected token so the next request is anonymous', async () => {
    setToken('stale')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        status: 401,
        ok: false,
        statusText: 'Unauthorized',
        text: async () => JSON.stringify({ code: 'UNAUTHORIZED', message: 'Please sign in' }),
      }),
    )

    await expect(api('/api/orders')).rejects.toBeInstanceOf(Error)
    expect(getToken()).toBeNull()
  })
})
