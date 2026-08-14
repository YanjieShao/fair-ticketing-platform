export function formatCents(cents: number, currency = 'EUR'): string {
  return new Intl.NumberFormat('en-IE', { style: 'currency', currency }).format(cents / 100)
}

export function formatInstant(iso: string, timeZone = 'UTC'): string {
  return new Intl.DateTimeFormat('en-IE', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone,
  }).format(new Date(iso))
}

export function formatWait(seconds: number): string {
  if (seconds <= 0) {
    return 'any moment'
  }
  if (seconds < 60) {
    return `${seconds}s`
  }
  const minutes = Math.ceil(seconds / 60)
  return `about ${minutes} min`
}
