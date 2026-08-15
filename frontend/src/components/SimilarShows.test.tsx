import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SimilarShows } from './SimilarShows'

describe('SimilarShows', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists same-genre shows that are still on sale', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        expect(String(input)).toBe('/api/events/1/recommendations')
        return {
          status: 200,
          ok: true,
          text: async () =>
            JSON.stringify([
              {
                id: 2,
                title: 'Mercury',
                artistName: 'Imagine Dragons',
                genre: 'Pop',
                city: 'Dublin',
                status: 'ON_SALE',
                ticketsAvailable: 400,
                lowestPriceCents: 7500,
                score: 95,
                reasons: ['Same genre (Pop)', 'Same city (Dublin)'],
              },
            ]),
        }
      }),
    )

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <SimilarShows eventId={1} />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('Imagine Dragons')).toBeInTheDocument()
    expect(screen.getByText('Mercury')).toBeInTheDocument()
    expect(screen.getByText(/Same genre \(Pop\)/)).toBeInTheDocument()
  })
})
