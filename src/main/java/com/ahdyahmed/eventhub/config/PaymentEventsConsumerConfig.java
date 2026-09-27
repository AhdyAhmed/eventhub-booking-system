package com.ahdyahmed.eventhub.config;

import com.ahdyahmed.eventhub.payment.event.PaymentProcessedEvent;
import java.util.Map;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * A second {@code @KafkaListener} container factory, needed the moment a
 * second Kafka event type entered this project.
 *
 * <p>{@code application.yml}'s {@code spring.kafka.consumer.properties}
 * sets one global {@code spring.json.value.default.type} (currently {@code
 * BookingConfirmedEvent}) on the auto-configured, shared consumer factory
 * every plain {@code @KafkaListener} uses by default — fine when
 * {@code NotificationListener} (Day 13) and {@code PaymentConsumer} (Day
 * 14) are the only consumers, since both listen on {@code
 * booking-confirmed-events} and both want that same default type. {@code
 * PaymentProcessedListener} listens on a different topic for a different
 * event shape, {@link PaymentProcessedEvent}, so it needs its own factory
 * with its own default type rather than fighting over the one global
 * property — the two facts "which class to deserialize into" and "which
 * topic this listener reads" would otherwise be pulling in opposite
 * directions on the same shared bean.</p>
 *
 * <p>{@link KafkaProperties#buildConsumerProperties()} is reused rather
 * than hand-copying bootstrap servers, deserializer classes, and trusted
 * packages a second time — this factory only overrides the one property
 * that actually needs to differ.</p>
 *
 * <p><strong>Day 15:</strong> this factory now also carries the shared
 * {@code CommonErrorHandler} from {@code KafkaErrorHandlingConfig} — the
 * same bean {@code NotificationListener} and {@code PaymentConsumer} get
 * for free via the replaced default factory. Because this factory is
 * separately constructed by hand (it always has been, for the
 * deserialization-type reason above), attaching the error handler here is
 * one explicit line rather than something that happens automatically —
 * worth calling out so this factory doesn't quietly fall out of sync with
 * the default one's retry/DLT behavior the next time either changes.</p>
 */
@Configuration
public class PaymentEventsConsumerConfig {

    @Bean
    public ConsumerFactory<String, Object> paymentProcessedConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentProcessedEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> paymentProcessedKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> paymentProcessedConsumerFactory,
            CommonErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentProcessedConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
