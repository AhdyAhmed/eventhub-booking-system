package com.ahdyahmed.eventhub.payment.event;

import com.ahdyahmed.eventhub.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The Kafka message {@link com.ahdyahmed.eventhub.payment.PaymentConsumer}
 * publishes to {@code payment-processed-events} once a mock charge has been
 * attempted for a booking. {@link com.ahdyahmed.eventhub.booking.PaymentProcessedListener}
 * is the one consumer that matters for this project: it's what actually
 * moves a {@code Booking} out of {@code PENDING}.
 *
 * <p>Carries {@code eventId} and {@code seatIds} — not strictly needed to
 * decide {@code PENDING → CONFIRMED} vs. {@code PENDING → FAILED} by
 * themselves — for the same reason {@link
 * com.ahdyahmed.eventhub.booking.event.BookingConfirmedEvent} carries
 * {@code userEmail}: whatever a consumer needs to act without a second
 * lookup back into booking's own database belongs on the event, not behind
 * a repository call a genuinely independent service wouldn't be able to
 * make. Here that's releasing or finalizing the exact seats this booking
 * touched.</p>
 *
 * <p>{@code reason} is {@code null} for a {@link PaymentStatus#SUCCEEDED}
 * result and populated for a {@link PaymentStatus#FAILED} one — see {@link
 * com.ahdyahmed.eventhub.payment.PaymentResult} for why the two aren't
 * split into separate event types instead: one shape, one topic, one
 * consumer method, with the status field as the branch point.</p>
 */
public record PaymentProcessedEvent(
        Long bookingId,
        Long eventId,
        List<Long> seatIds,
        BigDecimal totalAmount,
        PaymentStatus status,
        String reason,
        Instant processedAt
) {
}
