package com.seatlock.backend.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.seatlock.backend.support.Api.Response;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

// Base for tests against the running app and a real Postgres. Each test creates its own data
// (city, theatre, show, users), so tests don't depend on each other or on their order.
// WireMock plays the payment service (/payments/**) and Keycloak's token endpoint (/token);
// by default every payment succeeds.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
public abstract class IntegrationTest {

    protected static final long CHENNAI = 5;
    // Paise; prices of every newShow()
    protected static final long REGULAR_PRICE = 15000;
    protected static final long RECLINER_PRICE = 40000;

    protected static final long PAYMENT_ID = 4242;
    protected static final String SERVICE_TOKEN = "stub-service-token";

    protected static final WireMockServer paymentService = new WireMockServer(options().dynamicPort());

    static {
        paymentService.start();
    }

    @DynamicPropertySource
    static void outsideServices(DynamicPropertyRegistry registry) {
        registry.add("payment.base-url", paymentService::baseUrl);
        registry.add("spring.security.oauth2.client.provider.seatlock.token-uri",
                () -> paymentService.baseUrl() + "/token");
    }

    protected final TestUser admin = TestUser.admin();

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    private JsonMapper jsonMapper;

    @Value("${local.server.port}")
    protected int port;

    protected Api api;

    @BeforeEach
    void startClient() {
        api = new Api("http://localhost:" + port, jsonMapper);
        paymentService.resetAll();
        paymentService.stubFor(post(urlEqualTo("/token")).willReturn(okJson("""
                {"access_token": "%s", "token_type": "Bearer", "expires_in": 300}
                """.formatted(SERVICE_TOKEN))));
        stubPaymentServiceUp();
    }

    protected void stubPaymentServiceUp() {
        paymentService.stubFor(post(urlEqualTo("/payments"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""
                        {"id": %d, "bookingId": 1, "amount": 30000, "currency": "INR", "status": "CREATED",
                         "razorpayOrderId": "order_test", "razorpayPaymentId": null, "keyId": "rzp_test_key"}
                        """.formatted(PAYMENT_ID))));
        paymentService.stubFor(post(urlPathMatching("/payments/\\d+/(verify|refund)")).willReturn(okJson("{}")));
    }

    protected TestShow newShow() {
        return newShow(Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS));
    }

    protected TestShow newShow(Instant startTime) {
        long cityId = created(
                api.post("/api/v1/cities", admin, Map.of("name", unique("Testpur"), "state", "Tamil Nadu")));
        long movieId = created(api.post("/api/v1/movies", admin, Map.of("title", unique("Movie"), "durationMinutes",
                150, "certification", "UA", "releaseDate", "2026-01-01", "genres", List.of("DRAMA"))));
        long theatreId = created(api.post("/api/v1/theatres", admin,
                Map.of("name", unique("Cinema"), "cityId", cityId, "address", "1 Main Road")));
        long screenId = created(
                api.post("/api/v1/theatres/" + theatreId + "/api/v1/screens", admin, Map.of("name", "Screen 1", "rows",
                        List.of(row("A", 10, "REGULAR"), row("B", 10, "REGULAR"), row("C", 8, "RECLINER")))));
        long showId = created(api.post("/api/v1/shows", admin,
                Map.of("movieId", movieId, "screenId", screenId, "startTime", startTime.toString(), "language",
                        "ENGLISH", "format", "IMAX_2D", "prices",
                        Map.of("REGULAR", REGULAR_PRICE, "RECLINER", RECLINER_PRICE))));

        JsonNode rows = api.get("/api/v1/shows/" + showId + "/layout", null).body().get("rows");
        Map<String, List<Long>> seats = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            seats.put(rows.get(i).get("label").asString(), ids(rows.get(i).get("seats"), "id"));
        }
        return new TestShow(showId, movieId, theatreId, screenId, cityId, startTime, seats);
    }

    protected Response hold(TestUser user, TestShow show, List<Long> seatIds) {
        return api.post("/api/v1/holds", user, Map.of("showId", show.showId(), "showSeatIds", seatIds));
    }

    // Starts payment: an unpaid booking (201, or 200 when the hold already has one)
    protected Response checkout(TestUser user, String holdId) {
        return api.post("/api/v1/bookings", user, Map.of("holdId", holdId));
    }

    // What the browser sends after Razorpay Checkout; the stubbed payment service accepts any signature
    protected Response pay(TestUser user, long bookingId) {
        return api.post("/api/v1/bookings/" + bookingId + "/payment", user,
                Map.of("razorpayOrderId", "order_test", "razorpayPaymentId", "pay_test", "razorpaySignature", "sig"));
    }

    // Checkout, then pay: 200 with the CONFIRMED booking. A failed checkout is returned as is.
    protected Response confirm(TestUser user, String holdId) {
        Response checkout = checkout(user, holdId);
        return checkout.status() == 201 || checkout.status() == 200 ? pay(user, checkout.id()) : checkout;
    }

    // Hold, checkout, pay: the only way to book. A failed hold is returned as is.
    protected Response book(TestUser user, TestShow show, List<Long> seatIds) {
        Response hold = hold(user, show, seatIds);
        return hold.status() == 201 ? confirm(user, hold.text("id")) : hold;
    }

    protected List<String> seatStatuses(List<Long> showSeatIds) {
        return jdbc.sql("select status from inventory.show_seat where id in (:ids)").param("ids", showSeatIds)
                .query(String.class).list();
    }

    protected static List<Long> ids(JsonNode array, String field) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            ids.add(array.get(i).get(field).asLong());
        }
        return ids;
    }

    protected static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private static long created(Response response) {
        assertThat(response.status()).as(response.toString()).isEqualTo(201);
        return response.id();
    }

    private static Map<String, Object> row(String label, int seatCount, String seatType) {
        return Map.of("label", label, "seatCount", seatCount, "seatType", seatType);
    }
}
