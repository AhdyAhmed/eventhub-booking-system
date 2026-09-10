package com.ahdyahmed.eventhub.booking;

import com.ahdyahmed.eventhub.common.BaseEntity;
import com.ahdyahmed.eventhub.seat.Seat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A single seat within a booking, with the price captured at the time of
 * booking (so later price changes on the seat/event never retroactively
 * change what the customer already paid).
 *
 * <p>There is intentionally no DB-level uniqueness on {@code seat_id} here:
 * a seat can legitimately appear in more than one historical booking if an
 * earlier booking was cancelled. Preventing a seat from being double-booked
 * *while active* is a concurrency-control problem, not a static schema
 * constraint — that's exactly what Day 6's optimistic locking solves.</p>
 */
@Entity
@Table(name = "booking_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(callSuper = true)
public class BookingItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ToString.Include
    @Column(name = "price_at_booking", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtBooking;

}
