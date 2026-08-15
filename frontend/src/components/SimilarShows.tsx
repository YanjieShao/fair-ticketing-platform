import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { formatCents } from '../api/money'
import type { Recommendation } from '../api/types'

export function SimilarShows({ eventId }: { eventId: number }) {
  const recs = useQuery({
    queryKey: ['recommendations', eventId],
    queryFn: () => api<Recommendation[]>(`/api/events/${eventId}/recommendations`),
  })

  if (!recs.data?.length) {
    return null
  }

  return (
    <aside className="similar">
      <h2>Similar shows still on sale</h2>
      <p className="muted">Same genre first. City and price only break ties.</p>
      <ul className="show-list">
        {recs.data.map((show) => (
          <li key={show.id}>
            <Link to={`/events/${show.id}`} className="show-card">
              <div>
                <p className="muted">{show.artistName}</p>
                <h2>{show.title}</h2>
                <p className="muted">{show.city}</p>
                <p className="muted">{show.reasons.join(' · ')}</p>
              </div>
              <div className="show-meta">
                <p>from {formatCents(show.lowestPriceCents)}</p>
                <p className="muted">{show.ticketsAvailable} left</p>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </aside>
  )
}
