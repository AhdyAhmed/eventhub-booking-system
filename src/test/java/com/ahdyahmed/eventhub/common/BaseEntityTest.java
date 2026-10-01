package com.ahdyahmed.eventhub.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.ahdyahmed.eventhub.user.User;
import com.ahdyahmed.eventhub.venue.Venue;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

    @Test
    void sameEntityTypeAndId_areEqualRegardlessOfMutableFields() {
        Venue first = Venue.builder().id(1L).name("Old Name").city("Cairo").capacity(100).build();
        Venue second = Venue.builder().id(1L).name("New Name").city("Giza").capacity(200).build();

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void differentEntityTypesWithSameNumericId_areNotEqual() {
        Venue venue = Venue.builder().id(1L).name("Arena").city("Cairo").capacity(100).build();
        User user = User.builder().id(1L).fullName("User").email("user@example.com")
                .passwordHash("hash").build();

        assertThat(venue).isNotEqualTo(user);
    }

    @Test
    void transientEntitiesWithoutIds_areNotEqual() {
        Venue first = Venue.builder().name("Arena").city("Cairo").capacity(100).build();
        Venue second = Venue.builder().name("Arena").city("Cairo").capacity(100).build();

        assertThat(first).isNotEqualTo(second);
    }
}
