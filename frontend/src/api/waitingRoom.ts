import { consumeSseJson } from './sse'
import { ApiError, getToken, setToken } from './client'
import type { WaitingRoom } from './types'

/**
 * EventSource cannot set Authorization, so this uses fetch and parses SSE.
 * The GET poll endpoint remains as a fallback when the stream dies.
 */
export async function streamWaitingRoom(
  eventId: string,
  onUpdate: (room: WaitingRoom) => void,
  signal: AbortSignal,
): Promise<void> {
  const headers = new Headers({ Accept: 'text/event-stream' })
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`/api/waiting-room/${eventId}/stream`, { headers, signal })
  if (!response.ok) {
    if (response.status === 401) {
      setToken(null)
    }
    throw new ApiError('INTERNAL_ERROR', response.statusText, response.status)
  }
  if (!response.body) {
    throw new ApiError('INTERNAL_ERROR', 'Waiting-room stream had no body', 500)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const consumed = consumeSseJson<WaitingRoom>(buffer)
      buffer = consumed.rest
      for (const frame of consumed.frames) {
        onUpdate(frame)
        if (frame.status !== 'WAITING') {
          return
        }
      }
    }
  } finally {
    await reader.cancel().catch(() => undefined)
  }
}
