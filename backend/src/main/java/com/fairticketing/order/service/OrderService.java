package com.fairticketing.order.service;

import com.fairticketing.common.config.TicketingProperties;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.inventory.domain.InventoryLedgerEntry;
import com.fairticketing.inventory.repository.TicketTierRepository;
import com.fairticketing.inventory.repository.TierPurchaseView;
import com.fairticketing.inventory.service.InventoryService;
import com.fairticketing.order.domain.OrderStatus;
import com.fairticketing.order.domain.TicketOrder;
import com.fairticketing.order.repository.TicketOrderRepository;
import com.fairticketing.payment.domain.Payment;
import com.fairticketing.payment.repository.PaymentRepository;
import com.fairticketing.payment.service.PaymentGateway;
import com.fairticketing.waitingroom.service.WaitingRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final EnumSet<OrderStatus> HOLDING_UNPAID = EnumSet.of(OrderStatus.CREATED, OrderStatus.PENDING_PAYMENT);

    private final TicketOrderRepository orders;
    private final TicketTierRepository tiers;
    private final InventoryService inventory;
    private final PaymentGateway paymentGateway;
    private final PaymentRepository payments;
    private final OrderNumberGenerator orderNumbers;
    private final WaitingRoomService waitingRoom;
    private final TicketingProperties properties;
    private final Clock clock;

    public OrderService(TicketOrderRepository orders,
                        TicketTierRepository tiers,
                        InventoryService inventory,
                        PaymentGateway paymentGateway,
                        PaymentRepository payments,
                        OrderNumberGenerator orderNumbers,
                        WaitingRoomService waitingRoom,
                        TicketingProperties properties,
                        Clock clock) {
        this.orders = orders;
        this.tiers = tiers;
        this.inventory = inventory;
        this.paymentGateway = paymentGateway;
        this.payments = payments;
        this.orderNumbers = orderNumbers;
        this.waitingRoom = waitingRoom;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Holds stock and opens a payment window.
     *
     * <p>Step order matters more than it looks. Inserting the order row takes a
     * shared lock on the tier it references, and reserving stock needs an
     * exclusive one; doing it in that order means concurrent buyers each hold a
     * shared lock while waiting to upgrade, which is a deadlock. Reserving
     * first makes every transaction grab the contended tier row before it
     * touches anything else, so they queue instead of colliding.
     */
    @Transactional
    public TicketOrder checkout(Long userId, Long tierId, int quantity, String idempotencyKey) {
        // A retry of the same request must return the original order, not a second one.
        var replay = orders.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }

        TierPurchaseView tier = tiers.findPurchaseView(tierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Ticket tier " + tierId + " not found"));

        requireOnSale(tier);
        // Checked before the limit so a queue-jumper is told to queue rather
        // than being handed a reason that leaks how the tier is configured.
        waitingRoom.requireAdmission(tier.eventId(), userId);
        requireWithinPurchaseLimit(tier, quantity);

        if (!inventory.tryReserve(tierId, quantity)) {
            throw new BusinessException(ErrorCode.SOLD_OUT, "Not enough tickets left in this tier");
        }

        Instant now = Instant.now(clock);
        TicketOrder order = TicketOrder.create(
                orderNumbers.next(),
                userId,
                tier.eventId(),
                tierId,
                quantity,
                tier.priceCents(),
                idempotencyKey,
                now,
                now.plus(properties.order().paymentWindow()));

        try {
            orders.saveAndFlush(order);
        } catch (DataIntegrityViolationException ex) {
            // The unique index on active_lock_key is the real enforcement point
            // for "one live order per user per event"; rolling back returns the
            // stock we just took.
            throw new BusinessException(ErrorCode.DUPLICATE_ACTIVE_ORDER,
                    "You already have an order in progress for this event");
        }

        inventory.recordReservation(tierId, order.getId(), quantity);
        order.transitionTo(OrderStatus.PENDING_PAYMENT, now);
        return order;
    }

    @Transactional
    public TicketOrder pay(Long userId, String orderNo) {
        TicketOrder order = loadOwned(userId, orderNo);
        Instant now = Instant.now(clock);

        if (order.isExpiredAt(now)) {
            expire(order, now);
            throw new BusinessException(ErrorCode.OFFER_WINDOW_CLOSED, "The payment window for this order has closed");
        }

        PaymentGateway.Charge charge = paymentGateway.charge(order.getOrderNo(), order.getTotalCents());
        payments.save(new Payment(order.getId(), charge.providerRef(), charge.status(), order.getTotalCents(), now));

        if (!charge.succeeded()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Payment was declined");
        }

        order.transitionTo(OrderStatus.PAID, now);
        // Nothing physical is shipped, so issuing follows payment immediately.
        order.transitionTo(OrderStatus.COMPLETED, now);
        return order;
    }

    @Transactional
    public TicketOrder cancel(Long userId, String orderNo) {
        TicketOrder order = loadOwned(userId, orderNo);
        Instant now = Instant.now(clock);

        order.transitionTo(OrderStatus.CANCELLED, now);
        inventory.release(order.getTierId(), order.getQuantity(), order.getId(),
                InventoryLedgerEntry.Reason.RELEASE_CANCELLED);
        return order;
    }

    /**
     * Without this, a buyer who never pays holds their seats forever and the
     * event is unsellable long before it is sold.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int expireOverdueOrders() {
        Instant now = Instant.now(clock);
        List<TicketOrder> overdue = orders.findExpired(HOLDING_UNPAID, now);
        overdue.forEach(order -> expire(order, now));

        if (!overdue.isEmpty()) {
            log.info("Expired {} unpaid orders and returned their inventory", overdue.size());
        }
        return overdue.size();
    }

    @Transactional(readOnly = true)
    public TicketOrder findOwned(Long userId, String orderNo) {
        return loadOwned(userId, orderNo);
    }

    private void expire(TicketOrder order, Instant now) {
        order.transitionTo(OrderStatus.EXPIRED, now);
        inventory.release(order.getTierId(), order.getQuantity(), order.getId(),
                InventoryLedgerEntry.Reason.RELEASE_EXPIRED);
    }

    private TicketOrder loadOwned(Long userId, String orderNo) {
        TicketOrder order = orders.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Order " + orderNo + " not found"));

        if (!order.getUserId().equals(userId)) {
            // Deliberately indistinguishable from a missing order.
            throw new BusinessException(ErrorCode.NOT_FOUND, "Order " + orderNo + " not found");
        }
        return order;
    }

    private void requireOnSale(TierPurchaseView tier) {
        if (!tier.eventStatus().acceptsPurchases()) {
            throw new BusinessException(ErrorCode.EVENT_NOT_ON_SALE, "This event is not on sale");
        }
        Instant now = Instant.now(clock);
        if (now.isBefore(tier.salesStartAt()) || now.isAfter(tier.salesEndAt())) {
            throw new BusinessException(ErrorCode.EVENT_NOT_ON_SALE, "This event is outside its sales window");
        }
    }

    private void requireWithinPurchaseLimit(TierPurchaseView tier, int quantity) {
        if (quantity < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Quantity must be at least 1");
        }
        int limit = Math.min(tier.maxPerUser(), properties.order().maxTicketsPerUserPerTier());
        if (quantity > limit) {
            throw new BusinessException(ErrorCode.PURCHASE_LIMIT_EXCEEDED,
                    "You can buy at most " + limit + " tickets in this tier");
        }
    }
}
