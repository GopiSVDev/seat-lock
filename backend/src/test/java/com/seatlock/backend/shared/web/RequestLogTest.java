package com.seatlock.backend.shared.web;

import com.seatlock.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RequestLogTest extends IntegrationTest {

    @Test
    void everyRequestIsLoggedWithItsStatusAndRejectionsWithTheirReason(CapturedOutput output)
            throws InterruptedException {
        assertThat(api.get("/bookings", null).status()).isEqualTo(401);
        assertThat(api.get("/shows/999999999", null).status()).isEqualTo(404);

        awaitLogged(output, "GET /bookings -> 401", "Rejected with 401", "GET /shows/999999999 -> 404",
                "Rejected with 404");
    }

    @Test
    void healthChecksAreNotLogged(CapturedOutput output) throws InterruptedException {
        assertThat(api.get("/actuator/health", null).status()).isEqualTo(200);
        assertThat(api.get("/shows/999999999", null).status()).isEqualTo(404);

        awaitLogged(output, "GET /shows/999999999 -> 404");
        assertThat(output.getAll()).doesNotContain("/actuator/health");
    }

    // The line is written once the response is done, which can be just after the client has it
    static void awaitLogged(CapturedOutput output, String... lines) throws InterruptedException {
        for (int i = 0; i < 40 && !Arrays.stream(lines).allMatch(output.getAll()::contains); i++) {
            Thread.sleep(50);
        }
        assertThat(output.getAll()).contains(lines);
    }
}
