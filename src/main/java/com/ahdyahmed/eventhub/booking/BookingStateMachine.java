package com.ahdyahmed.eventhub.booking;

import com.ahdyahmed.eventhub.common.exception.InvalidBookingStateTransitionException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Formalizes the {@link BookingStatus} lifecycle {@code BookingStatus}'s
 * own Javadoc has pointed to since Day 2: which transitions are legal, and
 * what happens when something tries an illegal one.
 *
 * <p>Only two transitions are actually exercised as of Day 14 —
 * {@code PENDING → CONFIRMED} and {@code PENDING → FAILED}, both driven by
 * {@link PaymentProcessedListener}. {@code PENDING → CANCELLED} and
 * {@code CONFIRMED → CANCELLED} are declared here now, unused, for the same
 * reason {@code SeatStatus.BOOKED} and {@code BookingStatus.CANCELLED}
 * themselves were declared back on Day 2 before anything set them: Day 16's
 * planned booking-cancellation endpoint needs a legal path to reach {@code
 * CANCELLED} from either state a real booking could be cancelled from, and
 * the schema/lifecycle shouldn't need to change shape when that endpoint
 * arrives — only this table gains a caller.</p>
 *
 * <p>{@code FAILED} and {@code CANCELLED} are terminal: once a booking
 * lands there, nothing in this table lets it move again.</p>
 */
@Component
public class BookingStateMachine {

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(BookingStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(BookingStatus.PENDING,
                EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.FAILED, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.CONFIRMED, EnumSet.of(BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.FAILED, EnumSet.noneOf(BookingStatus.class));
        ALLOWED_TRANSITIONS.put(BookingStatus.CANCELLED, EnumSet.noneOf(BookingStatus.class));
    }

    /**
     * Moves {@code booking} to {@code target}, or throws if that's not a
     * legal move from its current status.
     *
     * <p>A transition to the booking's own current status is treated as a
     * no-op success, not an error — deliberately idempotent. Kafka's
     * at-least-once delivery means {@code PaymentProcessedListener} can see
     * the same {@link com.ahdyahmed.eventhub.payment.event.PaymentProcessedEvent}
     * twice after a consumer restart or a rebalance; the second delivery
     * finding the booking already {@code CONFIRMED} should be a quiet
     * no-op, not a 409 that gets logged as if something were wrong.</p>
     */
    public void transition(Booking booking, BookingStatus target) {
        BookingStatus current = booking.getStatus();
        if (current == target) {
            return;
        }
        Set<BookingStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidBookingStateTransitionException(booking.getId(), current, target);
        }
        booking.setStatus(target);
    }
}
