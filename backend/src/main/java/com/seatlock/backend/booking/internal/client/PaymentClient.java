package com.seatlock.backend.booking.internal.client;

import com.seatlock.backend.shared.exception.BadRequestException;
import com.seatlock.backend.shared.exception.DownstreamException;
import com.seatlock.backend.shared.exception.ServiceUnavailableException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
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
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;

// The payment service, called synchronously with our own client credentials token.
// Every call goes through the circuit breaker "payment"; checkout calls also through the bulkhead
// "payment", so a slow payment service can hold only a few request threads (settings in application.properties).
@Slf4j
@Component
public class PaymentClient {

    public static final String RESILIENCE_NAME = "payment";

    // Below the gateway's 10 s, above the payment service's 5 s wait on Razorpay
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(7);
    private static final Duration BUSY_RETRY_AFTER = Duration.ofSeconds(5);

    // Every field of the payment service's response, so strict JSON reading never fails on an unknown one
    public record Payment(Long id, Long bookingId, long amount, String currency, String status, String razorpayOrderId,
            String razorpayPaymentId, String keyId) {
    }

    private final RestClient rest;
    private final CircuitBreaker breaker;
    private final Bulkhead bulkhead;
    private final Duration openDuration;
    private volatile Instant openedAt = Instant.EPOCH;

    public PaymentClient(OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${payment.base-url}") String baseUrl, CircuitBreakerRegistry breakers, BulkheadRegistry bulkheads,
            @Value("${resilience4j.circuitbreaker.instances.payment.wait-duration-in-open-state}") Duration openDuration) {
        // HTTP/1.1: the JDK client's default HTTP/2 tries an h2c upgrade on plain http,
        // which servers handle unevenly
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory)
                .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(authorizedClientManager)).build();

        this.breaker = breakers.circuitBreaker(RESILIENCE_NAME);
        this.bulkhead = bulkheads.bulkhead(RESILIENCE_NAME);
        this.openDuration = openDuration;
        breaker.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.State to = event.getStateTransition().getToState();
            if (to == CircuitBreaker.State.OPEN) {
                openedAt = Instant.now();
                log.warn("Payment circuit breaker OPEN: payment calls refused for {}", openDuration);
            } else {
                log.info("Payment circuit breaker {}", to);
            }
        });
    }

    // Idempotent per booking on the payment service's side: a retry returns the same Razorpay order
    public Payment create(Long bookingId, String customerId, long amount) {
        return call("create payment", true,
                () -> rest.post().uri("/payments").attributes(clientRegistrationId("seatlock"))
                        .body(Map.of("bookingId", bookingId, "customerId", customerId, "amount", amount)).retrieve()
                        .body(Payment.class));
    }

    public void verify(Long paymentId, String razorpayOrderId, String razorpayPaymentId, String signature) {
        try {
            call("verify payment", true, () -> rest.post().uri("/payments/{id}/verify", paymentId)
                    .attributes(clientRegistrationId("seatlock")).body(Map.of("razorpayOrderId", razorpayOrderId,
                            "razorpayPaymentId", razorpayPaymentId, "razorpaySignature", signature))
                    .retrieve().toBodilessEntity());
        } catch (DownstreamException e) {
            if (e.getCause() instanceof HttpClientErrorException clientError
                    && clientError.getStatusCode() == HttpStatus.BAD_REQUEST) {
                log.warn("Payment service rejected the browser's signature for payment {}", paymentId);
                throw new BadRequestException("Invalid payment signature");
            }
            throw e;
        }
    }

    // Idempotent on the payment service's side; reason is BOOKING_FAILED or BOOKING_CANCELLED.
    // Breaker only: refunds are rare, and refusing one because checkouts are busy would strand the money.
    public void refund(Long paymentId, String reason) {
        call("refund payment", false,
                () -> rest.post().uri("/payments/{id}/refund", paymentId).attributes(clientRegistrationId("seatlock"))
                        .body(Map.of("reason", reason)).retrieve().toBodilessEntity());
    }

    // Bulkhead outside the breaker: calls it turns away never count as payment service failures
    private <T> T call(String action, boolean limited, Supplier<T> request) {
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(breaker, request);
        if (limited) {
            guarded = Bulkhead.decorateSupplier(bulkhead, guarded);
        }
        try {
            return guarded.get();
        } catch (BulkheadFullException e) {
            throw new ServiceUnavailableException("Payments are busy", BUSY_RETRY_AFTER);
        } catch (CallNotPermittedException e) {
            // Half-open with its trial calls taken: the time is up, so this rounds to 1 s
            Duration left = Duration.between(Instant.now(), openedAt.plus(openDuration));
            throw new ServiceUnavailableException("Payments are unavailable", left.isNegative() ? Duration.ZERO : left);
        } catch (RestClientException | OAuth2AuthorizationException e) {
            // Also when Keycloak is down: no token, no call
            throw new DownstreamException("Payment service could not " + action, e);
        }
    }
}
