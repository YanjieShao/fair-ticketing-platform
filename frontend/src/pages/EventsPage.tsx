import { useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { formatCents, formatInstant } from '../api/money'
import type { EventSummary, SpringPage } from '../api/types'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { StatusChip } from '../components/StatusChip'

export function EventsPage() {
  const [city, setCity] = useState('')
  const [artist, setArtist] = useState('')
  const [category, setCategory] = useState('')
  const [applied, setApplied] = useState({ city: '', artist: '', category: '' })

  const events = useQuery({
    queryKey: ['events', applied],
    queryFn: () => {
      const params = new URLSearchParams()
      if (applied.city) params.set('city', applied.city)
      if (applied.artist) params.set('artist', applied.artist)
      if (applied.category) params.set('category', applied.category)
      const query = params.toString()
      return api<SpringPage<EventSummary>>(`/api/events${query ? `?${query}` : ''}`)
    },
  })

  function onSearch(event: FormEvent) {
    event.preventDefault()
    setApplied({ city: city.trim(), artist: artist.trim(), category: category.trim() })
  }

  return (
    <section>
      <header className="page-head">
        <p className="eyebrow">On sale</p>
        <h1>Find a show</h1>
      </header>

      <form className="filters" onSubmit={onSearch}>
        <label>
          City
          <input value={city} onChange={(e) => setCity(e.target.value)} placeholder="Dublin" />
        </label>
        <label>
          Artist
          <input value={artist} onChange={(e) => setArtist(e.target.value)} placeholder="Name" />
        </label>
        <label>
          Category
          <input value={category} onChange={(e) => setCategory(e.target.value)} placeholder="Concert" />
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
                <p className="muted">{formatInstant(show.startsAt)}</p>
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
    </section>
  )
}
