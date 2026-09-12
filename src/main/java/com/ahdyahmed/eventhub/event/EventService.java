package com.ahdyahmed.eventhub.event;

import com.ahdyahmed.eventhub.common.dto.PageResponse;
import com.ahdyahmed.eventhub.event.dto.EventRequest;
import com.ahdyahmed.eventhub.event.dto.EventResponse;
import com.ahdyahmed.eventhub.event.dto.EventSearchCriteria;
import org.springframework.data.domain.Pageable;

public interface EventService {

    EventResponse create(EventRequest request);

    EventResponse getById(Long id);

    PageResponse<EventResponse> search(EventSearchCriteria criteria, Pageable pageable);

    EventResponse update(Long id, EventRequest request);

    void delete(Long id);

}
