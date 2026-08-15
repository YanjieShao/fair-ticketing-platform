import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { formatInstant } from '../api/money'
import type { NotificationItem, SpringPage } from '../api/types'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { StatusChip } from '../components/StatusChip'

export function NotificationsPage() {
  const inbox = useQuery({
    queryKey: ['notifications'],
    queryFn: () => api<SpringPage<NotificationItem>>('/api/notifications'),
  })

  return (
    <section>
      <h1>Notifications</h1>
      <p className="muted">
        Waitlist offers and sales briefs land here. There is no preference centre;
        the system only writes what already happened.
      </p>
      <ApiErrorBanner error={inbox.error} />
      {inbox.isLoading ? <p>Loading notifications…</p> : null}

      <ul className="plain-list">
        {inbox.data?.content.map((item) => (
          <li key={item.id} className="wait-row">
            <div>
              <StatusChip>{item.type}</StatusChip>
              <h2>{item.title}</h2>
              <p>{item.body}</p>
              <p className="muted">
                {item.generatedBy} · {formatInstant(item.createdAt)}
              </p>
            </div>
          </li>
        ))}
      </ul>

      {inbox.data && inbox.data.content.length === 0 ? (
        <p className="muted">
          Nothing yet. Offers appear after a cancellation; admins also see sales insights.{' '}
          <Link to="/waitlist">Open waitlist</Link>
        </p>
      ) : null}
    </section>
  )
}
