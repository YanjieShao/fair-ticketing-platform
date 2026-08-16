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
    onSuccess: (updated) => {
      queryClient.setQueryData(['order', orderNo], updated)
      queryClient.invalidateQueries({ queryKey: ['orders'] })
    },
  })

  const cancel = useMutation({
    mutationFn: () => api<Order>(`/api/orders/${orderNo}/cancel`, { method: 'POST' }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['order', orderNo], updated)
      queryClient.invalidateQueries({ queryKey: ['orders'] })
    },
  })

  if (order.isLoading) {
    return <p>Loading order…</p>
  }
  if (order.error || !order.data) {
    return <ApiErrorBanner error={order.error ?? new Error('Order not found')} />
  }

  const current = order.data
  const canPay = current.status === 'PENDING_PAYMENT' || current.status === 'CREATED'
  const canReturn = current.status === 'PAID' || current.status === 'COMPLETED'
  const showTitle = current.eventTitle ?? `Event ${current.eventId}`
  const venueLine = [current.venueName, current.city].filter(Boolean).join(', ')

  return (
    <article className="ticket">
      {current.artistName ? <p className="eyebrow">{current.artistName}</p> : null}
      <h1>
        <Link to={`/events/${current.eventId}`}>{showTitle}</Link>
      </h1>
      {venueLine ? <p>{venueLine}</p> : null}
      {current.startsAt ? (
        <p className="muted">{formatInstant(current.startsAt, current.venueTimezone)}</p>
      ) : null}

      <p>
        {current.tierName ?? `Tier ${current.tierId}`} · {current.quantity} ticket
        {current.quantity === 1 ? '' : 's'} · {formatCents(current.totalCents)} ·{' '}
        <StatusChip>{current.status}</StatusChip>
      </p>
      <p className="muted mono">
        {current.orderNo} · held {formatInstant(current.createdAt, current.venueTimezone)}
      </p>
      {current.expiresAt && canPay ? (
        <p>
          Confirm before {formatInstant(current.expiresAt, current.venueTimezone)} or the
          reservation is released.
        </p>
      ) : null}

      <ApiErrorBanner error={pay.error} />
      <ApiErrorBanner error={cancel.error} />

      {canPay ? (
        <div className="actions">
          <button type="button" disabled={pay.isPending} onClick={() => pay.mutate()}>
            Confirm
          </button>
          <button type="button" className="ghost" disabled={cancel.isPending} onClick={() => cancel.mutate()}>
            Cancel
          </button>
        </div>
      ) : null}

      {canReturn ? (
        <div className="actions">
          <button type="button" className="ghost" disabled={cancel.isPending} onClick={() => cancel.mutate()}>
            Return tickets
          </button>
          <p className="muted">
            Payment is mocked. Returning restocks the tier and offers the waitlist; no money is
            sent.
          </p>
        </div>
      ) : null}

      <p>
        <Link to="/orders">All orders</Link>
      </p>
    </article>
  )
}
