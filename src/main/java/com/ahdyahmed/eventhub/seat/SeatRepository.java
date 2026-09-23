package com.ahdyahmed.eventhub.seat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventId(Long eventId);

    List<Seat> findByEventIdAndStatus(Long eventId, SeatStatus status);

}
