export function rolesFromAccessToken(token: string | null): string[] {
  if (!token) {
    return []
  }
  const parts = token.split('.')
  if (parts.length < 2) {
    return []
  }
  try {
    const padded = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const json = atob(padded.padEnd(padded.length + ((4 - (padded.length % 4)) % 4), '='))
    const payload = JSON.parse(json) as { roles?: unknown }
    return Array.isArray(payload.roles)
      ? payload.roles.filter((role): role is string => typeof role === 'string')
      : []
  } catch {
    return []
  }
}

export function isAdminToken(token: string | null): boolean {
  return rolesFromAccessToken(token).includes('ADMIN')
}
