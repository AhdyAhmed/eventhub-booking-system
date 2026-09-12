package com.ahdyahmed.eventhub.user;

import com.ahdyahmed.eventhub.booking.Booking;
import com.ahdyahmed.eventhub.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * A registered user who can create bookings. Auth (password hash, roles,
 * JWT) is deliberately out of scope here and comes from the Project 2 auth
 * module when it's wired in on Day 16 — this entity only carries what the
 * booking domain itself needs.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    @ToString.Include
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @ToString.Include
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // PERSIST/MERGE only — deleting a user should never cascade-delete their
    // booking history. Removal, if ever needed, is a deliberate separate
    // operation, not a side effect of this mapping.
    @OneToMany(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<Booking> bookings = new ArrayList<>();

}
