package com.ahdyahmed.eventhub.payment;

import static com.ahdyahmed.eventhub.common.logging.LogEvents.PAYMENT_PROCESSED;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static net.logstash.logback.argument.StructuredArguments.value;

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
 *
 * <p><strong>Day 17:</strong> this is where the {@code payment.processed}
 * lifecycle line is logged — one line per booking, carrying the outcome
 * ({@code paymentStatus}) and, for a decline, the {@code reason}. The
 * {@code correlationId} on it was restored from the incoming Kafka record's
 * header by {@code CorrelationIdRecordInterceptor}, and the {@code
 * KafkaTemplate.send} below re-stamps it onto the outgoing {@link
 * PaymentProcessedEvent} record — that's the second hop of the chain.
 * {@code MockPaymentServiceImpl}'s own per-charge lines were dropped to
 * {@code DEBUG} so an outcome isn't reported twice at {@code INFO}.</p>
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

        log.info("Payment {} for booking {}",
                value("paymentStatus", result.status()),
                value("bookingId", event.bookingId()),
                kv("eventId", event.eventId()),
                kv("amount", event.totalAmount()),
                kv("reason", result.reason()),
                kv("event", PAYMENT_PROCESSED));

        PaymentProcessedEvent processed = new PaymentProcessedEvent(
                event.bookingId(), event.eventId(), event.seatIds(), event.totalAmount(),
                result.status(), result.reason(), Instant.now());

        // Keyed by bookingId, same reasoning as BookingConfirmedEventPublisher:
        // every message about one booking - this one, and whatever a future
        // cancellation event adds - lands on the same partition, so a
        // consumer sees them in the order they actually happened.
        // Wait for broker acknowledgement before returning from the listener.
        // If the publish fails, join() propagates the failure so the shared
        // listener error handler retries and ultimately dead-letters the
        // incoming booking event instead of committing its offset and losing
        // the payment result. The mock charge is deterministic/idempotent, so
        // replaying this listener is safe in the current application.
        var sendResult = kafkaTemplate
                .send(KafkaTopicConfig.PAYMENT_PROCESSED_TOPIC, event.bookingId().toString(), processed)
                .join();
        log.debug("Published PaymentProcessedEvent ({}) for booking {} to partition {}",
                result.status(), event.bookingId(), sendResult.getRecordMetadata().partition());
    }
}
