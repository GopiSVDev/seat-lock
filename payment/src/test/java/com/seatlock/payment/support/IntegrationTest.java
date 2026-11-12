package com.seatlock.payment.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.seatlock.payment.razorpay.RazorpaySignature;
import com.seatlock.payment.support.Api.Response;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

// Base for tests against the running app and a real Postgres. One WireMock plays all three outside parties:
// Razorpay (/v1/**), Keycloak's token endpoint (/token) and the backend (/internal/**).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
public abstract class IntegrationTest {

    protected static final String KEY_ID = "rzp_test_key";
    protected static final String KEY_SECRET = "test-key-secret";
    protected static final String WEBHOOK_SECRET = "test-webhook-secret";
    protected static final String SERVICE_TOKEN = "stub-service-token";

    protected static final WireMockServer wireMock = new WireMockServer(options().dynamicPort());

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void outsideParties(DynamicPropertyRegistry registry) {
        registry.add("razorpay.base-url", wireMock::baseUrl);
        registry.add("backend.base-url", wireMock::baseUrl);
        registry.add("spring.security.oauth2.client.provider.seatlock.token-uri", () -> wireMock.baseUrl() + "/token");
    }

    protected final TestService backend = TestService.backend();

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected JsonMapper jsonMapper;

    @Value("${local.server.port}")
    private int port;

    protected Api api;

    @BeforeEach
    void resetOutsideParties() {
        api = new Api("http://localhost:" + port, jsonMapper);
        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/token")).willReturn(okJson("""
                {"access_token": "%s", "token_type": "Bearer", "expires_in": 300}
                """.formatted(SERVICE_TOKEN))));
        wireMock.stubFor(post(urlPathMatching("/internal/bookings/\\d+/paid")).willReturn(aResponse().withStatus(204)));
    }

    // A new payment for a booking id no other test uses
    protected Response createPayment(long amount) {
        return createPayment(newBookingId(), amount);
    }

    protected Response createPayment(long bookingId, long amount) {
        stubRazorpayOrder("order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
        return api.post("/payments", backend, Map.of("bookingId", bookingId, "customerId", "asha", "amount", amount));
    }

    protected void stubRazorpayOrder(String orderId) {
        wireMock.stubFor(post(urlEqualTo("/v1/orders")).willReturn(okJson("""
                {"id": "%s", "entity": "order", "status": "created"}
                """.formatted(orderId))));
    }

    protected Response capture(Response payment, String razorpayPaymentId) {
        String orderId = payment.text("razorpayOrderId");
        Response verified = api.post("/payments/" + payment.id() + "/verify", backend,
                Map.of("razorpayOrderId", orderId, "razorpayPaymentId", razorpayPaymentId, "razorpaySignature",
                        checkoutSignature(orderId, razorpayPaymentId)));
        assertThat(verified.status()).as(verified.toString()).isEqualTo(200);
        return verified;
    }

    protected static String checkoutSignature(String orderId, String razorpayPaymentId) {
        return RazorpaySignature.hmacHex((orderId + "|" + razorpayPaymentId).getBytes(StandardCharsets.UTF_8),
                KEY_SECRET);
    }

    // Signed like Razorpay signs it
    protected Response webhook(Map<String, Object> event) {
        String body = jsonMapper.writeValueAsString(event);
        return api.postRaw("/webhooks/razorpay", body, Map.of("X-Razorpay-Signature",
                RazorpaySignature.hmacHex(body.getBytes(StandardCharsets.UTF_8), WEBHOOK_SECRET)));
    }

    protected static Map<String, Object> paymentEvent(String event, String orderId, String razorpayPaymentId,
            String errorDescription) {
        Map<String, Object> entity = new HashMap<>(Map.of("id", razorpayPaymentId, "entity", "payment", "order_id",
                orderId, "method", "card", "status", event.equals("payment.captured") ? "captured" : "failed"));
        entity.put("error_description", errorDescription);
        return Map.of("entity", "event", "event", event, "contains", List.of("payment"), "payload",
                Map.of("payment", Map.of("entity", entity)));
    }

    protected static Map<String, Object> refundEvent(String razorpayPaymentId) {
        return Map.of("entity", "event", "event", "refund.processed", "contains", List.of("refund", "payment"),
                "payload",
                Map.of("refund", Map.of("entity", Map.of("id", "rfnd_" + UUID.randomUUID().toString().substring(0, 8),
                        "entity", "refund", "payment_id", razorpayPaymentId, "status", "processed"))));
    }

    protected String status(long paymentId) {
        return jdbc.sql("select status from payment.payment where id = :id").param("id", paymentId).query(String.class)
                .single();
    }

    protected static long newBookingId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    protected static String razorpayPaymentId() {
        return "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }
}
