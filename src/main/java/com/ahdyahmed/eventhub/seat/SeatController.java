package com.ahdyahmed.eventhub.seat;

import com.ahdyahmed.eventhub.seat.dto.SeatRequest;
import com.ahdyahmed.eventhub.seat.dto.SeatResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events/{eventId}/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @PostMapping
    public ResponseEntity<SeatResponse> create(@PathVariable Long eventId, @Valid @RequestBody SeatRequest request) {
        SeatResponse response = seatService.create(eventId, request);
        URI location = URI.create("/api/v1/events/" + eventId + "/seats/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<SeatResponse> getByEvent(@PathVariable Long eventId,
                                          @RequestParam(required = false) SeatStatus status) {
        return seatService.getByEvent(eventId, status);
    }

}
