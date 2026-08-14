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
  tiers: TicketTier[]
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
}

export type WaitingRoom = {
  eventId: number
  status: WaitingRoomStatus
  position: number
  queueLength: number
  estimatedWaitSeconds: number
  admissionExpiresAt: string | null
}
