package com.ahdyahmed.eventhub.event;

import com.ahdyahmed.eventhub.event.dto.EventRequest;
import com.ahdyahmed.eventhub.event.dto.EventResponse;
import com.ahdyahmed.eventhub.event.dto.VenueSummary;
import com.ahdyahmed.eventhub.venue.Venue;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    public Event toEntity(EventRequest request, Venue venue) {
        return Event.builder()
                .venue(venue)
                .name(request.name())
                .description(request.description())
                .category(request.category())
                .eventDate(request.eventDate())
                .build();
    }

    public void updateEntity(Event event, EventRequest request, Venue venue) {
        event.setVenue(venue);
        event.setName(request.name());
        event.setDescription(request.description());
        event.setCategory(request.category());
        event.setEventDate(request.eventDate());
    }

    public EventResponse toResponse(Event event) {
        return new EventResponse(
                event.getId(),
                toVenueSummary(event.getVenue()),
                event.getName(),
                event.getDescription(),
                event.getCategory(),
                event.getEventDate(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    private VenueSummary toVenueSummary(Venue venue) {
        return new VenueSummary(venue.getId(), venue.getName(), venue.getCity());
    }

}
