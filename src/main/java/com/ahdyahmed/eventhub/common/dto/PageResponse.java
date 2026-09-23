package com.ahdyahmed.eventhub.common.dto;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * A stable, framework-agnostic shape for paginated responses.
 *
 * <p>Spring Data's {@code Page<T>} is convenient internally but is not a
 * great thing to serialize directly: its JSON shape is verbose, tied to
 * Spring Data's version, and exposes internals (like {@code pageable} and
 * {@code sort} metadata objects) callers don't need. This wraps it down to
 * exactly what a client actually wants — same principle as not returning
 * JPA entities directly from a controller, applied to pagination.</p>
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                // Wrapped in a plain ArrayList rather than passed through as
                // whatever List implementation Page.map() happens to return.
                // A cache (Day 8's Redis cache-aside on event-search) whose
                // serializer embeds the runtime class name needs that name to
                // be something it can actually reconstruct - an immutable or
                // otherwise non-public List type can silently break that
                // round trip even though plain (non-cached) JSON responses
                // look identical either way.
                new ArrayList<>(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

}
