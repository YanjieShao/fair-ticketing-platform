function storageKey(tierId: number, quantity: number): string {
  return `ft.idempotency:${tierId}:${quantity}`
}

/**
 * A checkout retry after a timeout must send the same key, otherwise the
 * server treats it as a second order. The key lives in sessionStorage until
 * the hold is confirmed so a refresh does not buy twice.
 */
export function checkoutKey(tierId: number, quantity: number): string {
  const existing = sessionStorage.getItem(storageKey(tierId, quantity))
  if (existing) {
    return existing
  }
  const key = crypto.randomUUID()
  sessionStorage.setItem(storageKey(tierId, quantity), key)
  return key
}

export function clearCheckoutKey(tierId: number, quantity: number): void {
  sessionStorage.removeItem(storageKey(tierId, quantity))
}
