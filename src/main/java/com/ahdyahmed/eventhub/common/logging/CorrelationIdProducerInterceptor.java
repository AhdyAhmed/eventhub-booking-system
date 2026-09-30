package com.ahdyahmed.eventhub.common.logging;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

/**
 * Stamps the current correlation id onto every outgoing Kafka record as an
 * {@code X-Correlation-Id} header, so the id assigned to an HTTP request
 * survives the hop onto the broker: HTTP request → {@code
 * booking-confirmed-events} → payment consumer → {@code
 * payment-processed-events} → booking listener all carry the same id, and
 * one search reconstructs the whole chain.
 *
 * <p>Without this, the lifecycle log lines the roadmap asks for
 * ({@code payment.processed}, {@code notification.sent}) would be written on
 * Kafka consumer threads that never saw the HTTP request, and would have no
 * way to say which request caused them.</p>
 *
 * <p><strong>Why a Kafka {@link ProducerInterceptor} instead of adding the
 * header at each {@code kafkaTemplate.send(...)} call site:</strong> it's
 * registered once (see {@code spring.kafka.producer.properties.
 * interceptor.classes} in {@code application.yml}) and covers <em>every</em>
 * send made through the app's producer — including ones that don't exist
 * yet, and including the dead-letter publishes made by Spring Kafka's own
 * {@code DeadLetterPublishingRecoverer}, which no application code calls
 * directly. A call-site approach would have to be remembered at every new
 * {@code send}. {@code onSend} runs on the thread that called {@code
 * send()}, which is what makes reading a thread-local MDC here valid.</p>
 *
 * <p><strong>An existing header is never overwritten.</strong> A
 * dead-lettered record is republished with the original record's headers
 * already copied over; the original id is the one worth keeping, not
 * whatever the retrying consumer thread's MDC currently holds.</p>
 *
 * <p>Kafka instantiates this class itself, by name, from configuration — so
 * it needs a public no-argument constructor and can't take Spring-injected
 * dependencies. It doesn't need any: the MDC is static thread-local state.</p>
 */
public class CorrelationIdProducerInterceptor implements ProducerInterceptor<Object, Object> {

    @Override
    public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        if (correlationId != null && record.headers().lastHeader(CorrelationId.HEADER) == null) {
            record.headers().add(CorrelationId.HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // Nothing to do: this interceptor only decorates outgoing records.
    }

    @Override
    public void close() {
        // No resources held.
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // No configuration needed.
    }
}
