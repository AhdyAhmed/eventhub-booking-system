package com.ahdyahmed.eventhub.user;

import com.ahdyahmed.eventhub.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code create} left this controller on Day 16 - {@code POST
 * /api/v1/auth/register} is where a {@code User} gets created now, since
 * that's the one flow that can actually set a password. What's left here is
 * a plain lookup, reachable by any authenticated user (not owner-restricted
 * the way booking endpoints are - the roadmap's ownership requirement was
 * specifically about bookings, and a user profile isn't sensitive enough
 * here to need the same restriction).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        return userService.getById(id);
    }

}
