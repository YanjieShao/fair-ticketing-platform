import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { formatInstant } from '../api/money'
import type { AdminInsight } from '../api/types'
import { ApiErrorBanner } from '../components/ApiErrorBanner'

export function AdminInsightsPage() {
  const queryClient = useQueryClient()
  const insights = useQuery({
    queryKey: ['admin-insights'],
    queryFn: () => api<AdminInsight[]>('/api/admin/insights'),
  })
  const run = useMutation({
    mutationFn: () => api<{ written: number }>('/api/admin/insights/run', { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-insights'] }),
  })

  return (
    <section>
      <div className="page-head">
        <p className="eyebrow">Admin</p>
        <h1>Sales insights</h1>
        <p className="muted">
          Numbers are computed here. The LLM only phrases them, and a template
          is used when no key is set.
        </p>
        <p>
          <Link to="/admin">Back to dashboard</Link>
        </p>
        <button type="button" disabled={run.isPending} onClick={() => run.mutate()}>
          {run.isPending ? 'Writing…' : 'Generate now'}
        </button>
      </div>
      <ApiErrorBanner error={insights.error} />
      <ApiErrorBanner error={run.error} />
      {run.data ? <p className="muted">Wrote {run.data.written} briefings.</p> : null}
      {insights.isLoading ? <p>Loading insights…</p> : null}
      <ul className="plain-list">
        {insights.data?.map((insight) => (
          <li key={insight.id} className="insight">
            <p className="eyebrow">
              Event {insight.eventId} · {insight.generatedBy} · {formatInstant(insight.createdAt)}
            </p>
            <p>{insight.content}</p>
          </li>
        ))}
      </ul>
      {insights.data && insights.data.length === 0 ? (
        <p className="muted">No insights yet. Generate a pass after the sale is live.</p>
      ) : null}
    </section>
  )
}
