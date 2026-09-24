package com.ahdyahmed.eventhub.payment;

import com.ahdyahmed.eventhub.booking.event.BookingConfirmedEvent;
import com.ahdyahmed.eventhub.config.KafkaTopicConfig;
import com.ahdyahmed.eventhub.payment.event.PaymentProcessedEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * The second independent consumer of {@code booking-confirmed-events},
 * alongside Day 13's {@code NotificationListener} — and the reason that
 * day's per-listener {@code groupId} decision mattered rather than being
 * premature. This listener's own {@code groupId} ({@code payment-service})
 * is what lets it receive every {@link BookingConfirmedEvent} independently
 * of the notification consumer, instead of the two competing for the same
 * messages the way two instances of the same service correctly would.
 *
 * <p>Bridges Kafka to Kafka: consumes a booking confirmation, calls the
 * mock {@link PaymentService}, and republishes the outcome as a {@link
 * PaymentProcessedEvent} on its own topic. There's no database transaction
 * to protect here the way {@code BookingConfirmedEventPublisher}'s {@code
 * AFTER_COMMIT} protects the first publish — this method isn't wrapped in
 * {@code @Transactional} at all, so sending directly via {@link
 * KafkaTemplate} rather than through another {@code
 * @TransactionalEventListener} indirection is the right amount of
 * machinery, not a shortcut.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {

    private final PaymentService paymentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = KafkaTopicConfig.BOOKING_CONFIRMED_TOPIC, groupId = "payment-service")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        PaymentResult result = paymentService.charge(event);

        PaymentProcessedEvent processed = new PaymentProcessedEvent(
                event.bookingId(), event.eventId(), event.seatIds(), event.totalAmount(),
                result.status(), result.reason(), Instant.now());

        // Keyed by bookingId, same reasoning as BookingConfirmedEventPublisher:
        // every message about one booking - this one, and whatever a future
        // cancellation event adds - lands on the same partition, so a
        // consumer sees them in the order they actually happened.
        kafkaTemplate.send(KafkaTopicConfig.PAYMENT_PROCESSED_TOPIC, event.bookingId().toString(), processed)
                .whenComplete((sendResult, ex) -> {
                    if (ex != null) {
                        // Fire-and-forget by design, same accepted gap as the
                        // producer side of BookingConfirmedEventPublisher - no
                        // retry/DLT here yet, that's Day 15's job.
                        log.error("Failed to publish PaymentProcessedEvent for booking {}",
                                event.bookingId(), ex);
                    } else {
                        log.debug("Published PaymentProcessedEvent ({}) for booking {} to partition {}",
                                result.status(), event.bookingId(), sendResult.getRecordMetadata().partition());
                    }
                });
    }
}
