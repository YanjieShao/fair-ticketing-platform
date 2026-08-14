import { beforeEach, describe, expect, it } from 'vitest'
import { checkoutKey, clearCheckoutKey } from './idempotency'

describe('checkout idempotency key', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('reuses the same key until the hold is confirmed', () => {
    const first = checkoutKey(9, 2)
    const second = checkoutKey(9, 2)
    expect(second).toBe(first)
    expect(checkoutKey(9, 1)).not.toBe(first)
  })

  it('issues a new key after a successful checkout', () => {
    const first = checkoutKey(9, 2)
    clearCheckoutKey(9, 2)
    expect(checkoutKey(9, 2)).not.toBe(first)
  })
})
