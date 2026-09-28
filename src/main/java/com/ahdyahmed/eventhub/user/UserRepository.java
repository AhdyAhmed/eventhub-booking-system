package com.ahdyahmed.eventhub.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    // Day 16: how EventHubUserDetailsService and AuthServiceImpl's
    // duplicate-email pre-check both look a user up by the thing they
    // actually authenticate with - email, not id.
    Optional<User> findByEmail(String email);

}
