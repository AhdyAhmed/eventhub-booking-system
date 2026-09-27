package com.ahdyahmed.eventhub.seat;

import com.ahdyahmed.eventhub.common.BaseEntity;
import com.ahdyahmed.eventhub.event.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * A single seat's inventory row for one event.
 *
 * <p>Carries a {@code @Version} column (added Day 6, see {@code
 * V3__seat_optimistic_locking.sql}) so two concurrent booking attempts on the
 * same seat can't both succeed. Hibernate includes {@code version} in the
 * {@code WHERE} clause of every {@code UPDATE} against this row; if a second
 * transaction already bumped it, the update matches zero rows and Hibernate
 * raises an optimistic lock failure instead of silently overwriting someone
 * else's reservation. {@code BookingServiceImpl} catches that failure and
 * turns it into a {@code SeatUnavailableException} (409) — see Day 7 for the
 * test that actually proves this under real concurrency.</p>
 */
@Entity
@Table(
        name = "seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "seat_number"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(callSuper = true)
public class Seat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ToString.Include
    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;

    @ToString.Include
    @Column(length = 50)
    private String section;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Version
    @Column(nullable = false)
    private Long version;

}
