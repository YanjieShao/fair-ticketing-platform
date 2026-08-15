/**
 * Pulls JSON objects out of an SSE buffer. Only `data:` lines are kept; comments
 * and event names are ignored. Incomplete frames stay in the remainder.
 */
export function consumeSseJson<T>(buffer: string): { frames: T[]; rest: string } {
  const frames: T[] = []
  let rest = buffer
  let split = rest.indexOf('\n\n')
  while (split >= 0) {
    const block = rest.slice(0, split)
    rest = rest.slice(split + 2)
    const data = block
      .split('\n')
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trim())
      .join('\n')
    if (data) {
      frames.push(JSON.parse(data) as T)
    }
    split = rest.indexOf('\n\n')
  }
  return { frames, rest }
}
