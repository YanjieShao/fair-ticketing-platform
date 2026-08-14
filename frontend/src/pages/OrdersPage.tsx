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
      <ul className="plain-list">
        {orders.data?.content.map((order) => (
          <li key={order.orderNo}>
            <Link to={`/orders/${order.orderNo}`} className="row-link">
              <span>{order.orderNo}</span>
              <StatusChip>{order.status}</StatusChip>
              <span>{formatCents(order.totalCents)}</span>
              <span className="muted">{formatInstant(order.createdAt)}</span>
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
