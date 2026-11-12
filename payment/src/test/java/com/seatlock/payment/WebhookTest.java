package com.seatlock.payment;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.seatlock.payment.support.Api.Response;
import com.seatlock.payment.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

class WebhookTest extends IntegrationTest {

    @Test
    void unsignedOrForgedEventsAreRejected() {
        Response payment = createPayment(30000);
        String body = jsonMapper.writeValueAsString(
                paymentEvent("payment.captured", payment.text("razorpayOrderId"), razorpayPaymentId(), null));

        assertThat(api.postRaw("/webhooks/razorpay", body, Map.of()).status()).isEqualTo(400);
        assertThat(api.postRaw("/webhooks/razorpay", body, Map.of("X-Razorpay-Signature", "0".repeat(64))).status())
                .isEqualTo(400);
        assertThat(status(payment.id())).isEqualTo("CREATED");
        assertThat(backendCalls()).isEmpty();
    }

    @Test
    void capturedEventCapturesAndTellsTheBackendWithAServiceToken() {
        long bookingId = newBookingId();
        Response payment = createPayment(bookingId, 30000);
        String razorpayPaymentId = razorpayPaymentId();

        Response response = webhook(
                paymentEvent("payment.captured", payment.text("razorpayOrderId"), razorpayPaymentId, null));

        assertThat(response.status()).isEqualTo(200);
        assertThat(status(payment.id())).isEqualTo("CAPTURED");
        List<LoggedRequest> calls = backendCalls();
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().getUrl()).isEqualTo("/internal/bookings/" + bookingId + "/paid");
        assertThat(calls.getFirst().getHeader("Authorization")).isEqualTo("Bearer " + SERVICE_TOKEN);
        assertThat(calls.getFirst().getBodyAsString()).contains("\"paymentId\":" + payment.id());
    }

    @Test
    void repeatedEventChangesNothingButTellsTheBackendAgain() {
        Response payment = createPayment(30000);
        Map<String, Object> event = paymentEvent("payment.captured", payment.text("razorpayOrderId"),
                razorpayPaymentId(), null);

        assertThat(webhook(event).status()).isEqualTo(200);
        assertThat(webhook(event).status()).isEqualTo(200);

        assertThat(status(payment.id())).isEqualTo("CAPTURED");
        assertThat(attempts(payment.id())).containsExactly("CAPTURED");
        // The backend's confirm is idempotent; telling it twice is how a lost first call gets repaired
        assertThat(backendCalls()).hasSize(2);
    }

    @Test
    void backendDownMakesRazorpayRetry() {
        Response payment = createPayment(30000);
        Map<String, Object> event = paymentEvent("payment.captured", payment.text("razorpayOrderId"),
                razorpayPaymentId(), null);
        wireMock.stubFor(post(urlPathMatching("/internal/bookings/\\d+/paid")).willReturn(aResponse().withStatus(503)));

        assertThat(webhook(event).status()).isEqualTo(502);
        // The money is ours either way; only the backend still has to hear about it
        assertThat(status(payment.id())).isEqualTo("CAPTURED");

        wireMock.stubFor(post(urlPathMatching("/internal/bookings/\\d+/paid")).willReturn(aResponse().withStatus(204)));
        assertThat(webhook(event).status()).isEqualTo(200);
    }

    @Test
    void failedAttemptKeepsTheOrderOpen() {
        Response payment = createPayment(30000);
        String orderId = payment.text("razorpayOrderId");

        assertThat(webhook(paymentEvent("payment.failed", orderId, razorpayPaymentId(), "Card declined")).status())
                .isEqualTo(200);
        assertThat(status(payment.id())).isEqualTo("CREATED");
        assertThat(backendCalls()).isEmpty();

        assertThat(webhook(paymentEvent("payment.captured", orderId, razorpayPaymentId(), null)).status())
                .isEqualTo(200);
        assertThat(status(payment.id())).isEqualTo("CAPTURED");
        assertThat(attempts(payment.id())).containsExactlyInAnyOrder("FAILED", "CAPTURED");
        assertThat(jdbc.sql(
                "select error_description from payment.payment_attempt where payment_id = :id and status = 'FAILED'")
                .param("id", payment.id()).query(String.class).single()).isEqualTo("Card declined");
    }

    @Test
    void refundProcessedEventCompletesTheRefund() {
        Response payment = createPayment(30000);
        String razorpayPaymentId = razorpayPaymentId();
        capture(payment, razorpayPaymentId);
        wireMock.stubFor(post(urlPathMatching("/v1/payments/.+/refund")).willReturn(okJson("""
                {"id": "rfnd_test3", "entity": "refund", "status": "pending"}
                """)));
        api.post("/payments/" + payment.id() + "/refund", backend, Map.of("reason", "BOOKING_CANCELLED"));

        assertThat(webhook(refundEvent(razorpayPaymentId)).status()).isEqualTo(200);
        assertThat(webhook(refundEvent(razorpayPaymentId)).status()).isEqualTo(200);

        assertThat(status(payment.id())).isEqualTo("REFUNDED");
        assertThat(jdbc.sql("select processed_at is not null from payment.refund where payment_id = :id")
                .param("id", payment.id()).query(Boolean.class).single()).isTrue();
    }

    @Test
    void browserFirstStillKeepsTheMethodTheWebhookBrings() {
        Response payment = createPayment(30000);
        String razorpayPaymentId = razorpayPaymentId();
        capture(payment, razorpayPaymentId);

        assertThat(webhook(paymentEvent("payment.captured", payment.text("razorpayOrderId"), razorpayPaymentId, null))
                .status()).isEqualTo(200);

        assertThat(jdbc.sql("select method from payment.payment_attempt where payment_id = :id")
                .param("id", payment.id()).query(String.class).single()).isEqualTo("card");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void webhooksAreLoggedWithTheirEventAndIds(CapturedOutput output) throws InterruptedException {
        Response payment = createPayment(30000);
        String orderId = payment.text("razorpayOrderId");
        String razorpayPaymentId = razorpayPaymentId();
        String forged = jsonMapper
                .writeValueAsString(paymentEvent("payment.captured", orderId, razorpayPaymentId, null));

        assertThat(api.postRaw("/webhooks/razorpay", forged, Map.of("X-Razorpay-Signature", "0".repeat(64))).status())
                .isEqualTo(400);
        assertThat(webhook(paymentEvent("payment.captured", orderId, razorpayPaymentId, null)).status()).isEqualTo(200);

        String[] lines = {"Rejected Razorpay webhook: invalid signature", "POST /webhooks/razorpay -> 400",
                "Razorpay event payment.captured: order " + orderId + ", payment " + razorpayPaymentId,
                "Payment " + payment.id() + " captured (" + razorpayPaymentId + ", reported by webhook)",
                "POST /webhooks/razorpay -> 200"};
        // Request lines are written once the response is done, which can be just after the client has it
        for (int i = 0; i < 40 && !Arrays.stream(lines).allMatch(output.getAll()::contains); i++) {
            Thread.sleep(50);
        }
        assertThat(output.getAll()).contains(lines);
    }

    @Test
    void eventsForUnknownOrdersAndOtherEventTypesAreAcknowledged() {
        assertThat(webhook(paymentEvent("payment.captured", "order_not_ours", razorpayPaymentId(), null)).status())
                .isEqualTo(200);
        assertThat(webhook(Map.of("entity", "event", "event", "order.paid", "payload", Map.of())).status())
                .isEqualTo(200);
        assertThat(backendCalls()).isEmpty();
    }

    private List<String> attempts(long paymentId) {
        return jdbc.sql("select status from payment.payment_attempt where payment_id = :id").param("id", paymentId)
                .query(String.class).list();
    }

    private static List<LoggedRequest> backendCalls() {
        return wireMock.findAll(postRequestedFor(urlPathMatching("/internal/bookings/.*")));
    }
}
