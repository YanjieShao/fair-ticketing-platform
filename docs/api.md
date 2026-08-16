# API

Auth is a Bearer JWT from `POST /api/auth/login` or `POST /api/auth/register`.
Checkout also requires an `Idempotency-Key` header.

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/auth/register`, `/api/auth/login` | none | obtain a token |
| GET | `/api/events` | none | search by city, artist, category, date, price |
| GET | `/api/events/{id}` | none | tiers, remaining stock, latest demand forecast and sales insight |
| GET | `/api/events/{id}/recommendations` | none | same-genre shows still on sale |
| GET | `/api/notifications` | buyer | waitlist offers and sales briefs for this account |
| GET | `/api/admin/dashboard` | admin | live sales, waitlist, order, and forecast totals |
| POST | `/api/admin/events` | admin | create a show (draft or on sale) |
| POST | `/api/admin/events/{id}/publish` | admin | DRAFT → ON_SALE |
| POST | `/api/admin/events/{id}/cancel` | admin | take down a draft only |
| GET | `/api/admin/insights` | admin | latest sales briefings |
| POST | `/api/admin/insights/run` | admin | phrase live sales numbers (LLM or template) |
| POST | `/api/admin/forecasts/run` | admin | score upcoming shows; HIGH turns the waiting room on |
| POST | `/api/waiting-room/{eventId}/join` | buyer | take a place in line |
| GET | `/api/waiting-room/{eventId}` | buyer | position; still what moves the line if nobody is streaming |
| GET | `/api/waiting-room/{eventId}/stream` | buyer | SSE of the same payload until admitted |
| DELETE | `/api/waiting-room/{eventId}` | buyer | give up a place |
| POST | `/api/waitlist` | buyer | join after a tier sells out |
| GET | `/api/waitlist`, `/api/waitlist/{id}` | buyer | place in line and offer window |
| DELETE | `/api/waitlist/{id}` | buyer | leave the queue |
| POST | `/api/orders` | buyer | reserve a tier, requires `Idempotency-Key` |
| POST | `/api/orders/{orderNo}/pay` | buyer | confirm through the mock provider |
| POST | `/api/orders/{orderNo}/cancel` | buyer | release a hold or return paid tickets |
| GET | `/api/orders`, `/api/orders/{orderNo}` | buyer | history, including show, artist, venue, and tier |

`POST /api/admin/load-test/fixtures` and `GET /api/admin/load-test/result/{tierId}`
exist only when `FT_LOADTEST_ENABLED=true`. They are not part of the product
API. See [load-test.md](load-test.md).

## Errors

Every failure, including the ones rejected by security filters before any
controller runs, comes back in one shape:

```json
{ "code": "SOLD_OUT", "message": "Not enough tickets left in this tier", "timestamp": "..." }
```

`code` is the stable part and the one clients should branch on. Losing a race
for a ticket is an ordinary outcome rather than a fault, so those answer 409
with a code that says which rule stopped the buyer.

| Code | HTTP | When |
| --- | --- | --- |
| `SOLD_OUT` | 409 | not enough remaining in the tier |
| `PURCHASE_LIMIT_EXCEEDED` | 409 | this buyer already holds 4 tickets on that tier (held + this request) |
| `EVENT_NOT_ON_SALE` | 409 | the show is not in its sales window |
| `WAITING_ROOM_TOKEN_REQUIRED` | 403 | checkout skipped the queue |
| `RATE_LIMITED` | 429 | too many checkouts or joins from this account |
| `DUPLICATE_ACTIVE_ORDER` | 409 | rare idempotency fallback, not a one-order-per-event rule |
| `OFFER_WINDOW_CLOSED` | 409 | unpaid hold or waitlist offer expired |
| `WAITLIST_NOT_NEEDED` | 409 | seats remain; join the sale instead |
| `ALREADY_ON_WAITLIST` | 409 | this buyer is already queued on that tier |
| `EMAIL_ALREADY_REGISTERED` | 409 | register with an address that exists |
| `INVALID_CREDENTIALS` | 401 | bad password |
| `UNAUTHORIZED` | 401 | missing or forged token |
| `FORBIDDEN` | 403 | authenticated, but the wrong role |
| `NOT_FOUND` | 404 | unknown event, order, or waitlist entry |
| `VALIDATION_FAILED` | 400 | malformed body or quantity below 1 |
| `ILLEGAL_STATE_TRANSITION` | 409 | e.g. cancelling a show that is already on sale |
| `INTERNAL_ERROR` | 500 | the server broke; nothing a client can send should produce one |

A second order on the same event is allowed. The cap is the sum of tickets
already occupying inventory on that tier, not one order per event.
