package com.seatlock.backend.shared.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI seatLockOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SeatLock API")
                .version("v1")
                .description("Movie ticket booking: catalogue (movies, theatres, screens, shows), "
                        + "seat inventory and bookings. Never sells the same seat twice."));
    }
}
