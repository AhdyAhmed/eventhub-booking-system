package com.ahdyahmed.eventhub.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ahdyahmed.eventhub.event.Event;
import com.ahdyahmed.eventhub.payment.PaymentStatus;
import com.ahdyahmed.eventhub.payment.event.PaymentProcessedEvent;
import com.ahdyahmed.eventhub.seat.Seat;
import com.ahdyahmed.eventhub.seat.SeatStatus;
import com.ahdyahmed.eventhub.venue.Venue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

/**
 * Covers the status transition, the seat-status side effect, and the cache
 * eviction together — the three things this listener promises to keep in
 * sync in its class doc. {@link BookingConcurrencyIT}'s sibling for the
 * full event chain (Kafka included) is Day 15's job, same split as {@code
 * BookingServiceImplTest} vs. {@code BookingConcurrencyIT}.
 */
@ExtendWith(MockitoExtension.class)
class PaymentProcessedListenerTest {

    @Mock
    private BookingRepository bookingRepository;

    private final BookingStateMachine bookingStateMachine = new BookingStateMachine();
    private final CacheManager cacheManager = new ConcurrentMapCacheManager("seat-availability");
    private final SeatAvailabilityCacheEvictor seatAvailabilityCacheEvictor =
            new SeatAvailabilityCacheEvictor(cacheManager);

    private PaymentProcessedListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentProcessedListener(bookingRepository, bookingStateMachine, seatAvailabilityCacheEvictor);
    }

    private Seat seat(long id, SeatStatus status) {
        Venue venue = Venue.builder().id(1L).name("Arena").city("Cairo").capacity(100).build();
        Event event = Event.builder().id(10L).venue(venue).name("Show").category("CONCERT")
                .eventDate(Instant.now().plusSeconds(3600)).build();
        return Seat.builder().id(id).event(event).seatNumber("A" + id)
                .price(new BigDecimal("50.00")).status(status).build();
    }

    private Booking pendingBooking(Seat... seats) {
        Booking booking = Booking.builder().status(BookingStatus.PENDING).totalAmount(new BigDecimal("50.00")).build();
        booking.setId(500L);
        for (Seat seat : seats) {
            booking.addItem(BookingItem.builder().seat(seat).priceAtBooking(seat.getPrice()).build());
        }
        return booking;
    }

    private PaymentProcessedEvent processedEvent(PaymentStatus status, String reason) {
        return new PaymentProcessedEvent(500L, 10L, List.of(100L), new BigDecimal("50.00"),
                status, reason, Instant.now());
    }

    @Test
    void onPaymentProcessed_succeeded_confirmsBookingAndBooksSeats() {
        Seat seat = seat(100L, SeatStatus.RESERVED);
        Booking booking = pendingBooking(seat);
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));

        listener.onPaymentProcessed(processedEvent(PaymentStatus.SUCCEEDED, null));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        verify(bookingRepository).save(booking);
    }

    @Test
    void onPaymentProcessed_failed_failsBookingAndReleasesSeats() {
        Seat seat = seat(100L, SeatStatus.RESERVED);
        Booking booking = pendingBooking(seat);
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));

        listener.onPaymentProcessed(processedEvent(PaymentStatus.FAILED, "card declined"));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        verify(bookingRepository).save(booking);
    }

    @Test
    void onPaymentProcessed_failed_evictsSeatAvailabilityCacheForTheEvent() {
        Seat seat = seat(100L, SeatStatus.RESERVED);
        Booking booking = pendingBooking(seat);
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));

        var cache = cacheManager.getCache("seat-availability");
        cache.put("10-null", List.of());
        cache.put("10-RESERVED", List.of());
        cache.put("20-null", List.of()); // different event - must survive

        listener.onPaymentProcessed(processedEvent(PaymentStatus.FAILED, "card declined"));

        assertThat(cache.get("10-null")).isNull();
        assertThat(cache.get("10-RESERVED")).isNull();
        assertThat(cache.get("20-null")).isNotNull();
    }

    @Test
    void onPaymentProcessed_alreadyConfirmed_isIgnoredAndSeatsUntouched() {
        // Simulates a redelivered Kafka message after the first delivery
        // already resolved this booking - BookingStateMachine treats the
        // same-status transition as a no-op, and this listener must not
        // re-touch seats on top of that (a since-rebooked seat must not be
        // silently flipped back).
        Seat seat = seat(100L, SeatStatus.BOOKED);
        Booking booking = pendingBooking(seat);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));

        listener.onPaymentProcessed(processedEvent(PaymentStatus.SUCCEEDED, null));

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        verify(bookingRepository, never()).save(any(Booking.class));
    }
}
