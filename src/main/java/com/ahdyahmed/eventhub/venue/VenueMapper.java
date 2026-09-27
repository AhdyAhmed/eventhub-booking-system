package com.ahdyahmed.eventhub.venue;

import com.ahdyahmed.eventhub.venue.dto.VenueRequest;
import com.ahdyahmed.eventhub.venue.dto.VenueResponse;
import org.springframework.stereotype.Component;

/**
 * Manual mapping between {@link Venue} and its DTOs.
 *
 * <p>Kept as plain hand-written methods rather than pulling in MapStruct —
 * at this size, a mapping library adds an annotation processor and a
 * generated-code layer to explain for close to zero benefit. Worth revisiting
 * if/when the DTO surface grows a lot.</p>
 */
@Component
public class VenueMapper {

    public Venue toEntity(VenueRequest request) {
        return Venue.builder()
                .name(request.name())
                .city(request.city())
                .address(request.address())
                .capacity(request.capacity())
                .build();
    }

    /**
     * Mutates a managed entity in place so JPA's dirty checking picks up the
     * change on flush, rather than constructing a new detached entity and
     * losing the original id/createdAt.
     */
    public void updateEntity(Venue venue, VenueRequest request) {
        venue.setName(request.name());
        venue.setCity(request.city());
        venue.setAddress(request.address());
        venue.setCapacity(request.capacity());
    }

    public VenueResponse toResponse(Venue venue) {
        return new VenueResponse(
                venue.getId(),
                venue.getName(),
                venue.getCity(),
                venue.getAddress(),
                venue.getCapacity(),
                venue.getCreatedAt(),
                venue.getUpdatedAt()
        );
    }

}
