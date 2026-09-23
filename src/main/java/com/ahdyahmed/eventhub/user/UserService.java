package com.ahdyahmed.eventhub.user;

import com.ahdyahmed.eventhub.user.dto.UserRequest;
import com.ahdyahmed.eventhub.user.dto.UserResponse;

/**
 * Deliberately minimal — just enough to create and look up a user so the
 * booking flow has someone to book on behalf of. This is a stand-in, not
 * real user management: no password, no roles, no update/delete. Day 16's
 * auth module is what actually owns registration; this will very likely be
 * revisited (or replaced outright) once that lands.
 */
public interface UserService {

    UserResponse create(UserRequest request);

    UserResponse getById(Long id);

}
