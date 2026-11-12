package com.seatlock.backend.shared.web;

import com.seatlock.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RequestLogTest extends IntegrationTest {

    @Test
    void everyRequestIsLoggedWithItsStatusAndRejectionsWithTheirReason(CapturedOutput output)
            throws InterruptedException {
        assertThat(api.get("/api/v1/bookings", null).status()).isEqualTo(401);
        assertThat(api.get("/api/v1/shows/999999999", null).status()).isEqualTo(404);

        awaitLogged(output, "GET /api/v1/bookings -> 401", "Rejected with 401", "GET /api/v1/shows/999999999 -> 404",
                "Rejected with 404");
    }

    @Test
    void healthChecksAreNotLogged(CapturedOutput output) throws InterruptedException {
        assertThat(api.get("/actuator/health", null).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/shows/999999999", null).status()).isEqualTo(404);

        awaitLogged(output, "GET /api/v1/shows/999999999 -> 404");
        assertThat(output.getAll()).doesNotContain("/actuator/health");
    }

    @Test
    void clientAddressComesFromTheGatewaysForwardedFor(CapturedOutput output) throws IOException, InterruptedException {
        // Tests call from 127.0.0.1, a trusted proxy address like the gateway's Docker address
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/shows/999999999"))
                .header("X-Forwarded-For", "203.0.113.7").build();
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().send(request,
                HttpResponse.BodyHandlers.discarding());

        awaitLogged(output, "203.0.113.7 GET /api/v1/shows/999999999 -> 404");
    }

    // The line is written once the response is done, which can be just after the client has it
    static void awaitLogged(CapturedOutput output, String... lines) throws InterruptedException {
        for (int i = 0; i < 40 && !Arrays.stream(lines).allMatch(output.getAll()::contains); i++) {
            Thread.sleep(50);
        }
        assertThat(output.getAll()).contains(lines);
    }
}
