package com.seatlock.backend.inventory;

import com.seatlock.backend.support.Api.Response;
import com.seatlock.backend.support.IntegrationTest;
import com.seatlock.backend.support.TestShow;
import com.seatlock.backend.support.TestUser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Every test user has a random id, so each test starts with a full bucket
class HoldRateLimitTest extends IntegrationTest {

    private static final int BURST = 10;

    private final TestUser asha = TestUser.customer("Asha Kumar");
    private final TestUser ravi = TestUser.customer("Ravi Shankar");

    @Test
    void holdsBeyondTheBurstAreTooManyRequests() {
        TestShow show = newShow();
        // Each hold replaces the previous one, so only the limit can stop this loop
        for (int i = 0; i < BURST; i++) {
            assertThat(holdA1(asha, show).status()).as("hold %d", i + 1).isEqualTo(201);
        }

        Response limited = holdA1(asha, show);

        assertThat(limited.status()).isEqualTo(429);
        assertThat(limited.header("Content-Type")).contains("application/problem+json");
        assertThat(Long.parseLong(limited.header("Retry-After"))).isBetween(1L, 6L);
        assertThat(limited.text("detail")).startsWith("Too many hold requests");
        // The bucket lives in Redis (shared by every instance) and expires once it would be full again
        assertThat(redis.getExpire("rate:holds:" + asha.id())).isBetween(1L, 70L);
    }

    @Test
    void bestAvailableSharesTheBucket() {
        TestShow show = newShow();
        for (int i = 0; i < BURST / 2; i++) {
            assertThat(holdA1(asha, show).status()).isEqualTo(201);
            assertThat(bestAvailable(asha, show).status()).isEqualTo(201);
        }

        assertThat(bestAvailable(asha, show).status()).isEqualTo(429);
        assertThat(holdA1(asha, show).status()).isEqualTo(429);
    }

    @Test
    void otherUsersKeepTheirOwnBucket() {
        TestShow show = newShow();
        exhaust(asha, show);

        assertThat(api.post("/holds", ravi, Map.of("showId", show.showId(), "showSeatIds", show.seats("B", 1)))
                .status()).isEqualTo(201);
    }

    @Test
    void confirmAndReleaseAreNotLimited() {
        TestShow show = newShow();
        String lastHold = exhaust(asha, show);

        assertThat(api.send("DELETE", "/holds/" + lastHold, asha.token(), null).status()).isEqualTo(204);
        assertThat(api.get("/holds/" + lastHold, asha).status()).isEqualTo(404);

        // Ravi's own bucket is fresh: hold, then use up the rest and confirm after
        String raviHold = exhaust(ravi, show);
        assertThat(confirm(ravi, raviHold).status()).isEqualTo(201);
    }

    // Uses the whole burst, checks the next hold is refused, returns the last hold that got through
    private String exhaust(TestUser user, TestShow show) {
        String last = null;
        for (int i = 0; i < BURST; i++) {
            last = holdA1(user, show).text("id");
        }
        assertThat(holdA1(user, show).status()).isEqualTo(429);
        return last;
    }

    private Response holdA1(TestUser user, TestShow show) {
        return hold(user, show, show.seats("A", 1));
    }

    private Response bestAvailable(TestUser user, TestShow show) {
        return api.post("/holds/best-available", user,
                Map.of("showId", show.showId(), "quantity", 2, "seatType", "REGULAR"));
    }
}
