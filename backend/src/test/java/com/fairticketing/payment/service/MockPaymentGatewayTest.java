package com.fairticketing.payment.service;

import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.payment.domain.Payment;
import com.fairticketing.support.Fixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    @Test
    void zero_failure_rate_always_succeeds() {
        MockPaymentGateway gateway = new MockPaymentGateway(Fixtures.properties(InventoryStrategy.DB_PESSIMISTIC_LOCK, 0.0));
        PaymentGateway.Charge charge = gateway.charge("FT1", 5000);
        assertThat(charge.status()).isEqualTo(Payment.Status.SUCCEEDED);
        assertThat(charge.providerRef()).startsWith("mock_");
    }

    @Test
    void unit_failure_rate_always_declines() {
        MockPaymentGateway gateway = new MockPaymentGateway(Fixtures.properties(InventoryStrategy.DB_PESSIMISTIC_LOCK, 1.0));
        assertThat(gateway.charge("FT1", 5000).status()).isEqualTo(Payment.Status.FAILED);
    }

    @Test
    void refund_is_always_a_mock_success() {
        MockPaymentGateway gateway = new MockPaymentGateway(Fixtures.properties());
        PaymentGateway.Charge refund = gateway.refund("FT1", 5000);
        assertThat(refund.status()).isEqualTo(Payment.Status.REFUNDED);
        assertThat(refund.providerRef()).startsWith("mock_rf_");
    }
}
