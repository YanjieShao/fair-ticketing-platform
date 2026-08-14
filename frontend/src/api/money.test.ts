import { describe, expect, it } from 'vitest'
import { formatCents, formatWait } from './money'

describe('money', () => {
  it('renders euro amounts from integer cents', () => {
    expect(formatCents(5000).replace(/\s/g, '')).toContain('50')
    expect(formatCents(5000)).toMatch(/€/)
  })

  it('does not promise a wait of zero seconds', () => {
    expect(formatWait(0)).toBe('any moment')
    expect(formatWait(90)).toBe('about 2 min')
  })
})
