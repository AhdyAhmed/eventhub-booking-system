package com.ahdyahmed.eventhub.event;

import jakarta.persistence.criteria.Join;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/**
 * One small {@link Specification} per filterable field. Spring Data's
 * {@code Specification.where(...).and(...)} treats a {@code null}
 * specification as "no-op", so {@link EventServiceImpl} can chain every one
 * of these unconditionally and only the non-null criteria actually filter
 * anything — no manual if/else branching to build the query.
 */
final class EventSpecifications {

    private EventSpecifications() {
    }

    static Specification<Event> hasVenueId(Long venueId) {
        if (venueId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("venue").get("id"), venueId);
    }

    static Specification<Event> hasCity(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            Join<Object, Object> venue = root.join("venue");
            return cb.equal(cb.lower(venue.get("city")), city.toLowerCase());
        };
    }

    static Specification<Event> hasCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(cb.lower(root.get("category")), category.toLowerCase());
    }

    static Specification<Event> eventDateFrom(Instant fromDate) {
        if (fromDate == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("eventDate"), fromDate);
    }

    static Specification<Event> eventDateTo(Instant toDate) {
        if (toDate == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("eventDate"), toDate);
    }

}
