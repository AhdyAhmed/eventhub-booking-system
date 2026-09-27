package com.ahdyahmed.eventhub.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ahdyahmed.eventhub.common.exception.ResourceNotFoundException;
import com.ahdyahmed.eventhub.event.dto.EventRequest;
import com.ahdyahmed.eventhub.event.dto.EventResponse;
import com.ahdyahmed.eventhub.venue.Venue;
import com.ahdyahmed.eventhub.venue.VenueRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private VenueRepository venueRepository;

    private final EventMapper eventMapper = new EventMapper();

    private EventServiceImpl eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventServiceImpl(eventRepository, venueRepository, eventMapper);
    }

    @Test
    void create_whenVenueExists_savesEventAndReturnsResponseWithVenueSummary() {
        Venue venue = Venue.builder().id(1L).name("Cairo Arena").city("Cairo").capacity(5000).build();
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));

        Instant eventDate = Instant.now().plus(2, ChronoUnit.HOURS);
        EventRequest request = new EventRequest(1L, "Launch Night", "Opening event", "CONCERT", eventDate);

        Event saved = Event.builder()
                .id(10L)
                .venue(venue)
                .name("Launch Night")
                .description("Opening event")
                .category("CONCERT")
                .eventDate(eventDate)
                .build();
        when(eventRepository.save(any(Event.class))).thenReturn(saved);

        EventResponse response = eventService.create(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Launch Night");
        assertThat(response.venue().id()).isEqualTo(1L);
        assertThat(response.venue().city()).isEqualTo("Cairo");
    }

    @Test
    void create_whenVenueMissing_throwsAndNeverTouchesEventRepository() {
        when(venueRepository.findById(99L)).thenReturn(Optional.empty());
        EventRequest request = new EventRequest(
                99L, "Launch Night", "desc", "CONCERT", Instant.now().plus(2, ChronoUnit.HOURS)
        );

        assertThatThrownBy(() -> eventService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Venue");

        // If the venue lookup fails, the event repository should never be
        // touched — there's nothing valid to save.
        verifyNoInteractions(eventRepository);
    }

    @Test
    void getById_whenFound_returnsMappedResponse() {
        Venue venue = Venue.builder().id(2L).name("Hall B").city("Giza").capacity(200).build();
        Event event = Event.builder()
                .id(20L)
                .venue(venue)
                .name("Matinee")
                .category("THEATER")
                .eventDate(Instant.now().plus(3, ChronoUnit.HOURS))
                .build();
        when(eventRepository.findById(20L)).thenReturn(Optional.of(event));

        EventResponse response = eventService.getById(20L);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.category()).isEqualTo("THEATER");
    }

    @Test
    void getById_whenMissing_throwsResourceNotFoundException() {
        when(eventRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

}
