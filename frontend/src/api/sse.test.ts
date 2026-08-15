import { describe, expect, it } from 'vitest'
import { consumeSseJson } from './sse'

describe('consumeSseJson', () => {
  it('reads complete frames and keeps a partial one', () => {
    const { frames, rest } = consumeSseJson<{ status: string }>(
      'event: queue\ndata: {"status":"WAITING"}\n\ndata: {"status":"ADMITTED"}\n\ndata: {"status":',
    )
    expect(frames).toEqual([{ status: 'WAITING' }, { status: 'ADMITTED' }])
    expect(rest).toBe('data: {"status":')
  })
})
