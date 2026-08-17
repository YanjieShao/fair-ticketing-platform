import { afterEach, describe, expect, it, vi } from 'vitest'
import { streamWaitingRoom } from './waitingRoom'

describe('streamWaitingRoom', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('pushes frames until the buyer is admitted', async () => {
    localStorage.setItem('ft.accessToken', 'tok')
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('data: {"status":"WAITING"}\n\n'))
        controller.enqueue(new TextEncoder().encode('data: {"status":"ADMITTED"}\n\n'))
        controller.close()
      },
    })
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        body,
      }),
    )
    const updates: string[] = []
    await streamWaitingRoom('5', (room) => updates.push(room.status), new AbortController().signal)
    expect(updates).toEqual(['WAITING', 'ADMITTED'])
  })

  it('drops a rejected token', async () => {
    localStorage.setItem('ft.accessToken', 'stale')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        statusText: 'Unauthorized',
        body: null,
      }),
    )
    await expect(streamWaitingRoom('5', () => undefined, new AbortController().signal)).rejects.toMatchObject({
      status: 401,
    })
    expect(localStorage.getItem('ft.accessToken')).toBeNull()
  })
})
