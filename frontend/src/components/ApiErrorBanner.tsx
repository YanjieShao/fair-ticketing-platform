import { ApiError } from '../api/client'

export function ApiErrorBanner({ error }: { error: unknown }) {
  if (!error) {
    return null
  }
  const message = error instanceof ApiError
    ? `${error.code}: ${error.message}`
    : error instanceof Error
      ? error.message
      : 'Something went wrong'
  return (
    <p role="alert" className="banner">
      {message}
    </p>
  )
}
