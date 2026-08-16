import { describe, expect, it } from 'vitest'
import { formatCents, formatInstant, formatWait } from './money'

describe('money', () => {
  it('renders euro amounts from integer cents', () => {
    expect(formatCents(5000).replace(/\s/g, '')).toContain('50')
    expect(formatCents(5000)).toMatch(/€/)
  })

  it('shows instants in the venue timezone, not UTC by default for orders', () => {
    const iso = '2026-08-15T22:30:00Z'
    expect(formatInstant(iso, 'Europe/Dublin')).not.toBe(formatInstant(iso, 'UTC'))
    expect(formatInstant(iso, 'Europe/Dublin')).toMatch(/23:30/)
  })

  it('does not promise a wait of zero seconds', () => {
    expect(formatWait(0)).toBe('any moment')
    expect(formatWait(90)).toBe('about 2 min')
  })
})
