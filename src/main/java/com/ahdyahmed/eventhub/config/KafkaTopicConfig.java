package com.ahdyahmed.eventhub.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic topology — the equivalent of the exchange/queue declarations
 * this project's roadmap originally planned for RabbitMQ, before Kafka was
 * chosen instead (see the README's Design decisions for the trade-offs of
 * that swap).
 *
 * <p>Declaring a {@link NewTopic} bean here is enough: Spring Boot
 * autoconfigures a {@code KafkaAdmin} whenever {@code spring-kafka} is on
 * the classpath, and that admin reconciles every {@code NewTopic} bean in
 * the context against the broker on startup — creating it if missing,
 * leaving it alone if it already exists with a compatible configuration.
 * Nothing calls this class directly; it's topology-as-code the same way
 * Flyway migrations are schema-as-code.</p>
 *
 * <p>Day 11 only sets the topic up; nothing publishes to it yet. That's Day
 * 12 (the actual {@code BookingConfirmedEvent} and the publish call in
 * {@code BookingServiceImpl}) and Day 13 (a consumer). Splitting
 * "the topic exists" from "something uses the topic" mirrors the original
 * RabbitMQ plan's own Day 11/Day 12 split for exchange/queue setup vs. the
 * first published event.</p>
 */
@Configuration
public class KafkaTopicConfig {

    public static final String BOOKING_CONFIRMED_TOPIC = "booking-confirmed-events";

    /**
     * Day 14's topic: {@code PaymentConsumer} publishes here after mock-
     * charging a {@code BookingConfirmedEvent}, and {@code
     * PaymentProcessedListener} is the sole consumer that turns the result
     * into a booking status transition. Same partitions/replicas reasoning
     * as {@link #bookingConfirmedTopic()} below — nothing about this
     * topic's traffic shape differs enough to justify a different number.
     */
    public static final String PAYMENT_PROCESSED_TOPIC = "payment-processed-events";

    /**
     * 3 partitions even against a single local broker: partition count is
     * the unit of parallelism a Kafka topic can ever have (increasing it
     * later doesn't just work — added partitions break the key-to-partition
     * mapping any existing keyed messages relied on), so it's worth setting
     * to something realistic now rather than "1, because that's all this
     * demo needs today." Replication factor 1, unlike partitions, only
     * reflects the current hardware: this local stack has exactly one
     * broker, so 1 is the only value that's actually possible — not a
     * simplification, a constraint. A real deployment would run this at 3
     * for durability, the same number used for
     * {@code KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR} etc. in
     * {@code docker-compose.yml}'s broker config if this were a multi-node
     * cluster.
     */
    @Bean
    public NewTopic bookingConfirmedTopic() {
        return TopicBuilder.name(BOOKING_CONFIRMED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentProcessedTopic() {
        return TopicBuilder.name(PAYMENT_PROCESSED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

}
