import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import type { EventDetail } from '../api/types'
import { EventDetailPage } from './EventDetailPage'

const show: EventDetail = {
  id: 1,
  title: 'Live in Dublin',
  artistName: 'The Silent Harbour',
  genre: 'Indie',
  venueName: 'Test Arena',
  city: 'Dublin',
  country: 'Ireland',
  timezone: 'Europe/Dublin',
  category: 'Concert',
  status: 'ON_SALE',
  startsAt: '2026-10-01T19:00:00Z',
  salesStartAt: '2026-08-01T09:00:00Z',
  salesEndAt: '2026-09-30T21:00:00Z',
  waitingRoomEnabled: false,
  forecast: null,
  insight: null,
  tiers: [
    {
      id: 11,
      name: 'Standing',
      priceCents: 5000,
      currency: 'EUR',
      totalQuantity: 50,
      availableQuantity: 12,
      maxPerUser: 4,
      soldOut: false,
    },
    {
      id: 12,
      name: 'Seated',
      priceCents: 9000,
      currency: 'EUR',
      totalQuantity: 20,
      availableQuantity: 0,
      maxPerUser: 4,
      soldOut: true,
    },
  ],
}

function renderDetail() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <MemoryRouter initialEntries={['/events/1']}>
          <Routes>
            <Route path="/events/:eventId" element={<EventDetailPage />} />
            <Route path="/orders/:orderNo" element={<p>Held</p>} />
            <Route path="/waitlist" element={<p>On the waitlist</p>} />
            <Route path="/login" element={<p>Sign in</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

describe('EventDetailPage', () => {
  beforeEach(() => {
    localStorage.setItem('ft.accessToken', 'test-token')
    sessionStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('holds tickets with a stable idempotency key', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/events/1') {
        return {
          status: 200,
          ok: true,
          text: async () => JSON.stringify(show),
        }
      }
      if (url === '/api/events/1/recommendations') {
        return { status: 200, ok: true, text: async () => JSON.stringify([]) }
      }
      if (url === '/api/orders' && (!init?.method || init.method === 'GET')) {
        return {
          status: 200,
          ok: true,
          text: async () => JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
        }
      }
      if (url === '/api/orders') {
        return {
          status: 201,
          ok: true,
          text: async () =>
            JSON.stringify({
              orderNo: 'FT-1',
              eventId: 1,
              tierId: 11,
              quantity: 1,
              unitPriceCents: 5000,
              totalCents: 5000,
              status: 'PENDING_PAYMENT',
              createdAt: '2026-08-14T12:00:00Z',
              expiresAt: '2026-08-14T12:10:00Z',
              paidAt: null,
              completedAt: null,
              eventTitle: 'Live in Dublin',
              artistName: 'The Silent Harbour',
              tierName: 'Standing',
              venueName: 'Test Arena',
              city: 'Dublin',
              startsAt: '2026-09-01T19:00:00Z',
              venueTimezone: 'Europe/Dublin',
            }),
        }
      }
      throw new Error(`unexpected ${url} ${init?.method}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    const user = userEvent.setup()
    renderDetail()

    await user.click(await screen.findByRole('button', { name: 'Purchase' }))
    expect(await screen.findByText('Held')).toBeInTheDocument()

    const checkoutCall = fetchMock.mock.calls.find(
      ([url, init]) => String(url) === '/api/orders' && init?.method === 'POST',
    )
    expect(checkoutCall).toBeTruthy()
    const headers = checkoutCall![1]!.headers as Headers
    expect(headers.get('Idempotency-Key')).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    )
  })

  it('offers the waitlist when a tier is gone', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input)
        if (url === '/api/events/1') {
          return { status: 200, ok: true, text: async () => JSON.stringify(show) }
        }
        if (url === '/api/events/1/recommendations') {
          return { status: 200, ok: true, text: async () => JSON.stringify([]) }
        }
        if (url === '/api/orders') {
          return {
            status: 200,
            ok: true,
            text: async () => JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
          }
        }
        if (url === '/api/waitlist') {
          return {
            status: 201,
            ok: true,
            text: async () =>
              JSON.stringify({
                id: 3,
                eventId: 1,
                tierId: 12,
                status: 'WAITING',
                requestedQuantity: 1,
                positionSeq: 1,
                peopleAhead: 0,
                createdAt: '2026-08-14T12:00:00Z',
                offeredAt: null,
                offerExpiresAt: null,
                convertedOrderId: null,
                eventTitle: 'Live in Dublin',
                artistName: 'EXO',
                tierName: 'Standing',
                venueTimezone: 'Europe/Dublin',
              }),
          }
        }
        throw new Error(`unexpected ${url}`)
      }),
    )

    const user = userEvent.setup()
    renderDetail()

    await user.click(await screen.findByRole('button', { name: 'Join waitlist' }))
    expect(await screen.findByText('On the waitlist')).toBeInTheDocument()
  })

  it('shows a persisted sales insight without calling the LLM from the page', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (String(input) === '/api/events/1') {
          return {
            status: 200,
            ok: true,
            text: async () =>
              JSON.stringify({
                ...show,
                insight: {
                  content: 'Reached 92% of 10000 tickets in 3 hours; waitlist is 180% of remaining stock.',
                  generatedBy: 'TEMPLATE',
                  createdAt: '2026-08-14T12:00:00Z',
                },
              }),
          }
        }
        if (String(input) === '/api/events/1/recommendations') {
          return { status: 200, ok: true, text: async () => JSON.stringify([]) }
        }
        if (String(input) === '/api/orders') {
          return {
            status: 200,
            ok: true,
            text: async () => JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
          }
        }
        throw new Error(`unexpected ${String(input)}`)
      }),
    )

    renderDetail()

    expect(
      await screen.findByText(
        'Reached 92% of 10000 tickets in 3 hours; waitlist is 180% of remaining stock.',
      ),
    ).toBeInTheDocument()
    expect(screen.getByText(/TEMPLATE/)).toBeInTheDocument()
  })
})
