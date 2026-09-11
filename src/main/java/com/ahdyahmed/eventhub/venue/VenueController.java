package com.ahdyahmed.eventhub.venue;

import com.ahdyahmed.eventhub.venue.dto.VenueRequest;
import com.ahdyahmed.eventhub.venue.dto.VenueResponse;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @PostMapping
    public ResponseEntity<VenueResponse> create(@RequestBody VenueRequest request) {
        VenueResponse response = venueService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/venues/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public VenueResponse getById(@PathVariable Long id) {
        return venueService.getById(id);
    }

    @GetMapping
    public List<VenueResponse> getAll() {
        return venueService.getAll();
    }

    @PutMapping("/{id}")
    public VenueResponse update(@PathVariable Long id, @RequestBody VenueRequest request) {
        return venueService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        venueService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
