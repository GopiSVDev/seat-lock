package com.seatlock.payment.razorpay;

import com.seatlock.payment.shared.exception.DownstreamException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

// Razorpay's REST API (https://razorpay.com/docs/api/): basic auth with the key id and secret
@Component
public class RazorpayClient {

    private final RestClient rest;

    public RazorpayClient(RazorpayProperties properties) {
        // Read timeout below the backend's 7 s wait on us, so our error reaches it first.
        // HTTP/1.1: the JDK client's default HTTP/2 tries an h2c upgrade on plain http,
        // which servers handle unevenly
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.rest = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setBasicAuth(properties.keyId(), properties.keySecret())).build();
    }

    public String createOrder(long amount, String currency, long bookingId) {
        Map<String, Object> body = Map.of("amount", amount, "currency", currency, "receipt", "booking-" + bookingId,
                "notes", Map.of("bookingId", String.valueOf(bookingId)));
        return idOf(call("create order", () -> rest.post().uri("/v1/orders").body(body).retrieve().body(Map.class)));
    }

    public String refund(String razorpayPaymentId, long amount, String reason) {
        Map<String, Object> body = Map.of("amount", amount, "notes", Map.of("reason", reason));
        return idOf(call("refund", () -> rest.post().uri("/v1/payments/{id}/refund", razorpayPaymentId).body(body)
                .retrieve().body(Map.class)));
    }

    private static Map<?, ?> call(String action, Supplier<Map<?, ?>> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            throw new DownstreamException("Razorpay " + action + " failed", e);
        }
    }

    private static String idOf(Map<?, ?> response) {
        if (response == null || !(response.get("id") instanceof String id)) {
            throw new DownstreamException("Razorpay response has no id", null);
        }
        return id;
    }
}
