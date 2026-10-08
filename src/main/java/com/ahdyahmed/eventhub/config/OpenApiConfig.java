package com.ahdyahmed.eventhub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Human-facing OpenAPI metadata and the JWT authorization control used by
 * Swagger UI. Security requirements stay on protected operations instead of
 * being global because EventHub intentionally supports anonymous browsing.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_JWT = "bearer-jwt";

    @Bean
    public OpenAPI eventHubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("EventHub Booking API")
                        .version("1.0.0")
                        .description("Ticket discovery and concurrency-safe booking with Redis caching, "
                                + "Kafka payment processing, and booking ownership enforced by JWT.")
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/license/mit")))
                .components(new Components().addSecuritySchemes(BEARER_JWT,
                        new SecurityScheme()
                                .name(BEARER_JWT)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the token returned by register or login. "
                                        + "Swagger UI adds the Bearer prefix automatically.")));
    }
}
