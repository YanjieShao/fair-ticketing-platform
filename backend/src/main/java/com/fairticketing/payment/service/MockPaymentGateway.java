package com.fairticketing.payment.service;

import com.fairticketing.common.config.TicketingProperties;
import com.fairticketing.payment.domain.Payment;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for a real provider. The failure rate is configurable so the
 * unhappy paths, which are the ones that release inventory, can be demonstrated
 * rather than described.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private final TicketingProperties properties;

    public MockPaymentGateway(TicketingProperties properties) {
        this.properties = properties;
    }

    @Override
    public Charge charge(String orderNo, int amountCents) {
        boolean failed = ThreadLocalRandom.current().nextDouble() < properties.payment().failureRate();
        return new Charge(
                "mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24),
                failed ? Payment.Status.FAILED : Payment.Status.SUCCEEDED);
    }

    @Override
    public Charge refund(String orderNo, int amountCents) {
        return new Charge(
                "mock_rf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 21),
                Payment.Status.REFUNDED);
    }
}
