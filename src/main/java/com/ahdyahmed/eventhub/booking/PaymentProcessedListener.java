package com.ahdyahmed.eventhub.booking;

import com.ahdyahmed.eventhub.common.exception.ResourceNotFoundException;
import com.ahdyahmed.eventhub.config.KafkaTopicConfig;
import com.ahdyahmed.eventhub.payment.PaymentStatus;
import com.ahdyahmed.eventhub.payment.event.PaymentProcessedEvent;
import com.ahdyahmed.eventhub.seat.SeatStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The listener that finally moves a {@code Booking} out of {@code PENDING}
 * — the piece the roadmap's "PaymentProcessedEvent → booking status update"
 * line was really asking for, and the reason everything else added on Day
 * 14 exists.
 *
 * <p>Consumes {@link PaymentProcessedEvent} on its own {@code groupId}
 * ({@code booking-service}) via the dedicated {@code
 * paymentProcessedKafkaListenerContainerFactory} (see {@code
 * PaymentEventsConsumerConfig} for why a second factory was needed at
 * all). Two things happen together, inside one transaction:</p>
 *
 * <ol>
 *   <li>{@link BookingStateMachine} validates and applies the status
 *   transition — {@code CONFIRMED} on a successful charge, {@code FAILED}
 *   on a decline.</li>
 *   <li>The booking's seats resolve their {@code RESERVED} hold:
 *   {@code BOOKED} on success (the sale is final), or back to {@code
 *   AVAILABLE} on a decline — releasing them for someone else to book
 *   rather than leaving them stuck in limbo because a mock card was
 *   declined. Either way the seat-availability cache is evicted, the same
 *   convention {@code BookingServiceImpl} established on Day 9 for the
 *   same reason: a state change on a cached seat that doesn't evict is a
 *   guaranteed staleness bug, not a maybe.</li>
 * </ol>
 *
 * <p>No retry or dead-letter handling yet — same accepted gap as {@code
 * PaymentConsumer} and {@code NotificationListener}, closed project-wide on
 * Day 15.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessedListener {

    private final BookingRepository bookingRepository;
    private final BookingStateMachine bookingStateMachine;
    private final SeatAvailabilityCacheEvictor seatAvailabilityCacheEvictor;

    @KafkaListener(
            topics = KafkaTopicConfig.PAYMENT_PROCESSED_TOPIC,
            groupId = "booking-service",
            containerFactory = "paymentProcessedKafkaListenerContainerFactory")
    @Transactional
    public void onPaymentProcessed(PaymentProcessedEvent event) {
        Booking booking = bookingRepository.findById(event.bookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id " + event.bookingId()));

        BookingStatus target = event.status() == PaymentStatus.SUCCEEDED
                ? BookingStatus.CONFIRMED
                : BookingStatus.FAILED;
        BookingStatus previous = booking.getStatus();

        bookingStateMachine.transition(booking, target);
        if (previous == target) {
            // BookingStateMachine already treated this as a no-op (a
            // redelivered message) - the seats were resolved the first
            // time around, so touching them again would be redundant at
            // best and, for the AVAILABLE-release branch, actively wrong
            // if someone has since booked the released seat.
            log.debug("Booking {} already {} - ignoring redelivered payment event", booking.getId(), target);
            return;
        }

        SeatStatus resolvedSeatStatus = (target == BookingStatus.CONFIRMED) ? SeatStatus.BOOKED : SeatStatus.AVAILABLE;
        booking.getItems().forEach(item -> item.getSeat().setStatus(resolvedSeatStatus));
        seatAvailabilityCacheEvictor.evict(event.eventId());

        bookingRepository.save(booking);

        if (target == BookingStatus.FAILED) {
            log.info("Booking {} FAILED ({}) - seats released back to AVAILABLE", booking.getId(), event.reason());
        } else {
            log.info("Booking {} CONFIRMED - seats finalized as BOOKED", booking.getId());
        }
    }
}
