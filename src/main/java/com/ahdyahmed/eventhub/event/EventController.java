package com.ahdyahmed.eventhub.event;

import com.ahdyahmed.eventhub.common.dto.PageResponse;
import com.ahdyahmed.eventhub.event.dto.EventRequest;
import com.ahdyahmed.eventhub.event.dto.EventResponse;
import com.ahdyahmed.eventhub.event.dto.EventSearchCriteria;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request) {
        EventResponse response = eventService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/events/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public EventResponse getById(@PathVariable Long id) {
        return eventService.getById(id);
    }

    /**
     * Paginated, sortable, dynamically filterable event search. Every filter
     * param is optional — omit all of them to page through everything.
     * Sortable via the standard {@code ?sort=eventDate,desc} (repeatable)
     * query param, e.g. {@code ?sort=eventDate,asc&sort=category,asc}.
     */
    @GetMapping
    public PageResponse<EventResponse> search(
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @PageableDefault(size = 20, sort = "eventDate") Pageable pageable
    ) {
        EventSearchCriteria criteria = new EventSearchCriteria(venueId, city, category, fromDate, toDate);
        return eventService.search(criteria, pageable);
    }

    @PutMapping("/{id}")
    public EventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
        return eventService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        eventService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
