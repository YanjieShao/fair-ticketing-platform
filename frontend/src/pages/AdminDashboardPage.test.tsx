import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import type { AdminDashboard } from '../api/types'
import { AdminDashboardPage } from './AdminDashboardPage'

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: unknown }) => <div>{children as never}</div>,
  LineChart: ({ children }: { children: unknown }) => <div data-testid="sales-trend">{children as never}</div>,
  BarChart: ({ children }: { children: unknown }) => <div data-testid="bar-chart">{children as never}</div>,
  CartesianGrid: () => null,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: () => null,
  Line: () => null,
  Bar: () => null,
}))

const dashboard: AdminDashboard = {
  kpis: {
    eventsOnSale: 1,
    eventsSoldOut: 1,
    capacity: 11_000,
    reserved: 10_200,
    remaining: 800,
    sellThroughPercent: 93,
    waitlistPeople: 210,
    waitlistTickets: 1_480,
    paidOrders: 80,
    paidTickets: 240,
    paidCents: 1_200_000,
  },
  orderStatus: [{ name: 'PAID', count: 80, tickets: 240, cents: 1_200_000 }],
  forecastRisk: [{ name: 'HIGH', count: 1, tickets: 0, cents: 0 }],
  categories: [{ category: 'Concert', reserved: 10_200, heldCents: 80_000_000 }],
  salesTrend: [{ day: '2026-08-14', orders: 4, tickets: 12, cents: 60_000 }],
  hotEvents: [
    {
      eventId: 1,
      title: 'Night at Croke Park',
      artistName: 'Taylor Swift',
      status: 'ON_SALE',
      soldPercent: 92,
      reserved: 9_200,
      remaining: 800,
      waitlistPeople: 200,
      waitlistTickets: 1_440,
      waitlistVsRemainingPercent: 180,
      demandRisk: 'HIGH',
    },
    {
      eventId: 2,
      title: 'Club show',
      artistName: 'Act',
      status: 'SOLD_OUT',
      soldPercent: 100,
      reserved: 1_000,
      remaining: 0,
      waitlistPeople: 10,
      waitlistTickets: 40,
      waitlistVsRemainingPercent: null,
      demandRisk: 'LOW',
    },
  ],
}

describe('AdminDashboardPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders computed KPIs and filters the live-show table', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input)
        if (url === '/api/admin/dashboard') {
          return { status: 200, ok: true, text: async () => JSON.stringify(dashboard) }
        }
        if (url === '/api/admin/insights') {
          return { status: 200, ok: true, text: async () => JSON.stringify([]) }
        }
        throw new Error(`unexpected ${url}`)
      }),
    )

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <AuthProvider>
          <MemoryRouter>
            <AdminDashboardPage />
          </MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('93%')).toBeInTheDocument()
    expect(screen.getByText('Night at Croke Park')).toBeInTheDocument()
    expect(screen.getByText('Club show')).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Forecast risk'), 'HIGH')
    expect(screen.getByText('Night at Croke Park')).toBeInTheDocument()
    expect(screen.queryByText('Club show')).not.toBeInTheDocument()
  })
})
