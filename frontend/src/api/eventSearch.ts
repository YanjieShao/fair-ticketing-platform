export type EventSearchFilters = {
  city: string
  artist: string
  category: string
  from: string
  to: string
  minEuros: string
  maxEuros: string
  page: number
}

export function eventSearchQuery(filters: EventSearchFilters): string {
  const params = new URLSearchParams()
  if (filters.city) params.set('city', filters.city)
  if (filters.artist) params.set('artist', filters.artist)
  if (filters.category) params.set('category', filters.category)
  if (filters.from) params.set('from', startOfLocalDay(filters.from))
  if (filters.to) params.set('to', endOfLocalDay(filters.to))
  const minCents = eurosToCents(filters.minEuros)
  const maxCents = eurosToCents(filters.maxEuros)
  if (minCents != null) params.set('minPriceCents', String(minCents))
  if (maxCents != null) params.set('maxPriceCents', String(maxCents))
  if (filters.page > 0) params.set('page', String(filters.page))
  return params.toString()
}

function eurosToCents(value: string): number | null {
  if (!value.trim()) {
    return null
  }
  const euros = Number(value)
  if (!Number.isFinite(euros) || euros < 0) {
    return null
  }
  return Math.round(euros * 100)
}

function startOfLocalDay(date: string): string {
  return new Date(`${date}T00:00:00`).toISOString()
}

function endOfLocalDay(date: string): string {
  return new Date(`${date}T23:59:59.999`).toISOString()
}
