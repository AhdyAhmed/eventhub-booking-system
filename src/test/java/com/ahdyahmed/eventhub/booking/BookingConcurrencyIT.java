package com.ahdyahmed.eventhub.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.ahdyahmed.eventhub.event.Event;
import com.ahdyahmed.eventhub.event.EventRepository;
import com.ahdyahmed.eventhub.seat.Seat;
import com.ahdyahmed.eventhub.seat.SeatRepository;
import com.ahdyahmed.eventhub.seat.SeatStatus;
import com.ahdyahmed.eventhub.user.User;
import com.ahdyahmed.eventhub.user.UserRepository;
import com.ahdyahmed.eventhub.venue.Venue;
import com.ahdyahmed.eventhub.venue.VenueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * This is the test the whole project's thesis rests on. Everything in
 * {@code BookingServiceImpl} is written to survive concurrent booking
 * attempts on the same seat — this test is what actually proves it, by
 * firing real simultaneous HTTP requests (not mocked calls, not sequential
 * calls dressed up as concurrent) at a live app instance backed by a real
 * Postgres container, and checking that exactly one wins.
 *
 * <p>Container lifecycle is managed explicitly via {@code @BeforeAll}/
 * {@code @AfterAll} rather than the {@code @Testcontainers}/{@code @Container}
 * annotation pair used in {@code EventhubApplicationTests}. Both are
 * legitimate patterns, but the annotation-based one relies on JUnit
 * reflectively scanning for a static field at a specific point in the
 * extension lifecycle — which turned out to be the exact thing that failed
 * in practice here ({@code ExtensionConfigurationException: Container
 * postgres needs to be initialized}, thrown before the container ever
 * attempted to start). Calling {@code .start()}/{@code .stop()} directly
 * removes that indirection entirely: there's no scanning step that can
 * fail, just two explicit method calls.</p>
 *
 * <p>Why {@link TestRestTemplate} over {@code MockMvc}: {@code MockMvc}
 * dispatches requests through a single thread by design, which would make
 * "concurrent" requests concurrent in name only. A real embedded server on
 * a random port, hit by real threads, is the only way to exercise the
 * actual transaction boundaries and let the database's row-level behavior
 * decide who wins — which is the entire thing being tested.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingConcurrencyIT {

    private static final int CONCURRENT_ATTEMPTS = 10;

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("eventhub_db")
            .withUsername("eventhub_user")
            .withPassword("eventhub_pass");

    @BeforeAll
    static void startContainer() {
        postgres.start();
    }

    @AfterAll
    static void stopContainer() {
        postgres.stop();
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private UserRepository userRepository;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private Long contestedSeatId;
    private Long userId;

    @BeforeEach
    void seedContestedSeat() {
        Venue venue = venueRepository.save(
                Venue.builder().name("Concurrency Arena").city("Cairo").capacity(1000).build()
        );
        Event event = eventRepository.save(
                Event.builder()
                        .venue(venue)
                        .name("Race Night")
                        .category("CONCERT")
                        .eventDate(Instant.now().plus(2, ChronoUnit.HOURS))
                        .build()
        );
        Seat seat = seatRepository.save(
                Seat.builder()
                        .event(event)
                        .seatNumber("A1")
                        .price(new BigDecimal("50.00"))
                        .status(SeatStatus.AVAILABLE)
                        .build()
        );
        contestedSeatId = seat.getId();

        User user = userRepository.save(
                User.builder().fullName("Race Tester").email("race-tester@example.com").build()
        );
        userId = user.getId();
    }

    @Test
    void tenConcurrentBookingAttempts_exactlyOneSucceedsForTheSameSeat() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS);
        // The ready/start latch pair is what actually makes this concurrent
        // rather than "fast sequential": every thread blocks on startLatch
        // until all of them have reached that point, then they're released
        // in the same instant, maximizing real contention on the row.
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_ATTEMPTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_ATTEMPTS);
        List<HttpStatusCode> results = Collections.synchronizedList(new ArrayList<>());

        String url = "http://localhost:" + port + "/api/v1/bookings";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String requestBody = "{\"userId\":" + userId + ",\"seatIds\":[" + contestedSeatId + "]}";
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        for (int i = 0; i < CONCURRENT_ATTEMPTS; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
                    results.add(response.getStatusCode());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("all %d requests should complete within the timeout", CONCURRENT_ATTEMPTS).isTrue();
        assertThat(results).hasSize(CONCURRENT_ATTEMPTS);

        long successCount = results.stream().filter(HttpStatusCode::is2xxSuccessful).count();
        long conflictCount = results.stream().filter(status -> status.value() == 409).count();

        assertThat(successCount)
                .as("exactly one of %d simultaneous attempts should win the seat", CONCURRENT_ATTEMPTS)
                .isEqualTo(1);
        assertThat(conflictCount)
                .as("every losing attempt should come back as 409, not a 500 or a hang")
                .isEqualTo(CONCURRENT_ATTEMPTS - 1);

        Seat reloaded = seatRepository.findById(contestedSeatId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SeatStatus.RESERVED);
        // Exactly one successful UPDATE happened: version moved from 0 to 1,
        // not from 0 to 10. If this were ever 10, it would mean every
        // request "succeeded" independently — the exact bug this whole
        // feature exists to prevent.
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

}

