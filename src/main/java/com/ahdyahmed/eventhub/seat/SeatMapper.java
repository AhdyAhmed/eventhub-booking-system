package com.ahdyahmed.eventhub.seat;

import com.ahdyahmed.eventhub.event.Event;
import com.ahdyahmed.eventhub.seat.dto.SeatRequest;
import com.ahdyahmed.eventhub.seat.dto.SeatResponse;
import org.springframework.stereotype.Component;

@Component
public class SeatMapper {

    public Seat toEntity(SeatRequest request, Event event) {
        return Seat.builder()
                .event(event)
                .seatNumber(request.seatNumber())
                .section(request.section())
                .price(request.price())
                .status(SeatStatus.AVAILABLE)
                .build();
    }

    public SeatResponse toResponse(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getEvent().getId(),
                seat.getSeatNumber(),
                seat.getSection(),
                seat.getPrice(),
                seat.getStatus()
        );
    }

}
