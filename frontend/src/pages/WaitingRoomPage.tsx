import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { formatWait } from '../api/money'
import type { WaitingRoom } from '../api/types'
import { streamWaitingRoom } from '../api/waitingRoom'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { StatusChip } from '../components/StatusChip'

export function WaitingRoomPage() {
  const { eventId = '' } = useParams()
  const navigate = useNavigate()
  const [streamed, setStreamed] = useState<WaitingRoom | null>(null)
  const [streamFailed, setStreamFailed] = useState(false)

  const join = useQuery({
    queryKey: ['waiting-room-join', eventId],
    queryFn: () => api<WaitingRoom>(`/api/waiting-room/${eventId}/join`, { method: 'POST' }),
  })

  useEffect(() => {
    if (!join.isSuccess || join.data.status === 'ADMITTED') {
      return
    }
    const abort = new AbortController()
    streamWaitingRoom(eventId, setStreamed, abort.signal).catch(() => {
      if (!abort.signal.aborted) {
        setStreamFailed(true)
      }
    })
    return () => abort.abort()
  }, [join.isSuccess, join.data, eventId])

  const status = useQuery({
    queryKey: ['waiting-room', eventId],
    queryFn: () => api<WaitingRoom>(`/api/waiting-room/${eventId}`),
    enabled: join.isSuccess && streamFailed,
    refetchInterval: (query) => (query.state.data?.status === 'ADMITTED' ? false : 2000),
  })

  const room = streamed ?? status.data ?? join.data

  useEffect(() => {
    if (room?.status === 'ADMITTED') {
      sessionStorage.setItem(`ft.admitted:${eventId}`, '1')
      const timer = window.setTimeout(() => navigate(`/events/${eventId}`), 800)
      return () => window.clearTimeout(timer)
    }
  }, [room?.status, eventId, navigate])

  return (
    <section className="queue">
      <p className="eyebrow">Waiting room</p>
      <h1>Stay in line</h1>
      <p>Places are released in arrival order. Refreshing does not move you forward.</p>

      <ApiErrorBanner error={join.error ?? status.error} />

      {room ? (
        <div className="queue-card">
          <StatusChip>{room.status}</StatusChip>
          {room.status === 'WAITING' ? (
            <>
              <p className="position">#{room.position}</p>
              <p>
                {room.queueLength} in line · {formatWait(room.estimatedWaitSeconds)}
              </p>
            </>
          ) : (
            <p>You are through. Taking you to checkout.</p>
          )}
        </div>
      ) : (
        <p>Joining the queue…</p>
      )}

      <p>
        <Link to={`/events/${eventId}`}>Leave and go back</Link>
      </p>
    </section>
  )
}
