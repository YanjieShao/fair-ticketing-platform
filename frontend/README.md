# Frontend

Buyer and admin UI for Fair Ticketing. React 19, Vite, TypeScript,
react-router 6, TanStack Query, Recharts.

Needs the API on :8080. From the repository root: `docker compose up -d`,
then `cd backend && ./mvnw spring-boot:run`.

```bash
npm install
npm run dev     # http://localhost:5173, proxies /api to :8080
npm run lint    # oxlint
npm test        # Vitest + Testing Library
npm test -- --coverage   # src/api and src/auth; 95% lines
npm run build
```

The coverage gate is the request layer and auth, not every page component.
Playwright is a separate command and is not in CI.

To serve the production bundle behind nginx (port 80, `/api/` proxied to
the backend container), build the image from this directory or use the
Compose overlay in the repository root: [docs/deploy.md](../docs/deploy.md).

Playwright covers the buyer's main path (search, register, purchase,
confirm). It expects MySQL, Redis, and the API on :8080, then starts Vite
if needed:

```bash
npx playwright install chromium   # once
npm run e2e
```

Demo operator: `admin@fairticketing.local` / `password123`. Create a buyer
from the UI. Wording on the order pages is Purchase / Confirm / Cancel /
Return tickets (mock refund, no card movement).
