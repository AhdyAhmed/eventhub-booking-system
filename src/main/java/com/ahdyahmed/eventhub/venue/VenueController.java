package com.ahdyahmed.eventhub.venue;

import com.ahdyahmed.eventhub.config.OpenApiConfig;
import com.ahdyahmed.eventhub.venue.dto.VenueRequest;
import com.ahdyahmed.eventhub.venue.dto.VenueResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@Tag(name = "Venues", description = "Public venue browsing and authenticated venue management")
public class VenueController {

    private final VenueService venueService;

    @PostMapping
    @Operation(summary = "Create a venue", security = @SecurityRequirement(name = OpenApiConfig.BEARER_JWT))
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody VenueRequest request) {
        VenueResponse response = venueService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/venues/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a venue")
    public VenueResponse getById(@PathVariable Long id) {
        return venueService.getById(id);
    }

    @GetMapping
    @Operation(summary = "List venues")
    public List<VenueResponse> getAll() {
        return venueService.getAll();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a venue", security = @SecurityRequirement(name = OpenApiConfig.BEARER_JWT))
    public VenueResponse update(@PathVariable Long id, @Valid @RequestBody VenueRequest request) {
        return venueService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a venue", security = @SecurityRequirement(name = OpenApiConfig.BEARER_JWT))
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        venueService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
