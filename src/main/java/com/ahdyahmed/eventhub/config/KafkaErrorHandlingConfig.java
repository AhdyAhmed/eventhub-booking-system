package com.ahdyahmed.eventhub.config;

import com.ahdyahmed.eventhub.common.exception.InvalidBookingStateTransitionException;
import com.ahdyahmed.eventhub.common.exception.ResourceNotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * The Day 15 piece: what happens when a {@code @KafkaListener} method
 * throws. Through Day 14, the answer for every consumer in this project was
 * "Spring Kafka's default error handling logs it and moves on" — silent
 * data loss for that one message, flagged explicitly in {@code
 * NotificationListener}'s, {@code PaymentConsumer}'s, and {@code
 * PaymentProcessedListener}'s own class docs as an accepted gap. This class
 * closes it for all three at once.
 *
 * <p><strong>Why one shared policy instead of per-listener handling:</strong>
 * replacing Spring Boot's auto-configured {@code kafkaListenerContainerFactory}
 * bean (same bean name — {@link #kafkaListenerContainerFactory} below —
 * intentionally preempts it) means every {@code @KafkaListener} that
 * doesn't name an explicit {@code containerFactory} — {@code
 * NotificationListener} and {@code PaymentConsumer} — picks up retry + DLT
 * with zero changes to either class. {@code PaymentProcessedListener}
 * already needed its own factory for an unrelated reason (a different
 * default deserialization type, see {@code PaymentEventsConsumerConfig});
 * that factory is updated in the same commit to use the identical {@link
 * CommonErrorHandler} bean, so the policy is genuinely one definition, not
 * two copies that could drift.</p>
 */
@Configuration
public class KafkaErrorHandlingConfig {

    /**
     * Republishes a record that exhausted its retries to {@code
     * <source-topic>.DLT}, on the same partition number as the original —
     * Spring Kafka's default destination-resolving behavior, which is
     * exactly why {@code KafkaTopicConfig}'s two {@code .DLT} topics are
     * declared with the same partition count (3) as the topics they shadow.
     *
     * <p>Typed as {@code KafkaTemplate<Object, Object>}, not {@code
     * <String, Object>} the way {@code BookingConfirmedEventPublisher} and
     * {@code PaymentConsumer} are — that's not a style inconsistency, it's
     * what {@link DeadLetterPublishingRecoverer}'s constructor actually
     * requires, and it happens to be the literal generic signature Spring
     * Boot's autoconfiguration gives the underlying bean, so both classes
     * of caller are wiring to the same object either way.</p>
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<Object, Object> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate);
    }

    /**
     * 500ms before the first retry, doubling each attempt, capped at 5s
     * between attempts, giving up once 10s of cumulative retry time has
     * elapsed (roughly 4 attempts total) and handing the record to the
     * recoverer above. Exponential rather than fixed-interval: the kind of
     * transient failure this is meant to survive — a momentary DB
     * connection blip, a broker leader election mid-request — is more
     * likely resolved by a slightly longer pause each time than by
     * hammering it at a constant interval, which looks a lot like whatever
     * caused the failure in the first place.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxInterval(5_000L);
        backOff.setMaxElapsedTime(10_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(deadLetterPublishingRecoverer, backOff);

        // Both represent a permanent, logic-level problem rather than a
        // transient one - retrying either just delays reaching the same
        // DLT outcome by 10 wasted seconds:
        //  - InvalidBookingStateTransitionException: BookingStateMachine
        //    already treats a *redelivered* same-status message as a
        //    silent no-op (see its own class doc) - reaching this exception
        //    at all means a genuinely illegal transition was requested,
        //    which no amount of retrying changes.
        //  - ResourceNotFoundException: PaymentProcessedListener only sees
        //    this if a bookingId on the event doesn't exist in the database
        //    at all - not possible in normal operation, since
        //    BookingConfirmedEventPublisher only ever fires AFTER_COMMIT,
        //    so a missing booking means something is already wrong in a way
        //    a retry can't fix.
        errorHandler.addNotRetryableExceptions(
                InvalidBookingStateTransitionException.class,
                ResourceNotFoundException.class);

        return errorHandler;
    }

    /**
     * Deliberately named {@code kafkaListenerContainerFactory} — the exact
     * bean name Spring Boot's {@code KafkaAutoConfiguration} uses for its
     * own default factory, guarded by {@code @ConditionalOnMissingBean}.
     * Defining this bean here means Boot's version is never created, and
     * this one — configured identically via the autoconfigured {@link
     * ConcurrentKafkaListenerContainerFactoryConfigurer} and then carrying
     * one extra line, {@code setCommonErrorHandler} — becomes the default
     * every unqualified {@code @KafkaListener} binds to.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> kafkaConsumerFactory,
            CommonErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, kafkaConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
