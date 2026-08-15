import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { EventDetail } from '../api/types'
import { ApiErrorBanner } from '../components/ApiErrorBanner'

type TierDraft = { name: string; priceEuros: string; totalQuantity: string; maxPerUser: string }

const emptyTier = (): TierDraft => ({ name: 'Standing', priceEuros: '25', totalQuantity: '100', maxPerUser: '4' })

function defaultTimes() {
  const start = new Date(Date.now() + 60 * 24 * 3600 * 1000)
  const salesStart = new Date(Date.now() - 3600 * 1000)
  const salesEnd = new Date(start.getTime() - 2 * 3600 * 1000)
  return {
    startsAt: toLocalInput(start),
    salesStartAt: toLocalInput(salesStart),
    salesEndAt: toLocalInput(salesEnd),
  }
}

function toLocalInput(value: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}`
}

export function AdminCreateEventPage() {
  const navigate = useNavigate()
  const times = defaultTimes()
  const [title, setTitle] = useState('Live in Dublin')
  const [category, setCategory] = useState('Concert')
  const [artistName, setArtistName] = useState('')
  const [genre, setGenre] = useState('Pop')
  const [venueName, setVenueName] = useState('3Arena')
  const [city, setCity] = useState('Dublin')
  const [country, setCountry] = useState('Ireland')
  const [capacity, setCapacity] = useState('13000')
  const [timezone, setTimezone] = useState('Europe/Dublin')
  const [startsAt, setStartsAt] = useState(times.startsAt)
  const [salesStartAt, setSalesStartAt] = useState(times.salesStartAt)
  const [salesEndAt, setSalesEndAt] = useState(times.salesEndAt)
  const [waitingRoom, setWaitingRoom] = useState(false)
  const [tiers, setTiers] = useState<TierDraft[]>([emptyTier()])

  const create = useMutation({
    mutationFn: () =>
      api<EventDetail>('/api/admin/events', {
        method: 'POST',
        body: JSON.stringify({
          title,
          category,
          artistName,
          genre,
          popularityScore: 50,
          venueName,
          city,
          country,
          capacity: Number(capacity),
          timezone,
          startsAt: new Date(startsAt).toISOString(),
          salesStartAt: new Date(salesStartAt).toISOString(),
          salesEndAt: new Date(salesEndAt).toISOString(),
          waitingRoomEnabled: waitingRoom,
          tiers: tiers.map((tier) => ({
            name: tier.name,
            priceCents: Math.round(Number(tier.priceEuros) * 100),
            totalQuantity: Number(tier.totalQuantity),
            maxPerUser: Number(tier.maxPerUser),
          })),
        }),
      }),
    onSuccess: (created) => navigate(`/events/${created.id}`),
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  return (
    <section className="narrow">
      <p className="eyebrow">Admin</p>
      <h1>List a show</h1>
      <p className="muted">
        If sales have already opened, the show goes on sale immediately. A draft
        can still be cancelled; an on-sale show cannot, because that would mean
        bulk refunds.
      </p>
      <ApiErrorBanner error={create.error} />
      <form className="stack" onSubmit={onSubmit}>
        <label>
          Title
          <input value={title} onChange={(e) => setTitle(e.target.value)} required />
        </label>
        <label>
          Category
          <input value={category} onChange={(e) => setCategory(e.target.value)} required />
        </label>
        <label>
          Artist
          <input value={artistName} onChange={(e) => setArtistName(e.target.value)} required />
        </label>
        <label>
          Genre
          <input value={genre} onChange={(e) => setGenre(e.target.value)} required />
        </label>
        <label>
          Venue
          <input value={venueName} onChange={(e) => setVenueName(e.target.value)} required />
        </label>
        <label>
          City
          <input value={city} onChange={(e) => setCity(e.target.value)} required />
        </label>
        <label>
          Country
          <input value={country} onChange={(e) => setCountry(e.target.value)} required />
        </label>
        <label>
          Capacity
          <input type="number" min={1} value={capacity} onChange={(e) => setCapacity(e.target.value)} required />
        </label>
        <label>
          Timezone
          <input value={timezone} onChange={(e) => setTimezone(e.target.value)} required />
        </label>
        <label>
          Show starts
          <input type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} required />
        </label>
        <label>
          Sales start
          <input type="datetime-local" value={salesStartAt} onChange={(e) => setSalesStartAt(e.target.value)} required />
        </label>
        <label>
          Sales end
          <input type="datetime-local" value={salesEndAt} onChange={(e) => setSalesEndAt(e.target.value)} required />
        </label>
        <label className="inline">
          <input type="checkbox" checked={waitingRoom} onChange={(e) => setWaitingRoom(e.target.checked)} />
          Waiting room
        </label>
        {tiers.map((tier, index) => (
          <fieldset key={index} className="stack">
            <legend>Tier {index + 1}</legend>
            <label>
              Name
              <input
                value={tier.name}
                onChange={(e) =>
                  setTiers((current) => current.map((row, i) => (i === index ? { ...row, name: e.target.value } : row)))
                }
                required
              />
            </label>
            <label>
              Price (€)
              <input
                type="number"
                min={0.01}
                step="0.01"
                value={tier.priceEuros}
                onChange={(e) =>
                  setTiers((current) =>
                    current.map((row, i) => (i === index ? { ...row, priceEuros: e.target.value } : row)),
                  )
                }
                required
              />
            </label>
            <label>
              Inventory
              <input
                type="number"
                min={1}
                value={tier.totalQuantity}
                onChange={(e) =>
                  setTiers((current) =>
                    current.map((row, i) => (i === index ? { ...row, totalQuantity: e.target.value } : row)),
                  )
                }
                required
              />
            </label>
            <label>
              Max per buyer
              <input
                type="number"
                min={1}
                max={4}
                value={tier.maxPerUser}
                onChange={(e) =>
                  setTiers((current) =>
                    current.map((row, i) => (i === index ? { ...row, maxPerUser: e.target.value } : row)),
                  )
                }
                required
              />
            </label>
          </fieldset>
        ))}
        <div className="actions">
          {tiers.length < 5 ? (
            <button type="button" className="ghost" onClick={() => setTiers((current) => [...current, emptyTier()])}>
              Add a tier
            </button>
          ) : null}
          <button type="submit" disabled={create.isPending}>
            Create show
          </button>
        </div>
      </form>
      <p>
        <Link to="/admin">Back to dashboard</Link>
      </p>
    </section>
  )
}
