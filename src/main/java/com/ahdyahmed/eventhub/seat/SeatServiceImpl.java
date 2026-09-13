package com.ahdyahmed.eventhub.seat;

import com.ahdyahmed.eventhub.common.exception.ResourceNotFoundException;
import com.ahdyahmed.eventhub.event.Event;
import com.ahdyahmed.eventhub.event.EventRepository;
import com.ahdyahmed.eventhub.seat.dto.SeatRequest;
import com.ahdyahmed.eventhub.seat.dto.SeatResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final SeatMapper seatMapper;

    @Override
    @Transactional
    public SeatResponse create(Long eventId, SeatRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id " + eventId));
        Seat saved = seatRepository.save(seatMapper.toEntity(request, event));
        return seatMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeatResponse> getByEvent(Long eventId, SeatStatus status) {
        List<Seat> seats = (status == null)
                ? seatRepository.findByEventId(eventId)
                : seatRepository.findByEventIdAndStatus(eventId, status);
        return seats.stream().map(seatMapper::toResponse).toList();
    }

}
