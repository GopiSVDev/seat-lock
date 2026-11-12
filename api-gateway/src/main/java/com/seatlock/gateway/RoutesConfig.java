package com.seatlock.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

// The only way in from outside. Paths are forwarded unchanged. Anything not listed here is 404:
// /internal/** (service callbacks), the payment service's /payments/** and every /actuator.
@Configuration
public class RoutesConfig {

    @Bean
    RouterFunction<ServerResponse> backendApi(@Value("${backend.base-url}") String backend) {
        return route("backend-api")
                .route(path("/api/v1/**").or(path("/swagger-ui.html")).or(path("/swagger-ui/**"))
                        .or(path("/v3/api-docs/**")).or(path("/dev/**")), http())
                .before(uri(backend)).onError(Exception.class, UpstreamErrors::toResponse).build();
    }

    // Razorpay's webhook: the payment service's only public endpoint
    @Bean
    RouterFunction<ServerResponse> paymentWebhooks(@Value("${payment.base-url}") String payment) {
        return route("payment-webhooks").POST("/webhooks/razorpay", http()).before(uri(payment))
                .onError(Exception.class, UpstreamErrors::toResponse).build();
    }
}
