package com.ahdyahmed.eventhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void publishesPortfolioMetadataAndJwtSecurityScheme() {
        OpenAPI openApi = new OpenApiConfig().eventHubOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("EventHub Booking API");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("1.0.0");
        assertThat(openApi.getInfo().getLicense().getName()).isEqualTo("MIT");

        SecurityScheme jwt = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_JWT);
        assertThat(jwt.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(jwt.getScheme()).isEqualTo("bearer");
        assertThat(jwt.getBearerFormat()).isEqualTo("JWT");
    }
}
