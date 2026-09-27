package com.ahdyahmed.eventhub.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ahdyahmed.eventhub.booking.event.BookingConfirmedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockPaymentServiceImplTest {

    private final MockPaymentServiceImpl paymentService = new MockPaymentServiceImpl(new BigDecimal("1000.00"));

    private BookingConfirmedEvent bookingWithTotal(BigDecimal totalAmount) {
        return new BookingConfirmedEvent(1L, 2L, "test@example.com", 3L, List.of(100L), totalAmount, Instant.now());
    }

    @Test
    void charge_amountUnderThreshold_succeeds() {
        PaymentResult result = paymentService.charge(bookingWithTotal(new BigDecimal("50.00")));

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(result.reason()).isNull();
    }

    @Test
    void charge_amountExactlyAtThreshold_succeeds() {
        // compareTo > 0, not >= 0, is the decline condition - exactly at the
        // threshold is still an authorized amount.
        PaymentResult result = paymentService.charge(bookingWithTotal(new BigDecimal("1000.00")));

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void charge_amountOverThreshold_isDeclinedWithReason() {
        PaymentResult result = paymentService.charge(bookingWithTotal(new BigDecimal("1000.01")));

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void charge_sameAmountTwice_isDeterministic() {
        BookingConfirmedEvent event = bookingWithTotal(new BigDecimal("5000.00"));

        PaymentResult first = paymentService.charge(event);
        PaymentResult second = paymentService.charge(event);

        assertThat(first.status()).isEqualTo(second.status()).isEqualTo(PaymentStatus.FAILED);
    }
}
