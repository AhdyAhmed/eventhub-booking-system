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
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A single seat's inventory row for one event.
 *
 * <p>NOTE: this deliberately does NOT have a {@code @Version} column yet.
 * Optimistic locking is added on Day 6 alongside the booking creation flow
 * that actually needs it — see the Roadmap in README.md. Adding it here
 * ahead of time would mean the commit that introduces concurrency handling
 * has nothing to show for it.</p>
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
@Builder
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

}
