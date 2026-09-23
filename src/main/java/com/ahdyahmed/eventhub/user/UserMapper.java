package com.ahdyahmed.eventhub.user;

import com.ahdyahmed.eventhub.user.dto.UserRequest;
import com.ahdyahmed.eventhub.user.dto.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(UserRequest request) {
        return User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .build();
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getCreatedAt());
    }

}
