package com.ahdyahmed.eventhub.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ahdyahmed.eventhub.common.exception.ResourceNotFoundException;
import com.ahdyahmed.eventhub.event.dto.EventRequest;
import com.ahdyahmed.eventhub.event.dto.EventResponse;
import com.ahdyahmed.eventhub.event.dto.EventSearchCriteria;
import com.ahdyahmed.eventhub.venue.Venue;
import com.ahdyahmed.eventhub.venue.VenueRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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

    @Test
    @SuppressWarnings("unchecked")
    void search_withoutFromDate_defaultsToUpcomingEvents() {
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        Instant before = Instant.now();

        eventService.search(new EventSearchCriteria(null, null, null, null, null), Pageable.unpaged());

        Instant after = Instant.now();
        ArgumentCaptor<Specification<Event>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(eventRepository).findAll(specificationCaptor.capture(), eq(Pageable.unpaged()));

        Root<Event> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path<Instant> eventDatePath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.<Instant>get("eventDate")).thenReturn(eventDatePath);
        when(builder.greaterThanOrEqualTo(eq(eventDatePath), any(Instant.class))).thenReturn(predicate);

        specificationCaptor.getValue().toPredicate(root, query, builder);

        ArgumentCaptor<Instant> lowerBound = ArgumentCaptor.forClass(Instant.class);
        verify(builder).greaterThanOrEqualTo(eq(eventDatePath), lowerBound.capture());
        assertThat(lowerBound.getValue()).isBetween(before, after);
    }

}
