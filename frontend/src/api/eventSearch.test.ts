import { describe, expect, it } from 'vitest'
import { eventSearchQuery } from './eventSearch'

describe('eventSearchQuery', () => {
  it('omits blank filters', () => {
    expect(
      eventSearchQuery({
        city: '',
        artist: '',
        category: '',
        from: '',
        to: '',
        minEuros: '',
        maxEuros: '',
        page: 0,
      }),
    ).toBe('')
  })

  it('sends date bounds and euro prices as the API expects them', () => {
    const query = eventSearchQuery({
      city: 'Dublin',
      artist: 'Harbour',
      category: 'Concert',
      from: '2026-10-01',
      to: '2026-10-31',
      minEuros: '25',
      maxEuros: '90.5',
      page: 2,
    })
    const params = new URLSearchParams(query)

    expect(params.get('city')).toBe('Dublin')
    expect(params.get('artist')).toBe('Harbour')
    expect(params.get('category')).toBe('Concert')
    expect(params.get('minPriceCents')).toBe('2500')
    expect(params.get('maxPriceCents')).toBe('9050')
    expect(params.get('page')).toBe('2')
    expect(params.get('from')).toBe(new Date('2026-10-01T00:00:00').toISOString())
    expect(params.get('to')).toBe(new Date('2026-10-31T23:59:59.999').toISOString())
  })
})
