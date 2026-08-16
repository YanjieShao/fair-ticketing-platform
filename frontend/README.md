# Frontend

Buyer and admin UI for Fair Ticketing. React 19, Vite, TypeScript,
react-router 6, TanStack Query, Recharts.

Needs the API on :8080. From the repository root: `docker compose up -d`,
then `cd backend && ./mvnw spring-boot:run`.

```bash
npm install
npm run dev     # http://localhost:5173, proxies /api to :8080
npm test        # Vitest + Testing Library
npm run build
```

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
