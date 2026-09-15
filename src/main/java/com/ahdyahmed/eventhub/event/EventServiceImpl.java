package com.ahdyahmed.eventhub.event;

import com.ahdyahmed.eventhub.common.dto.PageResponse;
import com.ahdyahmed.eventhub.common.exception.ResourceNotFoundException;
import com.ahdyahmed.eventhub.event.dto.EventRequest;
import com.ahdyahmed.eventhub.event.dto.EventResponse;
import com.ahdyahmed.eventhub.event.dto.EventSearchCriteria;
import com.ahdyahmed.eventhub.venue.Venue;
import com.ahdyahmed.eventhub.venue.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final EventMapper eventMapper;

    @Override
    @Transactional
    // event-search is evicted wholesale (allEntries) rather than by key: a
    // new event can match an unbounded number of already-cached filter/page
    // combinations, so there's no single key to invalidate.
    @CacheEvict(cacheNames = "event-search", allEntries = true)
    public EventResponse create(EventRequest request) {
        Venue venue = findVenueOrThrow(request.venueId());
        Event saved = eventRepository.save(eventMapper.toEntity(request, venue));
        return eventMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "events", key = "#id")
    public EventResponse getById(Long id) {
        return eventMapper.toResponse(findEventOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    // Default key generation combines both arguments into one cache key:
    // EventSearchCriteria is a record (equals/hashCode for free) and
    // Spring Data's PageRequest implements them too, so two calls with the
    // same filters + page/sort land on the same key.
    @Cacheable(cacheNames = "event-search")
    public PageResponse<EventResponse> search(EventSearchCriteria criteria, Pageable pageable) {
        Specification<Event> spec = Specification
                .where(EventSpecifications.hasVenueId(criteria.venueId()))
                .and(EventSpecifications.hasCity(criteria.city()))
                .and(EventSpecifications.hasCategory(criteria.category()))
                .and(EventSpecifications.eventDateFrom(criteria.fromDate()))
                .and(EventSpecifications.eventDateTo(criteria.toDate()));

        Page<Event> page = eventRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(eventMapper::toResponse));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "events", key = "#id"),
            @CacheEvict(cacheNames = "event-search", allEntries = true)
    })
    public EventResponse update(Long id, EventRequest request) {
        Event event = findEventOrThrow(id);
        Venue venue = findVenueOrThrow(request.venueId());
        eventMapper.updateEntity(event, request, venue);
        return eventMapper.toResponse(event);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "events", key = "#id"),
            @CacheEvict(cacheNames = "event-search", allEntries = true)
    })
    public void delete(Long id) {
        eventRepository.delete(findEventOrThrow(id));
    }

    private Event findEventOrThrow(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id " + id));
    }

    private Venue findVenueOrThrow(Long venueId) {
        return venueRepository.findById(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id " + venueId));
    }

}
