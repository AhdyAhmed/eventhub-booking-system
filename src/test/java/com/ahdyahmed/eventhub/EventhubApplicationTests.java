package com.ahdyahmed.eventhub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Day 1 smoke test: the Spring context must start and Flyway must run its
 * baseline migration against a real Postgres instance (via Testcontainers),
 * not an in-memory substitute. This is the pattern the rest of the test
 * suite will build on as domain logic is added.
 */
@Testcontainers
@SpringBootTest
class EventhubApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("eventhub_db")
            .withUsername("eventhub_user")
            .withPassword("eventhub_pass");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void contextLoads() {
        // If the context fails to start (e.g. Flyway migration error,
        // misconfigured datasource), this test fails.
    }

}
