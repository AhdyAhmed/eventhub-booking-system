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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * A registered user who can create bookings.
 *
 * <p>{@code passwordHash} arrived on Day 16 (see {@code
 * V4__add_user_password.sql}) alongside the auth module that's the only
 * thing allowed to set it — {@code AuthServiceImpl.register} is the one
 * place a {@code User} gets constructed with a real hash; nothing else in
 * this class or its mapper should ever see a raw password. Roles are still
 * out of scope: every authenticated user has exactly the same
 * permissions (see {@code SecurityConfig}'s own doc for why that's an
 * accepted simplification, not an oversight).</p>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(onlyExplicitlyIncluded = true)
public class User extends BaseEntity {

    @ToString.Include
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @ToString.Include
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // Never included in toString() (no @ToString.Include) and never
    // returned by UserMapper.toResponse - a bcrypt hash isn't a secret in
    // the same way a raw password is, but it still has no reason to appear
    // in a log line or an API response.
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // PERSIST/MERGE only — deleting a user should never cascade-delete their
    // booking history. Removal, if ever needed, is a deliberate separate
    // operation, not a side effect of this mapping.
    @OneToMany(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<Booking> bookings = new ArrayList<>();

}
