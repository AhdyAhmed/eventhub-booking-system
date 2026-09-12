package com.ahdyahmed.eventhub.common.dto;

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
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

}
