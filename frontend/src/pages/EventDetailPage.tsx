import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { checkoutKey, clearCheckoutKey } from '../api/idempotency'
import { formatCents, formatInstant } from '../api/money'
import type { EventDetail, Order, TicketTier, WaitlistEntry } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { StatusChip } from '../components/StatusChip'

const ADMITTED_PREFIX = 'ft.admitted:'

function isAdmitted(eventId: string): boolean {
  return sessionStorage.getItem(ADMITTED_PREFIX + eventId) === '1'
}

export function EventDetailPage() {
  const { eventId = '' } = useParams()
  const navigate = useNavigate()
  const { signedIn } = useAuth()
  const [quantities, setQuantities] = useState<Record<number, number>>({})

  const event = useQuery({
    queryKey: ['event', eventId],
    queryFn: () => api<EventDetail>(`/api/events/${eventId}`),
  })

  const checkout = useMutation({
    mutationFn: ({ tier, quantity }: { tier: TicketTier; quantity: number }) =>
      api<Order>('/api/orders', {
        method: 'POST',
        headers: { 'Idempotency-Key': checkoutKey(tier.id, quantity) },
        body: JSON.stringify({ tierId: tier.id, quantity }),
      }),
    onSuccess: (order) => {
      clearCheckoutKey(order.tierId, order.quantity)
      navigate(`/orders/${order.orderNo}`)
    },
    onError: (error: unknown) => {
      if (error instanceof ApiError && error.code === 'WAITING_ROOM_TOKEN_REQUIRED') {
        navigate(`/events/${eventId}/queue`)
      }
    },
  })

  const joinWaitlist = useMutation({
    mutationFn: ({ tier, quantity }: { tier: TicketTier; quantity: number }) =>
      api<WaitlistEntry>('/api/waitlist', {
        method: 'POST',
        body: JSON.stringify({ tierId: tier.id, quantity }),
      }),
    onSuccess: () => navigate('/waitlist'),
  })

  if (event.isLoading) {
    return <p>Loading the show…</p>
  }
  if (event.error || !event.data) {
    return <ApiErrorBanner error={event.error ?? new Error('Show not found')} />
  }

  const show = event.data
  const admitted = !show.waitingRoomEnabled || isAdmitted(eventId)

  function quantityFor(tier: TicketTier): number {
    return quantities[tier.id] ?? 1
  }

  function buy(tier: TicketTier) {
    if (!signedIn) {
      navigate('/login', { state: { from: `/events/${eventId}` } })
      return
    }
    if (show.waitingRoomEnabled && !admitted) {
      navigate(`/events/${eventId}/queue`)
      return
    }
    checkout.mutate({ tier, quantity: quantityFor(tier) })
  }

  return (
    <article>
      <p className="eyebrow">{show.artistName}</p>
      <h1>{show.title}</h1>
      <p>
        {show.venueName}, {show.city} · {formatInstant(show.startsAt, show.timezone)}
      </p>
      <p className="muted">
        <StatusChip>{show.status}</StatusChip>
        {show.waitingRoomEnabled ? ' Waiting room is on for this sale.' : null}
      </p>

      <ApiErrorBanner error={checkout.error} />
      <ApiErrorBanner error={joinWaitlist.error} />

      <ul className="tier-list">
        {show.tiers.map((tier) => (
          <li key={tier.id} className="tier">
            <div>
              <h2>{tier.name}</h2>
              <p>
                {formatCents(tier.priceCents, tier.currency)} · {tier.availableQuantity} left
              </p>
            </div>
            {tier.soldOut ? (
              <div className="tier-actions">
                <label>
                  Qty
                  <select
                    value={quantityFor(tier)}
                    onChange={(e) =>
                      setQuantities((current) => ({ ...current, [tier.id]: Number(e.target.value) }))
                    }
                  >
                    {Array.from({ length: Math.max(1, tier.maxPerUser) }, (_, i) => i + 1).map((n) => (
                      <option key={n} value={n}>
                        {n}
                      </option>
                    ))}
                  </select>
                </label>
                <button type="button" onClick={() => {
                  if (!signedIn) {
                    navigate('/login', { state: { from: `/events/${eventId}` } })
                    return
                  }
                  joinWaitlist.mutate({ tier, quantity: quantityFor(tier) })
                }}>
                  Join waitlist
                </button>
              </div>
            ) : (
              <div className="tier-actions">
                <label>
                  Qty
                  <select
                    value={quantityFor(tier)}
                    onChange={(e) =>
                      setQuantities((current) => ({ ...current, [tier.id]: Number(e.target.value) }))
                    }
                  >
                    {Array.from(
                      { length: Math.min(tier.maxPerUser, Math.max(1, tier.availableQuantity)) },
                      (_, i) => i + 1,
                    ).map((n) => (
                      <option key={n} value={n}>
                        {n}
                      </option>
                    ))}
                  </select>
                </label>
                <button type="button" disabled={checkout.isPending} onClick={() => buy(tier)}>
                  {show.waitingRoomEnabled && !admitted ? 'Join queue' : 'Hold tickets'}
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>

      <p>
        <Link to="/">Back to shows</Link>
      </p>
    </article>
  )
}
