package com.ahdyahmed.eventhub.user;

import com.ahdyahmed.eventhub.user.dto.UserResponse;
import org.springframework.stereotype.Component;

/**
 * {@code toEntity} left with Day 16's registration flow - {@code
 * AuthServiceImpl.register} builds a {@code User} directly, since it's the
 * one place that needs to set {@code passwordHash} and this mapper has no
 * business knowing about raw passwords or {@code PasswordEncoder}.
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getCreatedAt());
    }

}
