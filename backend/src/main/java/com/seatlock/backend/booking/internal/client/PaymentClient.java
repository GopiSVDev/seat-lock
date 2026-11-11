package com.seatlock.backend.booking.internal.client;

import com.seatlock.backend.shared.exception.BadRequestException;
import com.seatlock.backend.shared.exception.DownstreamException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;

// The payment service, called synchronously with our own client credentials token.
// Plain timeouts only; retries and circuit breaking are step 5 (Resilience4j).
@Component
public class PaymentClient {

    // Every field of the payment service's response, so strict JSON reading never fails on an unknown one
    public record Payment(Long id, Long bookingId, long amount, String currency, String status, String razorpayOrderId,
            String razorpayPaymentId, String keyId) {
    }

    private final RestClient rest;

    public PaymentClient(OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${payment.base-url}") String baseUrl) {
        // HTTP/1.1: the JDK client's default HTTP/2 tries an h2c upgrade on plain http,
        // which servers handle unevenly
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory)
                .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(authorizedClientManager)).build();
    }

    // Idempotent per booking on the payment service's side: a retry returns the same Razorpay order
    public Payment create(Long bookingId, String customerId, long amount) {
        return call("create payment",
                () -> rest.post().uri("/payments").attributes(clientRegistrationId("seatlock"))
                        .body(Map.of("bookingId", bookingId, "customerId", customerId, "amount", amount)).retrieve()
                        .body(Payment.class));
    }

    public void verify(Long paymentId, String razorpayOrderId, String razorpayPaymentId, String signature) {
        try {
            call("verify payment", () -> rest.post().uri("/payments/{id}/verify", paymentId)
                    .attributes(clientRegistrationId("seatlock")).body(Map.of("razorpayOrderId", razorpayOrderId,
                            "razorpayPaymentId", razorpayPaymentId, "razorpaySignature", signature))
                    .retrieve().toBodilessEntity());
        } catch (DownstreamException e) {
            if (e.getCause() instanceof HttpClientErrorException clientError
                    && clientError.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new BadRequestException("Invalid payment signature");
            }
            throw e;
        }
    }

    // Idempotent on the payment service's side; reason is BOOKING_FAILED or BOOKING_CANCELLED
    public void refund(Long paymentId, String reason) {
        call("refund payment",
                () -> rest.post().uri("/payments/{id}/refund", paymentId).attributes(clientRegistrationId("seatlock"))
                        .body(Map.of("reason", reason)).retrieve().toBodilessEntity());
    }

    private static <T> T call(String action, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException | OAuth2AuthorizationException e) {
            // Also when Keycloak is down: no token, no call
            throw new DownstreamException("Payment service could not " + action, e);
        }
    }
}
