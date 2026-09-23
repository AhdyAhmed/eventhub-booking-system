package com.ahdyahmed.eventhub.booking.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Fired once a booking has successfully reserved its seats and committed.
 *
 * <p>This same class serves two roles, deliberately: within the JVM it's a
 * Spring {@code ApplicationEvent} payload (see {@link
 * BookingConfirmedEventPublisher}), and on the wire it's the exact JSON
 * body a Kafka consumer on {@code booking-confirmed-events} receives. One
 * shape for both avoids a separate, hand-maintained mapping between "what
 * happened internally" and "what we told the outside world happened,"
 * which would drift the moment one changed and the other didn't.</p>
 *
 * <p>{@code status} isn't a field here: this event only ever fires from the
 * {@code AFTER_COMMIT} path (see {@link BookingConfirmedEventPublisher}),
 * so "confirmed" is the one state a consumer can assume without needing to
 * ask. A booking's later transitions — payment succeeding or failing,
 * cancellation — are different events for a consumer to react to
 * differently, not a status flag on this one (Day 14 introduces the state
 * machine those will come from).</p>
 *
 * <p>{@code userEmail} (Day 13) is here so {@code NotificationListener}
 * doesn't need a database lookup — or worse, a dependency on {@code
 * UserRepository} — just to know who to mock-email. A real multi-service
 * deployment wouldn't have the notification consumer sharing this app's
 * database at all; putting what a consumer needs directly on the event is
 * what keeps that possible later without a breaking change to this
 * contract.</p>
 */
public record BookingConfirmedEvent(
        Long bookingId,
        Long userId,
        String userEmail,
        Long eventId,
        List<Long> seatIds,
        BigDecimal totalAmount,
        Instant confirmedAt
) {
}
