import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { formatCents, formatInstant } from '../api/money'
import type { Order, SpringPage } from '../api/types'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { StatusChip } from '../components/StatusChip'

export function OrdersPage() {
  const orders = useQuery({
    queryKey: ['orders'],
    queryFn: () => api<SpringPage<Order>>('/api/orders'),
  })

  return (
    <section>
      <h1>Your orders</h1>
      <ApiErrorBanner error={orders.error} />
      {orders.isLoading ? <p>Loading orders…</p> : null}
      <ul className="show-list">
        {orders.data?.content.map((order) => (
          <li key={order.orderNo}>
            <Link to={`/orders/${order.orderNo}`} className="show-card">
              <div>
                {order.artistName ? <p className="muted">{order.artistName}</p> : null}
                <h2>{order.eventTitle ?? `Event ${order.eventId}`}</h2>
                <p>
                  {order.tierName ?? `Tier ${order.tierId}`} · {order.quantity} ticket
                  {order.quantity === 1 ? '' : 's'}
                </p>
                {order.venueName || order.city ? (
                  <p className="muted">
                    {[order.venueName, order.city].filter(Boolean).join(', ')}
                    {order.startsAt
                      ? ` · ${formatInstant(order.startsAt, order.venueTimezone)}`
                      : ''}
                  </p>
                ) : null}
                <p className="muted mono">{order.orderNo}</p>
              </div>
              <div className="show-meta">
                <StatusChip>{order.status}</StatusChip>
                <p>{formatCents(order.totalCents)}</p>
                <p className="muted">{formatInstant(order.createdAt, order.venueTimezone)}</p>
              </div>
            </Link>
          </li>
        ))}
      </ul>
      {orders.data && orders.data.content.length === 0 ? (
        <p className="muted">No orders yet.</p>
      ) : null}
    </section>
  )
}
