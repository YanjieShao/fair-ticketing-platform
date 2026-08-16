import { expect, test, type APIRequestContext } from '@playwright/test'

const API = 'http://localhost:8080'

let showTitle = ''
let artistName = ''

test.beforeAll(async ({ request }) => {
  const listed = await listShow(request)
  showTitle = listed.title
  artistName = listed.artist
})

test('a visitor can search for a listed show', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Artist').fill(artistName)
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(page.getByRole('heading', { name: showTitle })).toBeVisible()
})

test('a new buyer can purchase tickets and confirm', async ({ page }) => {
  const email = `e2e-${Date.now()}@example.com`
  await page.goto('/register')
  await page.getByLabel('Name').fill('E2E Buyer')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('password123')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page).toHaveURL('/')

  await page.getByLabel('Artist').fill(artistName)
  await page.getByRole('button', { name: 'Search' }).click()
  await page.getByRole('link', { name: showTitle }).click()
  await page.getByRole('button', { name: 'Purchase' }).click()
  await expect(page.getByText('PENDING PAYMENT')).toBeVisible()
  await page.getByRole('button', { name: 'Confirm' }).click()
  await expect(page.getByText('COMPLETED')).toBeVisible()
})

async function listShow(request: APIRequestContext): Promise<{ title: string; artist: string }> {
  const stamp = Date.now()
  const title = `E2E Night ${stamp}`
  const artist = `E2E Act ${stamp}`
  const login = await request.post(`${API}/api/auth/login`, {
    data: { email: 'admin@fairticketing.local', password: 'password123' },
  })
  if (!login.ok()) {
    throw new Error(`admin login failed: ${login.status()} ${await login.text()}`)
  }
  const { accessToken } = (await login.json()) as { accessToken: string }
  const now = Date.now()
  const created = await request.post(`${API}/api/admin/events`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: {
      title,
      category: 'Concert',
      artistName: artist,
      genre: 'Pop',
      popularityScore: 40,
      venueName: 'E2E Hall',
      city: 'Dublin',
      country: 'Ireland',
      capacity: 500,
      timezone: 'Europe/Dublin',
      startsAt: new Date(now + 60 * 86_400_000).toISOString(),
      salesStartAt: new Date(now - 3_600_000).toISOString(),
      salesEndAt: new Date(now + 59 * 86_400_000).toISOString(),
      waitingRoomEnabled: false,
      tiers: [{ name: 'Standing', priceCents: 1500, totalQuantity: 50, maxPerUser: 4 }],
    },
  })
  if (created.status() !== 201) {
    throw new Error(`create event failed: ${created.status()} ${await created.text()}`)
  }
  return { title, artist }
}
