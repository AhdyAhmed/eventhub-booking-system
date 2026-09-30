package com.ahdyahmed.eventhub.common.logging;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * The consumer-side half of {@link CorrelationIdProducerInterceptor}: before
 * a {@code @KafkaListener} method runs, reads the {@code X-Correlation-Id}
 * header off the record and puts it in the MDC, so every log line the
 * listener writes (and every message it publishes onward) carries the id of
 * the HTTP request that started the chain.
 *
 * <p><strong>A record with no usable header still gets an id</strong> — a
 * freshly generated one — rather than leaving the MDC empty. A message
 * produced by something other than this app (or before this feature
 * existed) then still produces log lines that correlate <em>with each
 * other</em>, even though they can't correlate back to a request.</p>
 *
 * <p><strong>The MDC is overwritten on every record, and cleared after
 * each one.</strong> A Kafka listener thread processes record after record;
 * the same reasoning as {@code CorrelationIdFilter}'s {@code finally}
 * applies — an id must never leak from one message to the next. Overwriting
 * in {@link #intercept} as well as clearing in {@link #afterRecord} means
 * that even if a future Spring Kafka version skips {@code afterRecord} on
 * some failure path, the next record still starts from its own id.</p>
 *
 * <p>Generic over key/value types so the one class can be attached to both
 * of this project's container factories (they're typed differently) without
 * unchecked casts. It has to be attached to each factory explicitly — see
 * {@code KafkaErrorHandlingConfig} and {@code PaymentEventsConsumerConfig}.</p>
 */
public class CorrelationIdRecordInterceptor<K, V> implements RecordInterceptor<K, V> {

    @Override
    public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        Header header = record.headers().lastHeader(CorrelationId.HEADER);
        String candidate = (header == null || header.value() == null)
                ? null
                : new String(header.value(), StandardCharsets.UTF_8);

        MDC.put(MdcKeys.CORRELATION_ID, CorrelationId.sanitizeOrGenerate(candidate));
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        MDC.remove(MdcKeys.CORRELATION_ID);
    }
}
