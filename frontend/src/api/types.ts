export type EventStatus = 'DRAFT' | 'ON_SALE' | 'SOLD_OUT' | 'CLOSED' | 'CANCELLED'
export type OrderStatus =
  | 'CREATED'
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'COMPLETED'
  | 'EXPIRED'
  | 'CANCELLED'
export type WaitlistStatus = 'WAITING' | 'OFFERED' | 'CONVERTED' | 'OFFER_EXPIRED' | 'CANCELLED'
export type WaitingRoomStatus = 'NOT_QUEUED' | 'WAITING' | 'ADMITTED'

export type SpringPage<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type EventSummary = {
  id: number
  title: string
  artistName: string
  genre: string
  venueName: string
  city: string
  country: string
  timezone: string
  category: string
  status: EventStatus
  startsAt: string
  salesStartAt: string
  waitingRoomEnabled: boolean
  ticketsAvailable: number
  lowestPriceCents: number
}

export type TicketTier = {
  id: number
  name: string
  priceCents: number
  currency: string
  totalQuantity: number
  availableQuantity: number
  maxPerUser: number
  soldOut: boolean
}

export type EventDetail = {
  id: number
  title: string
  artistName: string
  genre: string
  venueName: string
  city: string
  country: string
  timezone: string
  category: string
  status: EventStatus
  startsAt: string
  salesStartAt: string
  salesEndAt: string
  waitingRoomEnabled: boolean
  forecast: DemandForecast | null
  insight: SalesInsight | null
  tiers: TicketTier[]
}

export type SalesInsight = {
  content: string
  generatedBy: 'LLM' | 'TEMPLATE' | string
  createdAt: string
}

export type AdminInsight = {
  id: number
  eventId: number
  content: string
  generatedBy: string
  createdAt: string
  payloadJson: string | null
}

export type DemandForecast = {
  expectedDemand: number
  capacity: number
  demandRatio: number
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  modelVersion: string
}

export type TokenResponse = {
  accessToken: string
  expiresAt: string
}

export type Order = {
  orderNo: string
  eventId: number
  tierId: number
  quantity: number
  unitPriceCents: number
  totalCents: number
  status: OrderStatus
  createdAt: string
  expiresAt: string | null
  paidAt: string | null
  completedAt: string | null
  eventTitle: string | null
  artistName: string | null
  tierName: string | null
  venueName: string | null
  city: string | null
  startsAt: string | null
  venueTimezone: string
}

export type WaitlistEntry = {
  id: number
  eventId: number
  tierId: number
  status: WaitlistStatus
  requestedQuantity: number
  positionSeq: number
  peopleAhead: number
  createdAt: string
  offeredAt: string | null
  offerExpiresAt: string | null
  convertedOrderId: number | null
  eventTitle: string | null
  artistName: string | null
  tierName: string | null
  venueTimezone: string
}

export type WaitingRoom = {
  eventId: number
  status: WaitingRoomStatus
  position: number
  queueLength: number
  estimatedWaitSeconds: number
  admissionExpiresAt: string | null
}

export type Recommendation = {
  id: number
  title: string
  artistName: string
  genre: string
  city: string
  status: EventStatus
  ticketsAvailable: number
  lowestPriceCents: number
  score: number
  reasons: string[]
}

export type AdminDashboard = {
  kpis: {
    eventsOnSale: number
    eventsSoldOut: number
    capacity: number
    reserved: number
    remaining: number
    sellThroughPercent: number
    waitlistPeople: number
    waitlistTickets: number
    paidOrders: number
    paidTickets: number
    paidCents: number
  }
  orderStatus: NamedCount[]
  forecastRisk: NamedCount[]
  categories: { category: string; reserved: number; heldCents: number }[]
  salesTrend: { day: string; orders: number; tickets: number; cents: number }[]
  hotEvents: {
    eventId: number
    title: string
    artistName: string
    status: EventStatus
    soldPercent: number
    reserved: number
    remaining: number
    waitlistPeople: number
    waitlistTickets: number
    waitlistVsRemainingPercent: number | null
    demandRisk: string | null
  }[]
}

export type NotificationItem = {
  id: number
  type: string
  title: string
  body: string
  sourceType: string
  generatedBy: string
  createdAt: string
}

export type NamedCount = {
  name: string
  count: number
  tickets: number
  cents: number
}
