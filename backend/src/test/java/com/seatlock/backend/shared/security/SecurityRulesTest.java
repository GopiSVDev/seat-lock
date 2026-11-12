package com.seatlock.backend.shared.security;

import com.seatlock.backend.support.Api.Response;
import com.seatlock.backend.support.IntegrationTest;
import com.seatlock.backend.support.TestUser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityRulesTest extends IntegrationTest {

    private final TestUser asha = TestUser.customer("Asha Kumar");

    @Test
    void catalogueReadsAndOperationsEndpointsArePublic() {
        assertThat(api.get("/api/v1/cities?query=chennai", null).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/movies", null).status()).isEqualTo(200);
        assertThat(api.get("/v3/api-docs", null).status()).isEqualTo(200);
        assertThat(api.get("/actuator/health", null).status()).isEqualTo(200);
        assertThat(api.get("/dev/checkout.html", null).status()).isEqualTo(200);
    }

    @Test
    void writeWithoutTokenIs401ProblemDetailWithBearerHeader() {
        Response response = api.post("/api/v1/movies", null, Map.of());

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.header("Content-Type")).contains("application/problem+json");
        assertThat(response.header("WWW-Authenticate")).startsWith("Bearer");
        assertThat(response.body().get("detail").asString()).isEqualTo("Authentication required");
        assertThat(response.body().get("instance").asString()).isEqualTo("/api/v1/movies");
    }

    @Test
    void invalidTokenIs401() {
        Response response = api.send("GET", "/api/v1/bookings", "abc.def.ghi", null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.body().get("detail").asString()).isEqualTo("Invalid or expired token");
        assertThat(response.header("WWW-Authenticate")).contains("invalid_token");
    }

    @Test
    void customerCannotCreateCatalogueData() {
        Response response = api.post("/api/v1/movies", asha, Map.of());

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.body().get("detail").asString()).isEqualTo("Not allowed for your role");
    }

    @Test
    void adminPassesSecurityAndReachesValidation() {
        assertThat(api.post("/api/v1/movies", admin, Map.of()).status()).isEqualTo(400);
    }

    @Test
    void bookingsNeedLogin() {
        assertThat(api.get("/api/v1/bookings", null).status()).isEqualTo(401);
        assertThat(api.get("/api/v1/bookings", asha).status()).isEqualTo(200);
    }

    @Test
    void holdsNeedLogin() {
        assertThat(api.post("/api/v1/holds", null, Map.of()).status()).isEqualTo(401);
        assertThat(api.get("/api/v1/holds/any", null).status()).isEqualTo(401);
    }

    @Test
    void unknownPathsAreClosedByDefault() {
        assertThat(api.get("/not-an-endpoint", null).status()).isEqualTo(401);
        assertThat(api.get("/not-an-endpoint", asha).status()).isEqualTo(404);
    }

    @Test
    void apiLivesUnderV1AndInternalEndpointsOutsideIt() {
        assertThat(api.get("/cities", asha).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/bookings", asha).status()).isEqualTo(200);
        TestUser paymentService = TestUser.service("seatlock-payment", "BOOKING_CALLBACK");
        assertThat(api.post("/api/v1/internal/bookings/1/paid", paymentService, Map.of("paymentId", 1)).status())
                .isEqualTo(404);
    }
}
