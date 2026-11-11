package com.seatlock.payment;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.seatlock.payment.support.Api.Response;
import com.seatlock.payment.support.IntegrationTest;
import com.seatlock.payment.support.TestService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentApiTest extends IntegrationTest {

    @Test
    void createsOneRazorpayOrderPerBooking() {
        long bookingId = newBookingId();
        Response first = createPayment(bookingId, 30000);
        Response again = createPayment(bookingId, 30000);

        assertThat(first.status()).as(first.toString()).isEqualTo(201);
        assertThat(first.text("status")).isEqualTo("CREATED");
        assertThat(first.text("currency")).isEqualTo("INR");
        assertThat(first.text("keyId")).isEqualTo(KEY_ID);
        assertThat(first.body().get("amount").asLong()).isEqualTo(30000);
        assertThat(again.status()).isEqualTo(200);
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.text("razorpayOrderId")).isEqualTo(first.text("razorpayOrderId"));

        List<LoggedRequest> orders = wireMock.findAll(postRequestedFor(urlEqualTo("/v1/orders")));
        assertThat(orders).hasSize(1);
        assertThat(orders.getFirst().getHeader("Authorization")).startsWith("Basic ");
        assertThat(orders.getFirst().getBodyAsString()).contains("\"amount\":30000", "\"currency\":\"INR\"",
                "\"receipt\":\"booking-" + bookingId + "\"");
    }

    @Test
    void sameBookingWithAnotherAmountIsAConflict() {
        long bookingId = newBookingId();
        createPayment(bookingId, 30000);

        assertThat(createPayment(bookingId, 99999).status()).isEqualTo(409);
    }

    @Test
    void razorpayFailureIs502AndSavesNothing() {
        long bookingId = newBookingId();
        wireMock.stubFor(post(urlEqualTo("/v1/orders")).willReturn(aResponse().withStatus(500)));

        Response response = api.post("/payments", backend,
                Map.of("bookingId", bookingId, "customerId", "asha", "amount", 30000));

        assertThat(response.status()).isEqualTo(502);
        assertThat(jdbc.sql("select count(*) from payment.payment where booking_id = :id").param("id", bookingId)
                .query(Long.class).single()).isZero();
    }

    @Test
    void onlyServicesWithThePaymentsRoleGetIn() {
        Map<String, Object> body = Map.of("bookingId", newBookingId(), "customerId", "asha", "amount", 30000);

        assertThat(api.post("/payments", null, body).status()).isEqualTo(401);
        assertThat(api.post("/payments", TestService.withRoles("CUSTOMER"), body).status()).isEqualTo(403);
        assertThat(api.get("/payments/1", TestService.withRoles("ADMIN")).status()).isEqualTo(403);
        assertThat(api.get("/payments/" + Long.MAX_VALUE, backend).status()).isEqualTo(404);
    }

    @Test
    void invalidRequestIs400() {
        Response response = api.post("/payments", backend,
                Map.of("bookingId", newBookingId(), "customerId", "asha", "amount", -5));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("errors").has("amount")).isTrue();
    }

    @Test
    void validCheckoutSignatureCapturesThePayment() {
        Response payment = createPayment(30000);
        String razorpayPaymentId = razorpayPaymentId();

        Response captured = capture(payment, razorpayPaymentId);
        Response again = capture(payment, razorpayPaymentId);

        assertThat(captured.text("status")).isEqualTo("CAPTURED");
        assertThat(captured.text("razorpayPaymentId")).isEqualTo(razorpayPaymentId);
        assertThat(again.text("status")).isEqualTo("CAPTURED");
        assertThat(jdbc.sql("select status from payment.payment_attempt where payment_id = :id")
                .param("id", payment.id()).query(String.class).list()).containsExactly("CAPTURED");
    }

    @Test
    void forgedSignaturesAreRejected() {
        Response payment = createPayment(30000);
        String orderId = payment.text("razorpayOrderId");
        String razorpayPaymentId = razorpayPaymentId();

        Response wrongSignature = verify(payment, orderId, razorpayPaymentId, "0".repeat(64));
        // Genuine signature, but of another order (e.g. a cheaper one the attacker
        // paid)
        Response otherOrder = verify(payment, "order_other", razorpayPaymentId,
                checkoutSignature("order_other", razorpayPaymentId));

        assertThat(wrongSignature.status()).isEqualTo(400);
        assertThat(otherOrder.status()).isEqualTo(400);
        assertThat(status(payment.id())).isEqualTo("CREATED");
    }

    @Test
    void refundIsAskedFromRazorpayOnce() {
        Response payment = createPayment(30000);
        String razorpayPaymentId = razorpayPaymentId();
        capture(payment, razorpayPaymentId);
        stubRefund("rfnd_test1");

        Response refund = refund(payment, "BOOKING_FAILED");
        Response again = refund(payment, "BOOKING_FAILED");

        assertThat(refund.status()).as(refund.toString()).isEqualTo(200);
        assertThat(refund.text("status")).isEqualTo("REFUND_PENDING");
        assertThat(again.text("status")).isEqualTo("REFUND_PENDING");
        List<LoggedRequest> refunds = wireMock
                .findAll(postRequestedFor(urlEqualTo("/v1/payments/" + razorpayPaymentId + "/refund")));
        assertThat(refunds).hasSize(1);
        assertThat(refunds.getFirst().getBodyAsString()).contains("\"amount\":30000");
        assertThat(jdbc.sql("select reason from payment.refund where payment_id = :id").param("id", payment.id())
                .query(String.class).single()).isEqualTo("BOOKING_FAILED");
    }

    @Test
    void unpaidPaymentCannotBeRefunded() {
        Response payment = createPayment(30000);

        assertThat(refund(payment, "BOOKING_CANCELLED").status()).isEqualTo(409);
    }

    @Test
    void refundRefusedByRazorpayCanBeAskedAgain() {
        Response payment = createPayment(30000);
        capture(payment, razorpayPaymentId());
        wireMock.stubFor(post(urlPathMatching("/v1/payments/.+/refund")).willReturn(aResponse().withStatus(400)));

        assertThat(refund(payment, "BOOKING_CANCELLED").status()).isEqualTo(502);
        assertThat(status(payment.id())).isEqualTo("CAPTURED");

        stubRefund("rfnd_test2");
        assertThat(refund(payment, "BOOKING_CANCELLED").text("status")).isEqualTo("REFUND_PENDING");
    }

    private Response verify(Response payment, String orderId, String razorpayPaymentId, String signature) {
        return api.post("/payments/" + payment.id() + "/verify", backend, Map.of("razorpayOrderId", orderId,
                "razorpayPaymentId", razorpayPaymentId, "razorpaySignature", signature));
    }

    private Response refund(Response payment, String reason) {
        return api.post("/payments/" + payment.id() + "/refund", backend, Map.of("reason", reason));
    }

    private static void stubRefund(String refundId) {
        wireMock.stubFor(post(urlPathMatching("/v1/payments/.+/refund")).willReturn(okJson("""
                {"id": "%s", "entity": "refund", "status": "pending"}
                """.formatted(refundId))));
    }
}
