package com.ahdyahmed.eventhub.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Both halves of correlation-id propagation over Kafka, tested without a
 * broker: the producer interceptor's "MDC -> record header" and the
 * consumer interceptor's "record header -> MDC". {@code EventChainIT} proves
 * the two actually work together across real topics; this class pins down the
 * edge cases (no id, hostile id, never overwrite) cheaply and without Docker.
 */
class CorrelationIdKafkaInterceptorsTest {

    private final CorrelationIdProducerInterceptor producerInterceptor = new CorrelationIdProducerInterceptor();
    private final CorrelationIdRecordInterceptor<String, String> consumerInterceptor =
            new CorrelationIdRecordInterceptor<>();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private static String headerValue(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    // ---- producer side ----

    @Test
    void producer_stampsTheCurrentMdcIdOntoTheRecord() {
        MDC.put(MdcKeys.CORRELATION_ID, "corr-1");
        ProducerRecord<Object, Object> record = new ProducerRecord<>("some-topic", "key", "value");

        ProducerRecord<Object, Object> result = producerInterceptor.onSend(record);

        assertThat(headerValue(result.headers(), CorrelationId.HEADER)).isEqualTo("corr-1");
    }

    @Test
    void producer_withNoIdInMdc_addsNoHeader() {
        ProducerRecord<Object, Object> record = new ProducerRecord<>("some-topic", "key", "value");

        ProducerRecord<Object, Object> result = producerInterceptor.onSend(record);

        assertThat(result.headers().lastHeader(CorrelationId.HEADER)).isNull();
    }

    @Test
    void producer_neverOverwritesAnExistingHeader_soADeadLetteredRecordKeepsItsOriginalId() {
        // A DLT republish arrives with the original record's headers already
        // copied over, while the retrying thread's MDC may hold something else.
        MDC.put(MdcKeys.CORRELATION_ID, "some-other-id");
        ProducerRecord<Object, Object> record = new ProducerRecord<>("some-topic.DLT", "key", "value");
        record.headers().add(CorrelationId.HEADER, "original-id".getBytes(StandardCharsets.UTF_8));

        ProducerRecord<Object, Object> result = producerInterceptor.onSend(record);

        assertThat(headerValue(result.headers(), CorrelationId.HEADER)).isEqualTo("original-id");
        assertThat(result.headers().headers(CorrelationId.HEADER)).hasSize(1);
    }

    // ---- consumer side ----

    @Test
    void consumer_restoresTheIdFromTheHeaderIntoMdc_andClearsItAfterTheRecord() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("some-topic", 0, 0L, "key", "value");
        record.headers().add(CorrelationId.HEADER, "corr-from-kafka".getBytes(StandardCharsets.UTF_8));

        ConsumerRecord<String, String> returned = consumerInterceptor.intercept(record, null);

        // Must hand the record back unchanged - returning null would tell
        // Spring Kafka to skip the record entirely.
        assertThat(returned).isSameAs(record);
        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isEqualTo("corr-from-kafka");

        consumerInterceptor.afterRecord(record, null);

        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isNull();
    }

    @Test
    void consumer_recordWithoutAHeader_stillGetsAGeneratedId() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("some-topic", 0, 0L, "key", "value");

        consumerInterceptor.intercept(record, null);

        String id = MDC.get(MdcKeys.CORRELATION_ID);
        assertThat(id).isNotBlank();
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void consumer_hostileHeaderValue_isReplacedNotTrusted() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("some-topic", 0, 0L, "key", "value");
        record.headers().add(CorrelationId.HEADER, "forged\nlog-line".getBytes(StandardCharsets.UTF_8));

        consumerInterceptor.intercept(record, null);

        String id = MDC.get(MdcKeys.CORRELATION_ID);
        assertThat(id).doesNotContain("forged");
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void consumer_staleIdFromThePreviousRecord_isOverwrittenNotInherited() {
        MDC.put(MdcKeys.CORRELATION_ID, "left-over-from-previous-record");
        ConsumerRecord<String, String> record = new ConsumerRecord<>("some-topic", 0, 1L, "key", "value");
        record.headers().add(CorrelationId.HEADER, "this-records-id".getBytes(StandardCharsets.UTF_8));

        consumerInterceptor.intercept(record, null);

        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isEqualTo("this-records-id");
    }
}
