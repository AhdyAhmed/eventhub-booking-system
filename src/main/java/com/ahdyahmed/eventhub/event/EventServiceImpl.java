package com.ahdyahmed.eventhub.event;

import com.ahdyahmed.eventhub.common.exception.ResourceNotFoundException;
import com.ahdyahmed.eventhub.event.dto.EventRequest;
import com.ahdyahmed.eventhub.event.dto.EventResponse;
import com.ahdyahmed.eventhub.venue.Venue;
import com.ahdyahmed.eventhub.venue.VenueRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
    public EventResponse create(EventRequest request) {
        Venue venue = findVenueOrThrow(request.venueId());
        Event saved = eventRepository.save(eventMapper.toEntity(request, venue));
        return eventMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getById(Long id) {
        return eventMapper.toResponse(findEventOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventResponse> getAll() {
        return eventRepository.findAll().stream()
                .map(eventMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public EventResponse update(Long id, EventRequest request) {
        Event event = findEventOrThrow(id);
        Venue venue = findVenueOrThrow(request.venueId());
        eventMapper.updateEntity(event, request, venue);
        return eventMapper.toResponse(event);
    }

    @Override
    @Transactional
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
