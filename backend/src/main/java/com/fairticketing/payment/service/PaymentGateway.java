package com.fairticketing.payment.service;

import com.fairticketing.payment.domain.Payment;

/**
 * Behind an interface so tests can decide the outcome instead of depending on
 * whatever the mock feels like doing.
 */
public interface PaymentGateway {

    Charge charge(String orderNo, int amountCents);

    Charge refund(String orderNo, int amountCents);

    record Charge(String providerRef, Payment.Status status) {

        public boolean succeeded() {
            return status == Payment.Status.SUCCEEDED;
        }
    }
}
