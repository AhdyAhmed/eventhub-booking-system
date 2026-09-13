package com.ahdyahmed.eventhub.seat;

import com.ahdyahmed.eventhub.seat.dto.SeatRequest;
import com.ahdyahmed.eventhub.seat.dto.SeatResponse;
import java.util.List;

public interface SeatService {

    SeatResponse create(Long eventId, SeatRequest request);

    List<SeatResponse> getByEvent(Long eventId, SeatStatus status);

}
