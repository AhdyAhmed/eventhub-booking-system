package com.ahdyahmed.eventhub.event;

import com.ahdyahmed.eventhub.event.dto.EventRequest;
import com.ahdyahmed.eventhub.event.dto.EventResponse;
import java.util.List;

public interface EventService {

    EventResponse create(EventRequest request);

    EventResponse getById(Long id);

    List<EventResponse> getAll();

    EventResponse update(Long id, EventRequest request);

    void delete(Long id);

}
