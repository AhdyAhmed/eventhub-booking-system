package com.ahdyahmed.eventhub.booking;

import com.ahdyahmed.eventhub.auth.UserPrincipal;
import com.ahdyahmed.eventhub.booking.dto.BookingRequest;
import com.ahdyahmed.eventhub.booking.dto.BookingResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every method here needs an authenticated caller — {@code SecurityConfig}
 * doesn't carve out any exception for {@code /api/v1/bookings/**}, unlike
 * the public {@code GET} browsing endpoints on events and venues. {@code
 * @AuthenticationPrincipal UserPrincipal} resolves to whatever {@code
 * JwtAuthenticationFilter} put in the {@code SecurityContext} for this
 * request; by the time a request reaches this class, that's guaranteed to
 * be non-null, since an unauthenticated request never gets past {@code
 * SecurityConfig}'s filter chain to reach here at all.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                                   @Valid @RequestBody BookingRequest request) {
        BookingResponse response = bookingService.create(principal.getId(), request);
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public BookingResponse getById(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return bookingService.getById(id, principal.getId());
    }

    /**
     * The "cancel" half of Day 16's "users can only view/cancel their own
     * bookings" — a {@code POST}, not a {@code DELETE}, since cancelling a
     * booking doesn't delete the row (it transitions its status; the
     * booking's history stays queryable) the way {@code DELETE} implies for
     * a resource.
     */
    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return bookingService.cancel(id, principal.getId());
    }

}
