import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { checkoutKey, clearCheckoutKey } from '../api/idempotency'
import { formatInstant } from '../api/money'
import type { Order, SpringPage, WaitlistEntry } from '../api/types'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { StatusChip } from '../components/StatusChip'

export function WaitlistPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const entries = useQuery({
    queryKey: ['waitlist'],
    queryFn: () => api<SpringPage<WaitlistEntry>>('/api/waitlist'),
  })

  const leave = useMutation({
    mutationFn: (id: number) => api<WaitlistEntry>(`/api/waitlist/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['waitlist'] }),
  })

  const buyOffer = useMutation({
    mutationFn: (entry: WaitlistEntry) =>
      api<Order>('/api/orders', {
        method: 'POST',
        headers: { 'Idempotency-Key': checkoutKey(entry.tierId, entry.requestedQuantity) },
        body: JSON.stringify({ tierId: entry.tierId, quantity: entry.requestedQuantity }),
      }),
    onSuccess: (order) => {
      clearCheckoutKey(order.tierId, order.quantity)
      navigate(`/orders/${order.orderNo}`)
    },
  })

  return (
    <section>
      <h1>Waitlist</h1>
      <p className="muted">Returned tickets go to the person who joined first, not whoever refreshes fastest.</p>
      <ApiErrorBanner error={entries.error} />
      <ApiErrorBanner error={leave.error} />
      <ApiErrorBanner error={buyOffer.error} />

      {entries.isLoading ? <p>Loading waitlist…</p> : null}

      <ul className="plain-list">
        {entries.data?.content.map((entry) => (
          <li key={entry.id} className="wait-row">
            <div>
              <StatusChip>{entry.status}</StatusChip>
              <p>
                {entry.requestedQuantity} ticket{entry.requestedQuantity === 1 ? '' : 's'} · joined{' '}
                {formatInstant(entry.createdAt)}
              </p>
              {entry.status === 'WAITING' ? <p>{entry.peopleAhead} ahead of you</p> : null}
              {entry.status === 'OFFERED' && entry.offerExpiresAt ? (
                <p>Buy before {formatInstant(entry.offerExpiresAt)}</p>
              ) : null}
            </div>
            <div className="actions">
              {entry.status === 'OFFERED' ? (
                <button type="button" disabled={buyOffer.isPending} onClick={() => buyOffer.mutate(entry)}>
                  Buy now
                </button>
              ) : null}
              {entry.status === 'WAITING' || entry.status === 'OFFERED' ? (
                <button type="button" className="ghost" onClick={() => leave.mutate(entry.id)}>
                  Leave
                </button>
              ) : null}
            </div>
          </li>
        ))}
      </ul>

      {entries.data && entries.data.content.length === 0 ? (
        <p className="muted">
          You are not on a waitlist. Join from a <Link to="/">sold-out show</Link>.
        </p>
      ) : null}
    </section>
  )
}
