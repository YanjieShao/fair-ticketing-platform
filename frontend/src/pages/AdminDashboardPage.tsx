import { useQuery } from '@tanstack/react-query'
import { useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { api } from '../api/client'
import { formatCents } from '../api/money'
import type { AdminDashboard, AdminInsight } from '../api/types'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { StatusChip } from '../components/StatusChip'

const AXIS = { fill: '#cfc6b4', fontSize: 12 }
const GRID = '#2c261d'
const INK = '#f3ead7'
const RED = '#c4452d'
const GREEN = '#d7e3c0'

const tooltipStyle = {
  background: '#1a1611',
  border: '1px solid #2c261d',
  color: INK,
}

export function AdminDashboardPage() {
  const [status, setStatus] = useState('')
  const [risk, setRisk] = useState('')

  const dashboard = useQuery({
    queryKey: ['admin-dashboard'],
    queryFn: () => api<AdminDashboard>('/api/admin/dashboard'),
  })
  const insights = useQuery({
    queryKey: ['admin-insights'],
    queryFn: () => api<AdminInsight[]>('/api/admin/insights'),
  })

  const hot = useMemo(() => {
    const rows = dashboard.data?.hotEvents ?? []
    return rows.filter((row) => {
      if (status && row.status !== status) {
        return false
      }
      if (risk && row.demandRisk !== risk) {
        return false
      }
      return true
    })
  }, [dashboard.data, status, risk])

  if (dashboard.isLoading) {
    return <p>Loading dashboard…</p>
  }
  if (dashboard.error || !dashboard.data) {
    return <ApiErrorBanner error={dashboard.error ?? new Error('Dashboard unavailable')} />
  }

  const kpis = dashboard.data.kpis

  return (
    <section>
      <div className="page-head">
        <p className="eyebrow">Admin</p>
        <h1>Sales dashboard</h1>
        <p className="muted">
          Totals come from MySQL. Charts only plot those numbers. Insights phrase
          the same snapshots.{' '}
          <Link to="/admin/events/new">List a show</Link>
        </p>
      </div>

      <div className="kpi-grid">
        <Kpi label="On sale" value={String(kpis.eventsOnSale)} hint={`${kpis.eventsSoldOut} sold out`} />
        <Kpi label="Sell-through" value={`${kpis.sellThroughPercent}%`} hint={`${kpis.reserved.toLocaleString('en-IE')} of ${kpis.capacity.toLocaleString('en-IE')} held`} />
        <Kpi label="Remaining" value={kpis.remaining.toLocaleString('en-IE')} hint="Live inventory" />
        <Kpi label="Waitlist" value={String(kpis.waitlistPeople)} hint={`${kpis.waitlistTickets.toLocaleString('en-IE')} tickets wanted`} />
        <Kpi label="Paid revenue" value={formatCents(kpis.paidCents)} hint={`${kpis.paidOrders} paid orders · ${kpis.paidTickets.toLocaleString('en-IE')} tickets`} />
      </div>

      <div className="dash-charts">
        <ChartPanel title="Paid tickets, last 14 days">
          <ResponsiveContainer width="100%" height={240}>
            <LineChart data={dashboard.data.salesTrend}>
              <CartesianGrid stroke={GRID} />
              <XAxis dataKey="day" tick={AXIS} tickFormatter={(day: string) => day.slice(5)} />
              <YAxis tick={AXIS} allowDecimals={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Line type="monotone" dataKey="tickets" stroke={RED} strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </ChartPanel>
        <ChartPanel title="Orders by status">
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={dashboard.data.orderStatus}>
              <CartesianGrid stroke={GRID} />
              <XAxis dataKey="name" tick={AXIS} interval={0} angle={-25} height={60} textAnchor="end" />
              <YAxis tick={AXIS} allowDecimals={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Bar dataKey="count" fill={RED} />
            </BarChart>
          </ResponsiveContainer>
        </ChartPanel>
        <ChartPanel title="Held tickets by category">
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={dashboard.data.categories}>
              <CartesianGrid stroke={GRID} />
              <XAxis dataKey="category" tick={AXIS} />
              <YAxis tick={AXIS} allowDecimals={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Bar dataKey="reserved" fill={GREEN} />
            </BarChart>
          </ResponsiveContainer>
        </ChartPanel>
        <ChartPanel title="Forecast risk">
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={dashboard.data.forecastRisk}>
              <CartesianGrid stroke={GRID} />
              <XAxis dataKey="name" tick={AXIS} />
              <YAxis tick={AXIS} allowDecimals={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Bar dataKey="count" fill={INK} />
            </BarChart>
          </ResponsiveContainer>
        </ChartPanel>
      </div>

      <div className="page-head">
        <h2>Live shows</h2>
        <p className="muted">Highest sell-through first. Filters only hide rows; they do not recompute totals.</p>
        <form className="filters" onSubmit={(event) => event.preventDefault()}>
          <label>
            Status
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All</option>
              <option value="ON_SALE">ON_SALE</option>
              <option value="SOLD_OUT">SOLD_OUT</option>
            </select>
          </label>
          <label>
            Forecast risk
            <select value={risk} onChange={(e) => setRisk(e.target.value)}>
              <option value="">All</option>
              <option value="LOW">LOW</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="HIGH">HIGH</option>
            </select>
          </label>
        </form>
      </div>

      <table className="data-table">
        <thead>
          <tr>
            <th>Show</th>
            <th>Status</th>
            <th>Sold</th>
            <th>Waitlist</th>
            <th>Risk</th>
          </tr>
        </thead>
        <tbody>
          {hot.map((row) => (
            <tr key={row.eventId}>
              <td>
                <Link to={`/events/${row.eventId}`}>{row.title}</Link>
                <div className="muted">{row.artistName}</div>
              </td>
              <td>
                <StatusChip>{row.status}</StatusChip>
              </td>
              <td>
                {row.soldPercent}% · {row.remaining.toLocaleString('en-IE')} left
              </td>
              <td>
                {row.waitlistPeople} people
                {row.waitlistVsRemainingPercent != null ? ` · ${row.waitlistVsRemainingPercent}% of remaining` : ''}
              </td>
              <td>{row.demandRisk ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {hot.length === 0 ? <p className="muted">No live shows match those filters.</p> : null}

      <div className="page-head" style={{ marginTop: '2rem' }}>
        <h2>Latest insights</h2>
        <p>
          <Link to="/admin/insights">Generate or refresh copy</Link>
        </p>
      </div>
      <ApiErrorBanner error={insights.error} />
      <ul className="plain-list">
        {insights.data?.slice(0, 3).map((insight) => (
          <li key={insight.id} className="insight">
            <p className="eyebrow">
              Event {insight.eventId} · {insight.generatedBy}
            </p>
            <p>{insight.content}</p>
          </li>
        ))}
      </ul>
    </section>
  )
}

function Kpi({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <article className="kpi">
      <p className="eyebrow">{label}</p>
      <p className="kpi-value">{value}</p>
      <p className="muted">{hint}</p>
    </article>
  )
}

function ChartPanel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <article className="chart-panel">
      <h2>{title}</h2>
      {children}
    </article>
  )
}
