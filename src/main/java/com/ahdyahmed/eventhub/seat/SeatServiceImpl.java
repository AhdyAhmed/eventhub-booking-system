package com.ahdyahmed.eventhub.seat;

import com.ahdyahmed.eventhub.common.exception.ResourceNotFoundException;
import com.ahdyahmed.eventhub.event.Event;
import com.ahdyahmed.eventhub.event.EventRepository;
import com.ahdyahmed.eventhub.seat.dto.SeatRequest;
import com.ahdyahmed.eventhub.seat.dto.SeatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final SeatMapper seatMapper;

    @Override
    @Transactional
    // A newly-added seat should show up in the very next GET, not after the
    // TTL expires, so every cached listing for this event is dropped -
    // there's no single (eventId, status) key to target since the new seat
    // is a member of the unfiltered list and whichever status list it starts
    // in (AVAILABLE, always, right now).
    @CacheEvict(cacheNames = "seat-availability", allEntries = true)
    public SeatResponse create(Long eventId, SeatRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id " + eventId));
        Seat saved = seatRepository.save(seatMapper.toEntity(request, event));
        return seatMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    // Freshness after a booking (or a payment resolving one, as of Day 14)
    // is handled by SeatAvailabilityCacheEvictor, called from
    // BookingServiceImpl and PaymentProcessedListener - not here, since
    // this class doesn't know which seats a booking touched or which event
    // they belong to. Flagged as an open gap through Day 8, closed Day 9.
    @Cacheable(cacheNames = "seat-availability", key = "#eventId + '-' + #status")
    public List<SeatResponse> getByEvent(Long eventId, SeatStatus status) {
        List<Seat> seats = (status == null)
                ? seatRepository.findByEventId(eventId)
                : seatRepository.findByEventIdAndStatus(eventId, status);
        // .toList() (not Collectors.toList()) returns an immutable JDK-internal
        // list type (java.util.ImmutableCollections$ListN). GenericJackson2JsonRedisSerializer's
        // default typing writes that exact runtime class name into Redis as
        // the type id, then can't reconstruct it on the way back out - the
        // "Cache GET failed ... Unexpected token (START_OBJECT), expected
        // VALUE_STRING" error this caused. A plain ArrayList round-trips
        // cleanly through Jackson's polymorphic typing.
        return seats.stream().map(seatMapper::toResponse).collect(Collectors.toCollection(ArrayList::new));
    }

}
