import { describe, expect, it } from 'vitest'
import { isAdminToken, rolesFromAccessToken } from './roles'

function tokenWithRoles(roles: string[]): string {
  const payload = btoa(JSON.stringify({ roles })).replace(/\+/g, '-').replace(/\//g, '_')
  return `header.${payload}.sig`
}

describe('rolesFromAccessToken', () => {
  it('reads ADMIN from the JWT roles claim', () => {
    expect(rolesFromAccessToken(tokenWithRoles(['ADMIN']))).toEqual(['ADMIN'])
    expect(isAdminToken(tokenWithRoles(['ADMIN']))).toBe(true)
    expect(isAdminToken(tokenWithRoles(['USER']))).toBe(false)
    expect(isAdminToken(null)).toBe(false)
  })
})
