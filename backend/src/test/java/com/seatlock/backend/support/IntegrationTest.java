package com.seatlock.backend.support;

import com.seatlock.backend.support.Api.Response;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Base for tests against the running app and a real Postgres. Each test creates its own data
// (city, theatre, show, users), so tests don't depend on each other or on their order.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
public abstract class IntegrationTest {

    protected static final long CHENNAI = 5;
    // Paise; prices of every newShow()
    protected static final long REGULAR_PRICE = 15000;
    protected static final long RECLINER_PRICE = 40000;

    protected final TestUser admin = TestUser.admin();

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    private JsonMapper jsonMapper;

    @Value("${local.server.port}")
    private int port;

    protected Api api;

    @BeforeEach
    void startClient() {
        api = new Api("http://localhost:" + port, jsonMapper);
    }

    protected TestShow newShow() {
        return newShow(Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS));
    }

    protected TestShow newShow(Instant startTime) {
        long cityId = created(api.post("/cities", admin, Map.of("name", unique("Testpur"), "state", "Tamil Nadu")));
        long movieId = created(api.post("/movies", admin, Map.of(
                "title", unique("Movie"), "durationMinutes", 150, "certification", "UA",
                "releaseDate", "2026-01-01", "genres", List.of("DRAMA"))));
        long theatreId = created(api.post("/theatres", admin, Map.of(
                "name", unique("Cinema"), "cityId", cityId, "address", "1 Main Road")));
        long screenId = created(api.post("/theatres/" + theatreId + "/screens", admin, Map.of(
                "name", "Screen 1", "rows", List.of(
                        row("A", 10, "REGULAR"), row("B", 10, "REGULAR"), row("C", 8, "RECLINER")))));
        long showId = created(api.post("/shows", admin, Map.of(
                "movieId", movieId, "screenId", screenId, "startTime", startTime.toString(),
                "language", "ENGLISH", "format", "IMAX_2D",
                "prices", Map.of("REGULAR", REGULAR_PRICE, "RECLINER", RECLINER_PRICE))));

        JsonNode rows = api.get("/shows/" + showId + "/layout", null).body().get("rows");
        Map<String, List<Long>> seats = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            seats.put(rows.get(i).get("label").asString(), ids(rows.get(i).get("seats"), "id"));
        }
        return new TestShow(showId, movieId, theatreId, screenId, cityId, startTime, seats);
    }

    protected Response hold(TestUser user, TestShow show, List<Long> seatIds) {
        return api.post("/holds", user, Map.of("showId", show.showId(), "showSeatIds", seatIds));
    }

    protected Response confirm(TestUser user, String holdId) {
        return api.post("/bookings", user, Map.of("holdId", holdId));
    }

    // Hold, then confirm: the only way to book. A failed hold is returned as is.
    protected Response book(TestUser user, TestShow show, List<Long> seatIds) {
        Response hold = hold(user, show, seatIds);
        return hold.status() == 201 ? confirm(user, hold.text("id")) : hold;
    }

    protected List<String> seatStatuses(List<Long> showSeatIds) {
        return jdbc.sql("select status from inventory.show_seat where id in (:ids)")
                .param("ids", showSeatIds)
                .query(String.class)
                .list();
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
