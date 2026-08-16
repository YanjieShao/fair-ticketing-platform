import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { checkoutKey, clearCheckoutKey } from '../api/idempotency'
import { formatCents, formatInstant } from '../api/money'
import type { EventDetail, Order, SpringPage, TicketTier, WaitlistEntry } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { SimilarShows } from '../components/SimilarShows'
import { StatusChip } from '../components/StatusChip'

const ADMITTED_PREFIX = 'ft.admitted:'
const HOLDING = new Set(['CREATED', 'PENDING_PAYMENT', 'PAID', 'COMPLETED'])

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

  const mine = useQuery({
    queryKey: ['orders'],
    queryFn: () => api<SpringPage<Order>>('/api/orders'),
    enabled: signedIn,
  })

  useEffect(() => {
    if (!signedIn || !event.data?.waitingRoomEnabled || isAdmitted(eventId)) {
      return
    }
    navigate(`/events/${eventId}/queue`, { replace: true })
  }, [signedIn, event.data, eventId, navigate])

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

  function ownedOn(tierId: number): number {
    return (mine.data?.content ?? [])
      .filter((order) => order.tierId === tierId && HOLDING.has(order.status))
      .reduce((total, order) => total + order.quantity, 0)
  }

  function quantityFor(tier: TicketTier): number {
    const max = Math.max(1, remainingAllowance(tier))
    return Math.min(quantities[tier.id] ?? 1, max)
  }

  function remainingAllowance(tier: TicketTier): number {
    return Math.max(0, tier.maxPerUser - ownedOn(tier.id))
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
        {show.waitingRoomEnabled
          ? ' High-demand sale: signed-in buyers join the waiting room automatically. The waitlist is only after sell-out.'
          : null}
      </p>
      {show.forecast ? (
        <p>
          Forecast: {show.forecast.expectedDemand.toLocaleString('en-IE')} expected against a house of{' '}
          {show.forecast.capacity.toLocaleString('en-IE')} ({show.forecast.riskLevel}).
        </p>
      ) : null}
      {show.insight ? (
        <aside className="insight">
          <p className="eyebrow">Sales insight · {show.insight.generatedBy}</p>
          <p>{show.insight.content}</p>
        </aside>
      ) : null}

      <ApiErrorBanner error={checkout.error} />
      <ApiErrorBanner error={joinWaitlist.error} />

      <ul className="tier-list">
        {show.tiers.map((tier) => (
          <li key={tier.id} className="tier">
            <div>
              <h2>{tier.name}</h2>
              <p>
                {formatCents(tier.priceCents, tier.currency)} · {tier.availableQuantity} left
                {ownedOn(tier.id) > 0 ? ` · you have ${ownedOn(tier.id)}` : ''}
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
                    {Array.from({ length: Math.max(1, remainingAllowance(tier)) }, (_, i) => i + 1).map((n) => (
                      <option key={n} value={n}>
                        {n}
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  type="button"
                  disabled={remainingAllowance(tier) === 0}
                  onClick={() => {
                    if (!signedIn) {
                      navigate('/login', { state: { from: `/events/${eventId}` } })
                      return
                    }
                    joinWaitlist.mutate({ tier, quantity: quantityFor(tier) })
                  }}
                >
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
                      {
                        length: Math.min(
                          remainingAllowance(tier) || 1,
                          Math.max(1, tier.availableQuantity),
                        ),
                      },
                      (_, i) => i + 1,
                    ).map((n) => (
                      <option key={n} value={n}>
                        {n}
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  type="button"
                  disabled={checkout.isPending || remainingAllowance(tier) === 0}
                  onClick={() => buy(tier)}
                >
                  Purchase
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>

      {show.status === 'SOLD_OUT' || show.tiers.some((tier) => tier.soldOut) ? (
        <SimilarShows eventId={show.id} />
      ) : null}

      <p>
        <Link to="/">Back to shows</Link>
      </p>
    </article>
  )
}
