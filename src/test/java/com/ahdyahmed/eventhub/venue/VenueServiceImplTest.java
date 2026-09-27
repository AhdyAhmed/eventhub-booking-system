package com.ahdyahmed.eventhub.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ahdyahmed.eventhub.common.exception.ResourceNotFoundException;
import com.ahdyahmed.eventhub.venue.dto.VenueRequest;
import com.ahdyahmed.eventhub.venue.dto.VenueResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Only {@link VenueRepository} is mocked here — it's the one collaborator
 * with a real side effect (the database). {@link VenueMapper} is plain,
 * dependency-free mapping logic, so it's used for real rather than mocked;
 * mocking it would just mean re-specifying its behavior with {@code when(...)}
 * instead of testing against what it actually does.
 */
@ExtendWith(MockitoExtension.class)
class VenueServiceImplTest {

    @Mock
    private VenueRepository venueRepository;

    private final VenueMapper venueMapper = new VenueMapper();

    private VenueServiceImpl venueService;

    @BeforeEach
    void setUp() {
        venueService = new VenueServiceImpl(venueRepository, venueMapper);
    }

    @Test
    void create_savesAndReturnsMappedResponse() {
        VenueRequest request = new VenueRequest("Cairo Arena", "Cairo", "Nasr City", 5000);
        Venue saved = Venue.builder()
                .id(1L)
                .name("Cairo Arena")
                .city("Cairo")
                .address("Nasr City")
                .capacity(5000)
                .build();
        when(venueRepository.save(any(Venue.class))).thenReturn(saved);

        VenueResponse response = venueService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Cairo Arena");
        assertThat(response.capacity()).isEqualTo(5000);
        verify(venueRepository).save(any(Venue.class));
    }

    @Test
    void getById_whenFound_returnsMappedResponse() {
        Venue venue = Venue.builder().id(7L).name("Hall A").city("Giza").capacity(300).build();
        when(venueRepository.findById(7L)).thenReturn(Optional.of(venue));

        VenueResponse response = venueService.getById(7L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.city()).isEqualTo("Giza");
    }

    @Test
    void getById_whenMissing_throwsResourceNotFoundException() {
        when(venueRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void update_whenFound_mutatesManagedEntityAndReturnsUpdatedResponse() {
        Venue existing = Venue.builder().id(1L).name("Old Name").city("Old City").capacity(100).build();
        when(venueRepository.findById(1L)).thenReturn(Optional.of(existing));
        VenueRequest request = new VenueRequest("New Name", "New City", "New Address", 200);

        VenueResponse response = venueService.update(1L, request);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.city()).isEqualTo("New City");
        assertThat(response.capacity()).isEqualTo(200);
        // update() should never call save() explicitly — it relies on the
        // managed entity being dirty-checked and flushed at transaction commit.
    }

    @Test
    void update_whenMissing_throwsResourceNotFoundException() {
        when(venueRepository.findById(42L)).thenReturn(Optional.empty());
        VenueRequest request = new VenueRequest("Name", "City", "Address", 10);

        assertThatThrownBy(() -> venueService.update(42L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_whenMissing_throwsResourceNotFoundException() {
        when(venueRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueService.delete(5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

}
