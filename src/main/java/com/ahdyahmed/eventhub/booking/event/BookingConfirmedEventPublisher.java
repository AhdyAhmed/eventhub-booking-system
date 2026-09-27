package com.ahdyahmed.eventhub.booking.event;

import com.ahdyahmed.eventhub.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges "a booking was confirmed" (an in-JVM Spring {@code
 * ApplicationEvent}, published from inside {@code
 * BookingServiceImpl.create}'s {@code @Transactional} method) to "a message
 * actually left this process for Kafka."
 *
 * <p><strong>Why the indirection, not just calling {@code
 * KafkaTemplate.send} directly from {@code BookingServiceImpl}:</strong>
 * this is the exact "keep the publish inside the same transaction boundary"
 * problem the roadmap flagged for this day. Publishing to Kafka directly
 * inside the {@code @Transactional} method would mean the message can go
 * out before the database transaction actually commits — and if something
 * later in that same transaction then fails and rolls back, a consumer
 * would have already reacted to a booking that, as far as the database is
 * concerned, never happened. {@code @TransactionalEventListener(phase =
 * AFTER_COMMIT)} makes that ordering a guarantee instead of a race: this
 * method only runs at all if the transaction that published the event
 * actually committed. If it rolled back, the listener is simply never
 * invoked — no compensating action needed, because there's nothing to
 * compensate for.</p>
 *
 * <p><strong>What this is not:</strong> a transactional outbox. The
 * roadmap names the outbox pattern as the fuller guarantee and treats
 * skipping it as an accepted trade-off, not an oversight — see Design
 * decisions for the honest version of that trade-off. The gap this
 * approach still has: the database commit and the Kafka send are two
 * separate operations with no atomicity between them. If this process
 * crashes in the narrow window after the commit but before {@code
 * kafkaTemplate.send} completes, the booking exists in Postgres with no
 * corresponding event ever reaching Kafka — silently, with nothing to
 * detect or replay it. An outbox table written in the same transaction as
 * the booking, with a separate poller publishing from that table, is what
 * closes that gap; it's real additional infrastructure for a failure
 * window this portfolio project accepts rather than builds around.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingConfirmedEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        // Keyed by bookingId so every message about one booking - this one,
        // and whatever payment/notification events later days add - lands
        // on the same partition, giving per-booking ordering for free.
        kafkaTemplate.send(KafkaTopicConfig.BOOKING_CONFIRMED_TOPIC, event.bookingId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // Fire-and-forget by design (see class doc) - this
                        // log line is currently the only trace of a failed
                        // publish. No retry, no DLT for the producer side;
                        // Day 15's retry/DLT work is scoped to the
                        // *consumer* side of this same topic.
                        log.error("Failed to publish BookingConfirmedEvent for booking {}",
                                event.bookingId(), ex);
                    } else {
                        log.debug("Published BookingConfirmedEvent for booking {} to partition {}",
                                event.bookingId(), result.getRecordMetadata().partition());
                    }
                });
    }

}
