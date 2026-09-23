package com.ahdyahmed.eventhub.notification;

import com.ahdyahmed.eventhub.booking.event.BookingConfirmedEvent;
import com.ahdyahmed.eventhub.config.KafkaTopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to {@link BookingConfirmedEvent} by mock-sending a confirmation
 * email — this is this project's "decoupling" proof point. {@code
 * BookingServiceImpl} publishes an event and returns; it has no reference
 * to this class, no idea it exists, and no idea how many other listeners
 * (Day 14 adds a payment consumer reacting to the same event) are also
 * about to receive the exact same message. Deleting this entire class
 * would not change one line of the booking flow.
 *
 * <p><strong>{@code groupId} is set here explicitly, not inherited from a
 * {@code spring.kafka.consumer.group-id} default</strong> — and that's a
 * deliberate Kafka-specific choice, not an oversight. A Kafka consumer
 * group is a load-balancing unit: every consumer *in the same group*
 * competes for partitions, so only one of them gets any given message.
 * That's the right behavior *within* one logical service running multiple
 * instances (scale this listener horizontally and they'd correctly split
 * the work). It's the wrong behavior *across* different services that each
 * need to see every message — which is exactly the notification-vs-payment
 * situation Day 14 introduces. Giving each logical consumer its own group
 * id is what makes both of them receive every {@code BookingConfirmedEvent}
 * independently, the Kafka equivalent of RabbitMQ's fanout exchange with
 * one queue per consumer.</p>
 *
 * <p><strong>What this doesn't do yet, on purpose:</strong> no retry policy
 * and no dead-letter handling for a message that fails to process. Spring
 * Kafka's default error handling for an uncaught exception here is to log
 * it and keep going, which is silent data loss for this specific message —
 * an accepted gap flagged for Day 15, the same way the Day 8 caching gap
 * was flagged in code before Day 9 closed it.</p>
 */
@Component
@Slf4j
public class NotificationListener {

    @KafkaListener(topics = KafkaTopicConfig.BOOKING_CONFIRMED_TOPIC, groupId = "notification-service")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        // The mock: this project's entire "sends an email" story is one log
        // line. A real implementation would swap this method's body for a
        // call to an email provider; nothing about how the event got here
        // would need to change.
        log.info("Mock email -> {}: your booking {} for event {} ({} seat(s), total {}) is confirmed",
                event.userEmail(), event.bookingId(), event.eventId(), event.seatIds().size(),
                event.totalAmount());
    }

}
