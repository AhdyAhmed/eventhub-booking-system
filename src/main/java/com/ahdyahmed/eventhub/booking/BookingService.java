package com.ahdyahmed.eventhub.booking;

import com.ahdyahmed.eventhub.booking.dto.BookingRequest;
import com.ahdyahmed.eventhub.booking.dto.BookingResponse;

/**
 * Every method here takes the requesting user's id as an explicit
 * parameter, not something read off {@code SecurityContextHolder} inside
 * the implementation — {@code BookingController} resolves it once, from the
 * authenticated {@code UserPrincipal}, and passes it down. Keeping Spring
 * Security concerns out of this interface (and {@code BookingServiceImpl})
 * is what let every existing unit test keep constructing this service with
 * plain mocked repositories, no security test scaffolding required, even
 * after Day 16.
 */
public interface BookingService {

    BookingResponse create(Long userId, BookingRequest request);

    BookingResponse getById(Long id, Long requestingUserId);

    BookingResponse cancel(Long id, Long requestingUserId);

}
