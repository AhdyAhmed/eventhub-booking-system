package com.ahdyahmed.eventhub.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.ahdyahmed.eventhub.booking.Booking;
import com.ahdyahmed.eventhub.booking.BookingRepository;
import com.ahdyahmed.eventhub.booking.BookingService;
import com.ahdyahmed.eventhub.booking.BookingStatus;
import com.ahdyahmed.eventhub.booking.dto.BookingRequest;
import com.ahdyahmed.eventhub.booking.dto.BookingResponse;
import com.ahdyahmed.eventhub.booking.event.BookingConfirmedEvent;
import com.ahdyahmed.eventhub.config.KafkaTopicConfig;
import com.ahdyahmed.eventhub.event.Event;
import com.ahdyahmed.eventhub.event.EventRepository;
import com.ahdyahmed.eventhub.notification.NotificationListener;
import com.ahdyahmed.eventhub.seat.Seat;
import com.ahdyahmed.eventhub.seat.SeatRepository;
import com.ahdyahmed.eventhub.seat.SeatStatus;
import com.ahdyahmed.eventhub.user.User;
import com.ahdyahmed.eventhub.user.UserRepository;
import com.ahdyahmed.eventhub.venue.Venue;
import com.ahdyahmed.eventhub.venue.VenueRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The Day 15 test the roadmap names directly: "integration tests for the
 * full event chain (booking → payment event → notification event)."
 * Everything up to Day 14 proved each link of that chain in isolation, with
 * its neighbors mocked out — {@code BookingServiceImplTest} mocks the
 * repositories, {@code PaymentProcessedListenerTest} mocks the booking
 * repository, {@code PaymentConsumerTest} mocks the {@code KafkaTemplate}.
 * None of them prove the chain actually connects end to end against a real
 * broker. This class is that proof, plus the other half of Day 15's scope —
 * a forced failure that gets retried and then dead-lettered instead of
 * silently disappearing.
 *
 * <p>Container lifecycle follows {@link
 * com.ahdyahmed.eventhub.booking.BookingConcurrencyIT}'s established
 * explicit {@code @BeforeAll}/{@code @AfterAll} pattern rather than {@code
 * @Testcontainers}/{@code @Container}, for the same reason documented
 * there. Postgres and Kafka both need to already be running before {@code
 * @DynamicPropertySource} runs and the Spring context refreshes against
 * them.</p>
 *
 * <p>Lives in its own {@code integration} package, not under {@code
 * booking}, {@code payment}, or {@code notification} — this test's entire
 * point is that it exercises all three together, and filing it under any
 * single one of those packages would misrepresent it as belonging more to
 * that slice than to the others.</p>
 *
 * <p>No {@code webEnvironment} needed: the flow under test starts from
 * {@link BookingService#create}, not an HTTP request — {@code
 * BookingConcurrencyIT} already proves the controller layer sits correctly
 * on top of that method, so re-proving it here would just add an HTTP hop
 * with nothing new to verify.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EventChainIT {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("eventhub_db")
            .withUsername("eventhub_user")
            .withPassword("eventhub_pass");

    // Same image docker-compose.yml's kafka service runs (apache/kafka:3.8.0)
    // - testcontainers' KafkaContainer recognizes this as a native-KRaft
    // image and configures it accordingly, so the test broker runs in the
    // same mode Day 11 chose for the real stack rather than falling back to
    // a Zookeeper-based setup that would behave differently under the hood.
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @BeforeAll
    static void startContainers() {
        postgres.start();
        kafka.start();
    }

    @AfterAll
    static void stopContainers() {
        kafka.stop();
        postgres.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private BookingService bookingService;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private UserRepository userRepository;

    // A spy, not a mock: two of the three tests below want the real
    // onBookingConfirmed behavior (a log line) to keep running so the
    // "did notification receive this" assertion is meaningful, and only
    // stub it to throw in the one test that specifically needs a failure to
    // retry.
    @SpyBean
    private NotificationListener notificationListener;

    private Long userId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(
                User.builder().fullName("Chain Tester").email("chain-tester@example.com").build());
        userId = user.getId();
        // Each test method shares one Spring context (and so one spy) -
        // clearing recorded invocations, not the spy's real behavior,
        // keeps every test's verify() call scoped to what it did itself.
        clearInvocations(notificationListener);
    }

    private Long seedSeat(BigDecimal price) {
        Venue venue = venueRepository.save(Venue.builder().name("Chain Arena").city("Cairo").capacity(500).build());
        Event event = eventRepository.save(
                Event.builder().venue(venue).name("Chain Show").category("CONCERT")
                        .eventDate(Instant.now().plus(3, ChronoUnit.HOURS)).build());
        Seat seat = seatRepository.save(
                Seat.builder().event(event).seatNumber("A1").price(price).status(SeatStatus.AVAILABLE).build());
        return seat.getId();
    }

    @Test
    void fullChain_paymentSucceeds_confirmsBookingBooksSeatAndNotifies() {
        Long seatId = seedSeat(new BigDecimal("50.00")); // comfortably under the 1000.00 mock decline threshold

        BookingResponse created = bookingService.create(new BookingRequest(userId, List.of(seatId)));
        // The synchronous result is still PENDING - payment hasn't run yet,
        // it's mock-charged by a Kafka consumer reacting to the
        // AFTER_COMMIT publish this call triggers, not by this call itself.
        assertThat(created.status()).isEqualTo(BookingStatus.PENDING);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Booking booking = bookingRepository.findById(created.id()).orElseThrow();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.BOOKED);
        });

        // The third link: NotificationListener, on its own independent
        // consumer group, received the same BookingConfirmedEvent that
        // PaymentConsumer reacted to - proof the fan-out is real, not just
        // the payment path.
        verify(notificationListener, timeout(15_000))
                .onBookingConfirmed(argThat((BookingConfirmedEvent e) -> e.bookingId().equals(created.id())));
    }

    @Test
    void fullChain_paymentDeclines_failsBookingAndReleasesSeat() {
        Long seatId = seedSeat(new BigDecimal("5000.00")); // over the mock decline threshold

        BookingResponse created = bookingService.create(new BookingRequest(userId, List.of(seatId)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Booking booking = bookingRepository.findById(created.id()).orElseThrow();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
            // The seat didn't stay RESERVED forever behind a declined mock
            // charge - PaymentProcessedListener released it.
            assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        });
    }

    @Test
    void notificationListenerAlwaysFails_messageIsRetriedThenDeadLettered() {
        doThrow(new RuntimeException("mock email provider down"))
                .when(notificationListener).onBookingConfirmed(any());

        Long seatId = seedSeat(new BigDecimal("50.00"));
        BookingResponse created = bookingService.create(new BookingRequest(userId, List.of(seatId)));

        try (Consumer<String, String> dltConsumer = dltConsumer()) {
            dltConsumer.subscribe(List.of(KafkaTopicConfig.BOOKING_CONFIRMED_DLT));

            // KafkaErrorHandlingConfig's backoff budget is 10s cumulative
            // before it gives up and dead-letters - 20s here is that budget
            // plus headroom for consumer-group startup and poll latency,
            // not a guess at the exact number of retries. Asserting an
            // exact retry count against wall-clock timing would make this
            // test flaky under CI load for reasons unrelated to the actual
            // behavior being verified.
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                ConsumerRecords<String, String> records = dltConsumer.poll(Duration.ofMillis(500));
                boolean found = false;
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value() != null && record.value().contains("\"bookingId\":" + created.id())) {
                        found = true;
                    }
                }
                assertThat(found)
                        .as("booking %d's confirmed-event message reaching the dead-letter topic", created.id())
                        .isTrue();
            });
        }

        // More than one invocation is what actually distinguishes "the
        // retry policy ran and then gave up" from "the message went
        // straight to the DLT with no retry at all."
        verify(notificationListener, atLeast(2)).onBookingConfirmed(any());
    }

    private Consumer<String, String> dltConsumer() {
        Map<String, Object> props =
                KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), "dlt-test-reader", "true");
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }
}
