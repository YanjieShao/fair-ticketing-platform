import { request as newRequest } from '@playwright/test'

/**
 * The UI proxy is useless if the API is down. Fail early with a sentence a
 * human can act on, instead of a timeout on the first click.
 */
export default async function globalSetup(): Promise<void> {
  const api = await newRequest.newContext()
  try {
    const health = await api.get('http://localhost:8080/actuator/health')
    if (!health.ok()) {
      throw new Error(
        'Playwright needs the API on http://localhost:8080 (docker compose up -d, then backend ./mvnw spring-boot:run).',
      )
    }
  } finally {
    await api.dispose()
  }
}
