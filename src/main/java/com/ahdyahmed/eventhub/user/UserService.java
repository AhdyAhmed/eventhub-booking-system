package com.ahdyahmed.eventhub.user;

import com.ahdyahmed.eventhub.user.dto.UserResponse;

/**
 * Deliberately minimal: just a lookup. Creating a {@code User} moved to
 * {@code AuthServiceImpl.register} on Day 16 - exactly the "revisited or
 * replaced outright" this interface's own doc predicted back when it still
 * owned {@code create()} - since a user can't exist in this app anymore
 * without a password, and only the auth module is meant to ever set one.
 */
public interface UserService {

    UserResponse getById(Long id);

}
