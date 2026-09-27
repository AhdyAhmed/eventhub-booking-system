package com.ahdyahmed.eventhub.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ahdyahmed.eventhub.booking.event.BookingConfirmedEvent;
import com.ahdyahmed.eventhub.config.KafkaTopicConfig;
import com.ahdyahmed.eventhub.payment.event.PaymentProcessedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class PaymentConsumerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private PaymentConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentConsumer(paymentService, kafkaTemplate);
        // The real send() returns a CompletableFuture the class chains
        // .whenComplete() onto, and the success branch of that callback
        // calls .getRecordMetadata().partition() - RETURNS_DEEP_STUBS
        // keeps that chain from NPE-ing without needing an embedded/real
        // broker for this test.
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class, Answers.RETURNS_DEEP_STUBS)));
    }

    @Test
    void onBookingConfirmed_publishesPaymentProcessedEventWithChargeResult() {
        BookingConfirmedEvent booking = new BookingConfirmedEvent(
                500L, 1L, "test@example.com", 10L, List.of(100L, 101L), new BigDecimal("100.00"), Instant.now());
        when(paymentService.charge(booking)).thenReturn(PaymentResult.success());

        consumer.onBookingConfirmed(booking);

        ArgumentCaptor<PaymentProcessedEvent> captor = ArgumentCaptor.forClass(PaymentProcessedEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopicConfig.PAYMENT_PROCESSED_TOPIC), eq("500"), captor.capture());

        PaymentProcessedEvent published = captor.getValue();
        assertThat(published.bookingId()).isEqualTo(500L);
        assertThat(published.eventId()).isEqualTo(10L);
        assertThat(published.seatIds()).containsExactly(100L, 101L);
        assertThat(published.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(published.reason()).isNull();
    }

    @Test
    void onBookingConfirmed_declinedCharge_publishesFailedEventWithReason() {
        BookingConfirmedEvent booking = new BookingConfirmedEvent(
                500L, 1L, "test@example.com", 10L, List.of(100L), new BigDecimal("5000.00"), Instant.now());
        when(paymentService.charge(booking)).thenReturn(PaymentResult.failure("amount exceeds limit"));

        consumer.onBookingConfirmed(booking);

        ArgumentCaptor<PaymentProcessedEvent> captor = ArgumentCaptor.forClass(PaymentProcessedEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopicConfig.PAYMENT_PROCESSED_TOPIC), eq("500"), captor.capture());

        PaymentProcessedEvent published = captor.getValue();
        assertThat(published.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(published.reason()).isEqualTo("amount exceeds limit");
    }
}
