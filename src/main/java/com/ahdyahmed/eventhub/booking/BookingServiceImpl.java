package com.ahdyahmed.eventhub.booking;

import static com.ahdyahmed.eventhub.common.logging.LogEvents.BOOKING_STATUS_CHANGED;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static net.logstash.logback.argument.StructuredArguments.value;

import com.ahdyahmed.eventhub.booking.event.BookingConfirmedEvent;
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
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
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
 *
 * <p>Day 16 added the ownership checks {@code getById} and {@code cancel}
 * both start with — {@code requireOwnership} throws Spring Security's own
 * {@link AccessDeniedException} rather than a project-specific one, since
 * "you don't own this resource" is exactly what that exception already
 * means, and reusing it means one line in {@code GlobalExceptionHandler}
 * covers both this and any future ownership check the same way.</p>
 *
 * <p>Day 17: {@code cancel} logs the {@code booking.status_changed}
 * lifecycle event. {@code create} deliberately does <em>not</em> log
 * {@code booking.created} itself — that line lives in {@code
 * BookingConfirmedEventPublisher}, which only runs after the transaction has
 * actually committed (see its class doc for why that ordering matters).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;
    private final SeatAvailabilityCacheEvictor seatAvailabilityCacheEvictor;
    private final BookingStateMachine bookingStateMachine;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public BookingResponse create(Long userId, BookingRequest request) {
        if (request.seatIds().size() != request.seatIds().stream().distinct().count()) {
            throw new BookingValidationException("seatIds must not contain duplicates");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + userId));

        List<Seat> seats = seatRepository.findAllById(request.seatIds());
        if (seats.size() != Set.copyOf(request.seatIds()).size()) {
            throw new ResourceNotFoundException("One or more seats were not found");
        }
        validateSingleEvent(seats);
        Long eventId = seats.get(0).getEvent().getId();

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
        // eviction covers the whole booking. Day 14: the eviction logic
        // itself moved to SeatAvailabilityCacheEvictor, a shared bean -
        // PaymentProcessedListener needs the identical eviction later in
        // this same booking's life, once payment resolves the seats to
        // BOOKED or back to AVAILABLE.
        seatAvailabilityCacheEvictor.evict(eventId);
        // Day 12: publishing here, not sending to Kafka directly, is the
        // point - see BookingConfirmedEventPublisher's class doc for why
        // AFTER_COMMIT matters. This call just registers the event; nothing
        // leaves the process until (and unless) this transaction commits.
        eventPublisher.publishEvent(new BookingConfirmedEvent(
                saved.getId(), user.getId(), user.getEmail(), eventId, request.seatIds(), total, Instant.now()));
        return bookingMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getById(Long id, Long requestingUserId) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id " + id));
        requireOwnership(booking, requestingUserId);
        return bookingMapper.toResponse(booking);
    }

    /**
     * The endpoint {@link BookingStateMachine}'s {@code PENDING → CANCELLED}
     * and {@code CONFIRMED → CANCELLED} transitions were declared for, back
     * on Day 14, unused until now. Releases every seat the booking held
     * back to {@code AVAILABLE} regardless of whether payment had already
     * resolved them to {@code BOOKED} or they were still {@code RESERVED} —
     * either way, "cancelled" means the seat is free again.
     *
     * <p>{@link Booking}'s optimistic-lock version prevents this method and
     * {@code PaymentProcessedListener} from silently overwriting each other
     * if cancellation races payment settlement. The losing transaction gets
     * a conflict/retry outcome instead of committing a mixed booking/seat
     * state.</p>
     */
    @Override
    @Transactional
    public BookingResponse cancel(Long id, Long requestingUserId) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id " + id));
        requireOwnership(booking, requestingUserId);

        BookingStatus previous = booking.getStatus();
        bookingStateMachine.transition(booking, BookingStatus.CANCELLED);

        Long eventId = booking.getItems().get(0).getSeat().getEvent().getId();
        booking.getItems().forEach(item -> item.getSeat().setStatus(SeatStatus.AVAILABLE));
        seatAvailabilityCacheEvictor.evict(eventId);

        Booking saved = bookingRepository.save(booking);
        // No explicit userId field: this runs on the HTTP request thread, so
        // the request's userId MDC entry is already on the line (an explicit
        // one would duplicate that key in the JSON).
        log.info("Booking {} status changed {} -> {}",
                value("bookingId", saved.getId()),
                value("fromStatus", previous),
                value("toStatus", BookingStatus.CANCELLED),
                kv("eventId", eventId),
                kv("event", BOOKING_STATUS_CHANGED));
        return bookingMapper.toResponse(saved);
    }

    /**
     * Thrown as Spring Security's own {@link AccessDeniedException} rather
     * than a hand-rolled one — see this class's own doc for why reusing it
     * is deliberate, not a shortcut.
     */
    private void requireOwnership(Booking booking, Long requestingUserId) {
        if (!booking.getUser().getId().equals(requestingUserId)) {
            throw new AccessDeniedException("You do not have access to booking " + booking.getId());
        }
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

}
