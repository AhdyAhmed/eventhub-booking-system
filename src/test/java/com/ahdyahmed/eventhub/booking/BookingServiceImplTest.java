package com.ahdyahmed.eventhub.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ahdyahmed.eventhub.booking.dto.BookingRequest;
import com.ahdyahmed.eventhub.booking.dto.BookingResponse;
import com.ahdyahmed.eventhub.booking.event.BookingConfirmedEvent;
import com.ahdyahmed.eventhub.common.exception.BookingValidationException;
import com.ahdyahmed.eventhub.common.exception.ResourceNotFoundException;
import com.ahdyahmed.eventhub.common.exception.SeatUnavailableException;
import com.ahdyahmed.eventhub.event.Event;
import com.ahdyahmed.eventhub.seat.Seat;
import com.ahdyahmed.eventhub.seat.SeatRepository;
import com.ahdyahmed.eventhub.seat.SeatStatus;
import com.ahdyahmed.eventhub.user.User;
import com.ahdyahmed.eventhub.user.UserRepository;
import com.ahdyahmed.eventhub.venue.Venue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Covers everything {@code BookingServiceImpl} can do except the one thing
 * a unit test structurally can't prove: that the optimistic-lock path
 * actually fires under real concurrent database access, rather than a
 * mocked {@code saveAndFlush()}. That's what {@link BookingConcurrencyIT}
 * is for. This class proves the surrounding logic — happy path, every
 * failure mode, and that the optimistic-lock exception gets translated
 * correctly — cheaply and without Docker.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final BookingMapper bookingMapper = new BookingMapper();

    // A real cache manager, not a mock, wrapped in the real evictor (not a
    // mock either) - the eviction test needs actual get/put/evict
    // semantics, and ConcurrentMapCacheManager gives that without needing
    // Redis or Testcontainers for a plain unit test. Same evictor instance
    // PaymentProcessedListenerTest uses, for the same reason: it's the one
    // place the "which keys does this event's seats live under" logic is
    // defined, as of Day 14.
    private final CacheManager cacheManager = new ConcurrentMapCacheManager("seat-availability");
    private final SeatAvailabilityCacheEvictor seatAvailabilityCacheEvictor =
            new SeatAvailabilityCacheEvictor(cacheManager);

    private BookingServiceImpl bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingServiceImpl(bookingRepository, seatRepository, userRepository, bookingMapper,
                seatAvailabilityCacheEvictor, eventPublisher);
    }

    private User user(long id) {
        return User.builder().id(id).fullName("Test User").email("test@example.com").build();
    }

    private Event event(long id) {
        Venue venue = Venue.builder().id(1L).name("Arena").city("Cairo").capacity(100).build();
        return Event.builder()
                .id(id)
                .venue(venue)
                .name("Show")
                .category("CONCERT")
                .eventDate(Instant.now().plus(2, ChronoUnit.HOURS))
                .build();
    }

    private Seat seat(long id, Event event, SeatStatus status) {
        return Seat.builder()
                .id(id)
                .event(event)
                .seatNumber("A" + id)
                .price(new BigDecimal("50.00"))
                .status(status)
                .build();
    }

    @Test
    void create_happyPath_reservesSeatAndCreatesBooking() {
        User user = user(1L);
        Seat seat = seat(100L, event(10L), SeatStatus.AVAILABLE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(seatRepository.findAllById(List.of(100L))).thenReturn(List.of(seat));
        when(seatRepository.saveAndFlush(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking booking = inv.getArgument(0);
            booking.setId(500L);
            return booking;
        });

        BookingResponse response = bookingService.create(new BookingRequest(1L, List.of(100L)));

        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.items()).hasSize(1);
        assertThat(response.totalAmount()).isEqualByComparingTo("50.00");
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
    }

    @Test
    void create_happyPath_publishesBookingConfirmedEventWithCorrectFields() {
        Event event = event(10L);
        Seat seat = seat(100L, event, SeatStatus.AVAILABLE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(seatRepository.findAllById(List.of(100L))).thenReturn(List.of(seat));
        when(seatRepository.saveAndFlush(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking booking = inv.getArgument(0);
            booking.setId(500L);
            return booking;
        });

        bookingService.create(new BookingRequest(1L, List.of(100L)));

        ArgumentCaptor<BookingConfirmedEvent> captor = ArgumentCaptor.forClass(BookingConfirmedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        BookingConfirmedEvent published = captor.getValue();
        assertThat(published.bookingId()).isEqualTo(500L);
        assertThat(published.userId()).isEqualTo(1L);
        assertThat(published.userEmail()).isEqualTo("test@example.com");
        assertThat(published.eventId()).isEqualTo(10L);
        assertThat(published.seatIds()).containsExactly(100L);
        assertThat(published.totalAmount()).isEqualByComparingTo("50.00");
        assertThat(published.confirmedAt()).isNotNull();
    }

    @Test
    void create_happyPath_evictsSeatAvailabilityCacheForTheEvent() {
        Event event = event(10L);
        Seat seat = seat(100L, event, SeatStatus.AVAILABLE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(seatRepository.findAllById(List.of(100L))).thenReturn(List.of(seat));
        when(seatRepository.saveAndFlush(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking booking = inv.getArgument(0);
            booking.setId(500L);
            return booking;
        });

        // Pre-populate every key this event's seat listing could be cached
        // under, exactly as SeatServiceImpl.getByEvent's real cache key
        // (eventId + "-" + status) would have left them.
        Cache cache = cacheManager.getCache("seat-availability");
        cache.put("10-null", List.of());
        cache.put("10-AVAILABLE", List.of());
        cache.put("10-RESERVED", List.of());
        cache.put("10-BOOKED", List.of());
        // A different event's cache entry - eviction must not touch this.
        cache.put("20-null", List.of());

        bookingService.create(new BookingRequest(1L, List.of(100L)));

        assertThat(cache.get("10-null")).isNull();
        assertThat(cache.get("10-AVAILABLE")).isNull();
        assertThat(cache.get("10-RESERVED")).isNull();
        assertThat(cache.get("10-BOOKED")).isNull();
        assertThat(cache.get("20-null")).isNotNull();
    }

    @Test
    void create_userMissing_throwsAndNeverTouchesSeatRepository() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(99L, List.of(1L))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User");

        verifyNoInteractions(seatRepository);
    }

    @Test
    void create_someSeatsMissing_throwsResourceNotFoundException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(seatRepository.findAllById(List.of(100L, 200L)))
                .thenReturn(List.of(seat(100L, event(10L), SeatStatus.AVAILABLE)));

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(1L, List.of(100L, 200L))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("seat");
    }

    @Test
    void create_seatAlreadyTaken_throwsSeatUnavailableWithoutAttemptingSave() {
        Seat seat = seat(100L, event(10L), SeatStatus.RESERVED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(seatRepository.findAllById(List.of(100L))).thenReturn(List.of(seat));

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(1L, List.of(100L))))
                .isInstanceOf(SeatUnavailableException.class);

        verify(seatRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_optimisticLockConflict_isTranslatedToSeatUnavailableException() {
        Seat seat = seat(100L, event(10L), SeatStatus.AVAILABLE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(seatRepository.findAllById(List.of(100L))).thenReturn(List.of(seat));
        when(seatRepository.saveAndFlush(any(Seat.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Seat.class, 100L));

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(1L, List.of(100L))))
                .isInstanceOf(SeatUnavailableException.class);

        // The whole point: a lost race never reaches the point of persisting a booking.
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void create_seatsFromDifferentEvents_throwsBookingValidationException() {
        Seat seatA = seat(100L, event(10L), SeatStatus.AVAILABLE);
        Seat seatB = seat(200L, event(20L), SeatStatus.AVAILABLE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(seatRepository.findAllById(List.of(100L, 200L))).thenReturn(List.of(seatA, seatB));

        assertThatThrownBy(() -> bookingService.create(new BookingRequest(1L, List.of(100L, 200L))))
                .isInstanceOf(BookingValidationException.class);
    }

    @Test
    void getById_missing_throwsResourceNotFoundException() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getById(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

}
