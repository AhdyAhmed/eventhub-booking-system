package com.ahdyahmed.eventhub.booking;

import com.ahdyahmed.eventhub.common.exception.BookingValidationException;
import com.ahdyahmed.eventhub.common.exception.ResourceNotFoundException;
import com.ahdyahmed.eventhub.common.exception.SeatUnavailableException;
import com.ahdyahmed.eventhub.booking.dto.BookingRequest;
import com.ahdyahmed.eventhub.booking.dto.BookingResponse;
import com.ahdyahmed.eventhub.seat.Seat;
import com.ahdyahmed.eventhub.seat.SeatRepository;
import com.ahdyahmed.eventhub.seat.SeatStatus;
import com.ahdyahmed.eventhub.user.User;
import com.ahdyahmed.eventhub.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the one operation in this project that actually needs concurrency
 * control: turning a handful of seats into a Booking without letting two
 * people walk away thinking they both got the same seat.
 *
 * <p>The defense is two layers, on purpose:</p>
 * <ol>
 *   <li>A pre-check on {@code seat.getStatus()} rejects the obvious case
 *   (someone already booked this seat a while ago) cheaply, without
 *   touching the database beyond the read that already happened.</li>
 *   <li>The {@code @Version} column on {@link Seat} catches the case the
 *   pre-check can't: two requests both read the seat as {@code AVAILABLE}
 *   at nearly the same instant and both try to reserve it. Only one
 *   {@code UPDATE} can win; the loser's version check matches zero rows,
 *   Hibernate raises {@link ObjectOptimisticLockingFailureException}, and
 *   this class translates that into the same {@link SeatUnavailableException}
 *   the pre-check throws — from the client's perspective, "the seat wasn't
 *   available" is one outcome, regardless of which layer caught it.</li>
 * </ol>
 *
 * <p>Day 7 is the test that actually fires two real concurrent requests at
 * the same seat and proves layer 2 holds when layer 1 can't.</p>
 */
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;
    private final CacheManager cacheManager;

    @Override
    @Transactional
    public BookingResponse create(BookingRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + request.userId()));

        List<Seat> seats = seatRepository.findAllById(request.seatIds());
        if (seats.size() != Set.copyOf(request.seatIds()).size()) {
            throw new ResourceNotFoundException("One or more seats were not found");
        }
        validateSingleEvent(seats);

        Booking booking = Booking.builder()
                .user(user)
                .status(BookingStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (Seat seat : seats) {
            reserve(seat);
            booking.addItem(BookingItem.builder()
                    .seat(seat)
                    .priceAtBooking(seat.getPrice())
                    .build());
            total = total.add(seat.getPrice());
        }
        booking.setTotalAmount(total);

        Booking saved = bookingRepository.save(booking);
        // Day 9: the gap flagged since Day 8 - a booking changes seat status
        // but the seat-availability cache never found out. validateSingleEvent
        // already guarantees every seat here belongs to one event, so one
        // eviction covers the whole booking.
        evictSeatAvailabilityCache(seats.get(0).getEvent().getId());
        return bookingMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id " + id));
        return bookingMapper.toResponse(booking);
    }

    /**
     * Flips one seat from AVAILABLE to RESERVED and flushes immediately —
     * deliberately not batched with the rest of the booking. Flushing per
     * seat means a version conflict on seat #2 of a 3-seat booking is caught
     * and reported right here, rather than being deferred to a single flush
     * at the end where it'd be harder to say which seat actually lost the
     * race. Whatever throws here propagates out of the whole method, and
     * {@code @Transactional} rolls back any seats already reserved earlier
     * in this same loop — a booking either gets every seat it asked for, or
     * none of them.
     */
    private void reserve(Seat seat) {
        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new SeatUnavailableException(seat.getId());
        }
        seat.setStatus(SeatStatus.RESERVED);
        try {
            seatRepository.saveAndFlush(seat);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new SeatUnavailableException(seat.getId());
        }
    }

    private void validateSingleEvent(List<Seat> seats) {
        long distinctEvents = seats.stream()
                .map(seat -> seat.getEvent().getId())
                .collect(Collectors.toSet())
                .size();
        if (distinctEvents > 1) {
            throw new BookingValidationException("All seats in a booking must belong to the same event");
        }
    }

    /**
     * Evicts exactly the {@code seat-availability} entries a booking for
     * this event could have gone stale under.
     *
     * <p>{@code SeatServiceImpl.getByEvent}'s cache key is {@code
     * eventId + "-" + status}, and {@code status} is either {@code null}
     * (unfiltered) or one of {@link SeatStatus}'s three values — a small,
     * fixed set known at compile time. That's the opposite situation from
     * Day 8's {@code event-search} cache, whose key depends on arbitrary
     * filter and paging combinations with no fixed upper bound, which is
     * why that one is evicted with {@code allEntries = true} instead. Here,
     * enumerating the exact keys is both possible and cheap, and — unlike
     * {@code allEntries} — it leaves every other event's cached seat
     * listings untouched.</p>
     *
     * <p>Uses {@link CacheManager} directly rather than {@code @CacheEvict}
     * because the key to evict isn't known from this method's own
     * parameters — it depends on the seats a booking touched, which
     * {@code @CacheEvict}'s SpEL key expressions can't reach into.</p>
     */
    private void evictSeatAvailabilityCache(Long eventId) {
        Cache cache = cacheManager.getCache("seat-availability");
        if (cache == null) {
            return;
        }
        cache.evict(eventId + "-null");
        for (SeatStatus status : SeatStatus.values()) {
            cache.evict(eventId + "-" + status);
        }
    }

}
