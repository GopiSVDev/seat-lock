package com.seatlock.payment.booking;

import com.seatlock.payment.shared.exception.DownstreamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;

// Tells the backend a booking is paid. Each call carries our own client credentials token
// (fetched from Keycloak and cached until it expires by the interceptor).
@Slf4j
@Component
public class BookingClient {

    private final RestClient rest;

    public BookingClient(OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${backend.base-url}") String baseUrl) {
        // HTTP/1.1 for the same reason as RazorpayClient (plain http inside Docker)
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory)
                .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(authorizedClientManager)).build();
    }

    public void paid(long bookingId, long paymentId) {
        try {
            rest.post().uri("/internal/bookings/{id}/paid", bookingId).attributes(clientRegistrationId("seatlock"))
                    .body(Map.of("paymentId", paymentId)).retrieve().toBodilessEntity();
            log.info("Backend accepted payment {} of booking {}", paymentId, bookingId);
        } catch (RestClientException | OAuth2AuthorizationException e) {
            // Also when Keycloak is down: no token, no call
            throw new DownstreamException("Backend did not accept payment of booking " + bookingId, e);
        }
    }
}
