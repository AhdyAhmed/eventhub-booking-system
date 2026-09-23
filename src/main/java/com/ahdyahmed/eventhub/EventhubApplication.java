package com.ahdyahmed.eventhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the EventHub booking/order-processing service.
 *
 * <p>Day 1 scope: application bootstrap, database connectivity via Flyway-managed
 * PostgreSQL, and health checks via Spring Actuator. Domain entities, business logic,
 * caching, and the event-driven pipeline are layered in on the following days —
 * see the Roadmap section in README.md.</p>
 */
@SpringBootApplication
public class EventhubApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventhubApplication.class, args);
    }

}
