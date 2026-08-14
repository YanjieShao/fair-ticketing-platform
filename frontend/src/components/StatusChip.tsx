export function StatusChip({ children }: { children: string }) {
  const tone = children.toLowerCase().replaceAll('_', '-')
  return <span className={`chip chip-${tone}`}>{children.replaceAll('_', ' ')}</span>
}
