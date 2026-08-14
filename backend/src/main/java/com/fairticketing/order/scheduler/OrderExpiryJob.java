package com.fairticketing.order.scheduler;

import com.fairticketing.order.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sweeps up orders whose payment window has closed. Runs often enough that
 * returned stock reaches the next buyer while they are still watching the page.
 */
@Component
public class OrderExpiryJob {

    private final OrderService orders;

    public OrderExpiryJob(OrderService orders) {
        this.orders = orders;
    }

    @Scheduled(fixedDelayString = "${ticketing.order.expiry-scan-interval:PT30S}")
    public void releaseExpiredOrders() {
        orders.expireOverdueOrders();
    }
}
