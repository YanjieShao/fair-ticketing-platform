import { useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { eventSearchQuery } from '../api/eventSearch'
import { formatCents, formatInstant } from '../api/money'
import type { EventSummary, SpringPage } from '../api/types'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { StatusChip } from '../components/StatusChip'

const emptyFilters = {
  city: '',
  artist: '',
  category: '',
  from: '',
  to: '',
  minEuros: '',
  maxEuros: '',
}

export function EventsPage() {
  const [draft, setDraft] = useState(emptyFilters)
  const [applied, setApplied] = useState({ ...emptyFilters, page: 0 })

  const events = useQuery({
    queryKey: ['events', applied],
    queryFn: () => {
      const query = eventSearchQuery(applied)
      return api<SpringPage<EventSummary>>(`/api/events${query ? `?${query}` : ''}`)
    },
  })

  function onSearch(event: FormEvent) {
    event.preventDefault()
    setApplied({
      city: draft.city.trim(),
      artist: draft.artist.trim(),
      category: draft.category.trim(),
      from: draft.from,
      to: draft.to,
      minEuros: draft.minEuros.trim(),
      maxEuros: draft.maxEuros.trim(),
      page: 0,
    })
  }

  const page = events.data
  const canGoBack = applied.page > 0
  const canGoForward = page != null && applied.page + 1 < page.totalPages

  return (
    <section>
      <header className="page-head">
        <p className="eyebrow">Box office</p>
        <h1>Find a show</h1>
      </header>

      <form className="filters" onSubmit={onSearch}>
        <label>
          City
          <input value={draft.city} onChange={(e) => setDraft({ ...draft, city: e.target.value })} placeholder="Dublin" />
        </label>
        <label>
          Artist
          <input
            value={draft.artist}
            onChange={(e) => setDraft({ ...draft, artist: e.target.value })}
            placeholder="Name"
          />
        </label>
        <label>
          Category
          <input
            value={draft.category}
            onChange={(e) => setDraft({ ...draft, category: e.target.value })}
            placeholder="Concert"
          />
        </label>
        <label>
          From
          <input type="date" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} />
        </label>
        <label>
          To
          <input type="date" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} />
        </label>
        <label>
          Min €
          <input
            type="number"
            min={0}
            step="0.01"
            value={draft.minEuros}
            onChange={(e) => setDraft({ ...draft, minEuros: e.target.value })}
            placeholder="20"
          />
        </label>
        <label>
          Max €
          <input
            type="number"
            min={0}
            step="0.01"
            value={draft.maxEuros}
            onChange={(e) => setDraft({ ...draft, maxEuros: e.target.value })}
            placeholder="90"
          />
        </label>
        <button type="submit">Search</button>
      </form>

      <ApiErrorBanner error={events.error} />

      {events.isLoading ? <p>Loading shows…</p> : null}

      <ul className="show-list">
        {events.data?.content.map((show) => (
          <li key={show.id}>
            <Link to={`/events/${show.id}`} className="show-card">
              <div>
                <p className="muted">{show.artistName}</p>
                <h2>{show.title}</h2>
                <p>
                  {show.venueName}, {show.city}
                </p>
                <p className="muted">{formatInstant(show.startsAt, show.timezone)}</p>
              </div>
              <div className="show-meta">
                <StatusChip>{show.status}</StatusChip>
                <p>{show.ticketsAvailable > 0 ? `from ${formatCents(show.lowestPriceCents)}` : 'Sold out'}</p>
              </div>
            </Link>
          </li>
        ))}
      </ul>

      {events.data && events.data.content.length === 0 ? (
        <p className="muted">No shows match those filters.</p>
      ) : null}

      {page && page.totalPages > 1 ? (
        <div className="pager">
          <button
            type="button"
            className="ghost"
            disabled={!canGoBack}
            onClick={() => setApplied((current) => ({ ...current, page: current.page - 1 }))}
          >
            Previous
          </button>
          <p className="muted">
            Page {applied.page + 1} of {page.totalPages}
          </p>
          <button
            type="button"
            className="ghost"
            disabled={!canGoForward}
            onClick={() => setApplied((current) => ({ ...current, page: current.page + 1 }))}
          >
            Next
          </button>
        </div>
      ) : null}
    </section>
  )
}
