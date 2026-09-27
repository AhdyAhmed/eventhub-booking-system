package com.ahdyahmed.eventhub.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ahdyahmed.eventhub.booking.BookingService;
import com.ahdyahmed.eventhub.booking.dto.BookingRequest;
import com.ahdyahmed.eventhub.event.EventRepository;
import com.ahdyahmed.eventhub.event.EventService;
import com.ahdyahmed.eventhub.event.dto.EventRequest;
import com.ahdyahmed.eventhub.event.dto.EventResponse;
import com.ahdyahmed.eventhub.seat.SeatRepository;
import com.ahdyahmed.eventhub.seat.SeatService;
import com.ahdyahmed.eventhub.seat.SeatStatus;
import com.ahdyahmed.eventhub.seat.dto.SeatRequest;
import com.ahdyahmed.eventhub.seat.dto.SeatResponse;
import com.ahdyahmed.eventhub.user.User;
import com.ahdyahmed.eventhub.user.UserRepository;
import com.ahdyahmed.eventhub.venue.Venue;
import com.ahdyahmed.eventhub.venue.VenueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Day 8 added Redis caching; Day 9 closed the booking-eviction gap. Neither
 * was ever proven against a real Redis in the test suite — every claim
 * about hit/miss behavior and invalidation up to this point rested on
 * manual {@code curl} + {@code redis-cli} sessions (which is exactly how
 * the three real bugs Day 8 shipped with were actually found and fixed).
 * This class is that missing coverage: a real Postgres container and a
 * real Redis container, wired into an actual Spring context, so a broken
 * cache key, a broken evict, or a broken serializer fails a test instead of
 * failing silently in front of a person running curl commands by hand.
 *
 * <p>Container lifecycle follows {@link
 * com.ahdyahmed.eventhub.booking.BookingConcurrencyIT}'s pattern — explicit
 * {@code @BeforeAll}/{@code @AfterAll} rather than {@code @Testcontainers}/
 * {@code @Container} — for the same reason established there: it sidesteps
 * the JUnit field-scanning failure mode entirely rather than relying on it
 * to keep working.</p>
 *
 * <p>Hit/miss is proven with {@code @SpyBean} on the JPA repositories, not
 * by inspecting Redis directly: the thing that actually matters to a caller
 * is "did this request reach the database a second time," and counting
 * repository invocations proves that directly. Invalidation is proven by
 * calling the read again and asserting it reflects the write, which is a
 * stronger claim than "the key is gone from Redis" — it's "the cache
 * couldn't have made this response stale even if it tried."</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RedisCacheIT {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("eventhub_db")
            .withUsername("eventhub_user")
            .withPassword("eventhub_pass");

    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @BeforeAll
    static void startContainers() {
        postgres.start();
        redis.start();
    }

    @AfterAll
    static void stopContainers() {
        redis.stop();
        postgres.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private EventService eventService;

    @Autowired
    private SeatService seatService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private UserRepository userRepository;

    @SpyBean
    private EventRepository eventRepository;

    @SpyBean
    private SeatRepository seatRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetCachesAndSpies() {
        // Every cache this app defines, cleared before each test so one
        // test's cached data can't quietly make a later test pass (or
        // fail) for the wrong reason.
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        // @SpyBean wraps a singleton bean shared by the whole Spring
        // context, so invocation counts persist across test methods unless
        // explicitly reset - without this, times(1) in the second test to
        // run would actually mean "once more than whatever the previous
        // test already called it."
        clearInvocations(eventRepository, seatRepository);
    }

    private Venue seedVenue() {
        return venueRepository.save(Venue.builder().name("Cache Test Arena").city("Cairo").capacity(500).build());
    }

    @Test
    void getById_secondCallWithinTtlHitsCacheNotDatabase() {
        Venue venue = seedVenue();
        EventResponse created = eventService.create(new EventRequest(
                venue.getId(), "Launch Night", "desc", "CONCERT", Instant.now().plus(2, ChronoUnit.HOURS)));
        clearInvocations(eventRepository); // the create() call above also hits the repository; not what's under test

        EventResponse first = eventService.getById(created.id());
        EventResponse second = eventService.getById(created.id());

        assertThat(first).isEqualTo(second);
        verify(eventRepository, times(1)).findById(created.id());
    }

    @Test
    void update_evictsEventsCache_nextGetReflectsTheChangeImmediately() {
        Venue venue = seedVenue();
        EventResponse created = eventService.create(new EventRequest(
                venue.getId(), "Launch Night", "desc", "CONCERT", Instant.now().plus(2, ChronoUnit.HOURS)));

        // Populate the cache with the pre-update name.
        eventService.getById(created.id());
        assertThat(cacheManager.getCache("events").get(created.id())).isNotNull();

        eventService.update(created.id(), new EventRequest(
                venue.getId(), "Launch Night (Rescheduled)", "desc", "CONCERT",
                Instant.now().plus(3, ChronoUnit.HOURS)));

        // The whole claim: no TTL wait needed. If eviction didn't fire,
        // this would still return the stale cached name.
        assertThat(cacheManager.getCache("events").get(created.id())).isNull();
        EventResponse afterUpdate = eventService.getById(created.id());
        assertThat(afterUpdate.name()).isEqualTo("Launch Night (Rescheduled)");
    }

    @Test
    void getByEvent_secondCallWithinTtlHitsCacheNotDatabase() {
        Venue venue = seedVenue();
        EventResponse event = eventService.create(new EventRequest(
                venue.getId(), "Launch Night", "desc", "CONCERT", Instant.now().plus(2, ChronoUnit.HOURS)));
        seatService.create(event.id(), new SeatRequest("A1", "Floor",
                new BigDecimal("50.00")));
        clearInvocations(seatRepository);

        List<SeatResponse> first = seatService.getByEvent(event.id(), null);
        List<SeatResponse> second = seatService.getByEvent(event.id(), null);

        assertThat(first).isEqualTo(second);
        verify(seatRepository, times(1)).findByEventId(event.id());
    }

    @Test
    void booking_evictsSeatAvailabilityCache_seatShowsReservedWithoutWaitingForTtl() {
        Venue venue = seedVenue();
        EventResponse event = eventService.create(new EventRequest(
                venue.getId(), "Launch Night", "desc", "CONCERT", Instant.now().plus(2, ChronoUnit.HOURS)));
        SeatResponse seat = seatService.create(event.id(), new SeatRequest(
                "A1", "Floor", new BigDecimal("50.00")));
        User user = userRepository.save(User.builder().fullName("Cache Tester").email("cache-test@example.com")
                .build());

        // Cache the AVAILABLE listing - this is the read Day 9's fix
        // guarantees won't go stale after the booking below.
        List<SeatResponse> beforeBooking = seatService.getByEvent(event.id(), null);
        assertThat(beforeBooking).extracting(SeatResponse::status).containsExactly(SeatStatus.AVAILABLE);
        assertThat(cacheManager.getCache("seat-availability").get(event.id() + "-null")).isNotNull();

        bookingService.create(new BookingRequest(user.getId(), List.of(seat.id())));

        assertThat(cacheManager.getCache("seat-availability").get(event.id() + "-null")).isNull();
        List<SeatResponse> afterBooking = seatService.getByEvent(event.id(), null);
        assertThat(afterBooking).extracting(SeatResponse::status).containsExactly(SeatStatus.RESERVED);
    }

}
