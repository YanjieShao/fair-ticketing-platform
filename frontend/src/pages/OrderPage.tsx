import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { formatCents, formatInstant } from '../api/money'
import type { Order } from '../api/types'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { StatusChip } from '../components/StatusChip'

export function OrderPage() {
  const { orderNo = '' } = useParams()
  const queryClient = useQueryClient()

  const order = useQuery({
    queryKey: ['order', orderNo],
    queryFn: () => api<Order>(`/api/orders/${orderNo}`),
  })

  const pay = useMutation({
    mutationFn: () => api<Order>(`/api/orders/${orderNo}/pay`, { method: 'POST' }),
    onSuccess: (updated) => queryClient.setQueryData(['order', orderNo], updated),
  })

  const cancel = useMutation({
    mutationFn: () => api<Order>(`/api/orders/${orderNo}/cancel`, { method: 'POST' }),
    onSuccess: (updated) => queryClient.setQueryData(['order', orderNo], updated),
  })

  if (order.isLoading) {
    return <p>Loading order…</p>
  }
  if (order.error || !order.data) {
    return <ApiErrorBanner error={order.error ?? new Error('Order not found')} />
  }

  const current = order.data
  const canPay = current.status === 'PENDING_PAYMENT' || current.status === 'CREATED'

  return (
    <article>
      <p className="eyebrow">Order {current.orderNo}</p>
      <h1>{formatCents(current.totalCents)}</h1>
      <p>
        {current.quantity} ticket{current.quantity === 1 ? '' : 's'} · <StatusChip>{current.status}</StatusChip>
      </p>
      <p className="muted">Held {formatInstant(current.createdAt)}</p>
      {current.expiresAt && canPay ? (
        <p>Pay before {formatInstant(current.expiresAt)} or the hold is released.</p>
      ) : null}

      <ApiErrorBanner error={pay.error} />
      <ApiErrorBanner error={cancel.error} />

      {canPay ? (
        <div className="actions">
          <button type="button" disabled={pay.isPending} onClick={() => pay.mutate()}>
            Pay now
          </button>
          <button type="button" className="ghost" disabled={cancel.isPending} onClick={() => cancel.mutate()}>
            Cancel hold
          </button>
        </div>
      ) : null}

      <p>
        <Link to="/orders">All orders</Link>
      </p>
    </article>
  )
}
