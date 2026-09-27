package com.ahdyahmed.eventhub.venue;

import com.ahdyahmed.eventhub.venue.dto.VenueRequest;
import com.ahdyahmed.eventhub.venue.dto.VenueResponse;
import java.util.List;

public interface VenueService {

    VenueResponse create(VenueRequest request);

    VenueResponse getById(Long id);

    List<VenueResponse> getAll();

    VenueResponse update(Long id, VenueRequest request);

    void delete(Long id);

}
