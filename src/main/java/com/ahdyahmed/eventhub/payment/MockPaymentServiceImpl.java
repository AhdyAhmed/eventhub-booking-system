package com.ahdyahmed.eventhub.payment;

import com.ahdyahmed.eventhub.booking.event.BookingConfirmedEvent;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The entire "charges a card" story for this portfolio project: no gateway,
 * no network call, one comparison against a configured threshold.
 *
 * <p><strong>Deterministic, not random, on purpose.</strong> A coin-flip
 * mock ({@code random.nextBoolean()}) would technically exercise both the
 * success and failure branches, but at the cost of every demo, log walk, or
 * test run becoming non-reproducible — the exact failure this project's
 * {@code CountDownLatch}-gated concurrency test (Day 7) was designed to
 * avoid for the same reason. Keying the decision off {@code totalAmount}
 * instead means "book a seat priced under the threshold" and "book one
 * priced over it" are two reliable, repeatable ways to walk both paths of
 * this state machine on demand.</p>
 *
 * <p>{@code payment.mock.decline-threshold} defaults to {@code 1000.00} —
 * comfortably above every seat price this project's own usage examples use
 * ({@code 50.00}), so the happy path is what a fresh checkout of this repo
 * sees by default. Overriding it lower is the fastest way to see the
 * failure branch — {@link com.ahdyahmed.eventhub.booking.PaymentProcessedListener}
 * and the released-seat behavior it drives — without editing any code.</p>
 */
@Service
@Slf4j
public class MockPaymentServiceImpl implements PaymentService {

    private final BigDecimal declineThreshold;

    public MockPaymentServiceImpl(
            @Value("${payment.mock.decline-threshold:1000.00}") BigDecimal declineThreshold) {
        this.declineThreshold = declineThreshold;
    }

    @Override
    public PaymentResult charge(BookingConfirmedEvent booking) {
        if (booking.totalAmount().compareTo(declineThreshold) > 0) {
            String reason = "amount %s exceeds mock authorization limit %s"
                    .formatted(booking.totalAmount(), declineThreshold);
            log.info("Mock payment DECLINED for booking {}: {}", booking.bookingId(), reason);
            return PaymentResult.failure(reason);
        }
        log.info("Mock payment charged {} for booking {}", booking.totalAmount(), booking.bookingId());
        return PaymentResult.success();
    }
}
