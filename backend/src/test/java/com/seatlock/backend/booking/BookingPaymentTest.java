package com.seatlock.backend.booking;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.seatlock.backend.booking.internal.service.BookingExpiryJob;
import com.seatlock.backend.support.Api.Response;
import com.seatlock.backend.support.Concurrently;
import com.seatlock.backend.support.IntegrationTest;
import com.seatlock.backend.support.TestShow;
import com.seatlock.backend.support.TestUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

// Checkout → pay → CONFIRMED, and every way it can go wrong. The payment service is WireMock.
class BookingPaymentTest extends IntegrationTest {

    private static final String PAYMENTS = "/payments";
    private static final String VERIFY = "/payments/" + PAYMENT_ID + "/verify";
    private static final String REFUND = "/payments/" + PAYMENT_ID + "/refund";

    private final TestUser asha = TestUser.customer("Asha Kumar");
    private final TestUser ravi = TestUser.customer("Ravi Shankar");
    private final TestUser paymentServiceAccount = TestUser.service("seatlock-payment", "BOOKING_CALLBACK");

    @Autowired
    private BookingExpiryJob expiryJob;

    @Test
    void checkoutStartsPaymentWithoutBookingTheSeats() {
        TestShow show = newShow();
        List<Long> seats = show.seats("A", 2);
        String holdId = hold(asha, show, seats).text("id");

        Response booking = checkout(asha, holdId);

        assertThat(booking.status()).as(booking.toString()).isEqualTo(201);
        assertThat(booking.header("Location")).isEqualTo("/bookings/" + booking.id());
        assertThat(booking.text("status")).isEqualTo("PENDING_PAYMENT");
        assertThat(booking.body().get("totalAmount").asLong()).isEqualTo(2 * REGULAR_PRICE);
        JsonNode checkout = booking.body().get("checkout");
        assertThat(checkout.get("razorpayOrderId").asString()).isEqualTo("order_test");
        assertThat(checkout.get("keyId").asString()).isEqualTo("rzp_test_key");
        assertThat(seatStatuses(seats)).containsOnly("AVAILABLE");

        // The 5-minute hold now lasts the 10-minute payment window
        Instant deadline = Instant.parse(booking.text("paymentExpiresAt"));
        assertThat(deadline).isBetween(Instant.now().plus(9, ChronoUnit.MINUTES),
                Instant.now().plus(11, ChronoUnit.MINUTES));
        assertThat(redis.getExpire("hold:" + holdId, TimeUnit.SECONDS)).isGreaterThan(9 * 60);
        assertThat(redis.getExpire("seat:" + show.showId() + ":" + seats.getFirst(), TimeUnit.SECONDS))
                .isGreaterThan(9 * 60);

        List<LoggedRequest> calls = paymentService.findAll(postRequestedFor(urlEqualTo(PAYMENTS)));
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().getHeader("Authorization")).isEqualTo("Bearer " + SERVICE_TOKEN);
        assertThat(calls.getFirst().getBodyAsString()).contains("\"bookingId\":" + booking.id(),
                "\"amount\":" + 2 * REGULAR_PRICE, "\"customerId\":\"" + asha.id() + "\"");
    }

    @Test
    void checkingOutAgainFindsTheSameBooking() {
        TestShow show = newShow();
        String holdId = hold(asha, show, show.seats("A", 1)).text("id");

        Response first = checkout(asha, holdId);
        Response again = checkout(asha, holdId);

        assertThat(again.status()).isEqualTo(200);
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.body().get("checkout").isNull()).isFalse();
        assertThat(bookingsOfHold(holdId)).isEqualTo(1);
    }

    @Test
    void paymentServiceDownIs502AndCheckoutCanBeRetried() {
        TestShow show = newShow();
        String holdId = hold(asha, show, show.seats("A", 1)).text("id");
        paymentService.stubFor(post(urlEqualTo(PAYMENTS)).willReturn(aResponse().withStatus(503)));

        assertThat(checkout(asha, holdId).status()).isEqualTo(502);
        long bookingId = jdbc.sql("select id from booking.booking where hold_id = :holdId").param("holdId", holdId)
                .query(Long.class).single();
        Response tooEarly = pay(asha, bookingId);
        assertThat(tooEarly.status()).isEqualTo(409);
        assertThat(tooEarly.text("detail")).isEqualTo("Payment has not been started");

        stubPaymentServiceUp();
        Response retry = checkout(asha, holdId);

        assertThat(retry.status()).isEqualTo(200);
        assertThat(retry.id()).isEqualTo(bookingId);
        assertThat(retry.body().get("checkout").get("razorpayOrderId").asString()).isEqualTo("order_test");
        assertThat(bookingsOfHold(holdId)).isEqualTo(1);
    }

    @Test
    void verifiedPaymentConfirmsAndBooksTheSeats() {
        TestShow show = newShow();
        List<Long> seats = show.seats("B", 2);
        String holdId = hold(asha, show, seats).text("id");
        long bookingId = checkout(asha, holdId).id();

        Response paid = pay(asha, bookingId);

        assertThat(paid.status()).as(paid.toString()).isEqualTo(200);
        assertThat(paid.text("status")).isEqualTo("CONFIRMED");
        assertThat(paid.body().get("paymentExpiresAt").isNull()).isTrue();
        assertThat(seatStatuses(seats)).containsOnly("BOOKED");
        assertThat(redis.hasKey("hold:" + holdId)).isFalse();
        assertThat(paymentService.findAll(postRequestedFor(urlEqualTo(VERIFY))).getFirst().getBodyAsString())
                .contains("\"razorpayPaymentId\":\"pay_test\"", "\"razorpaySignature\":\"sig\"");
    }

    @Test
    void rejectedSignatureLeavesTheBookingUnpaid() {
        TestShow show = newShow();
        List<Long> seats = show.seats("A", 1);
        long bookingId = checkout(asha, hold(asha, show, seats).text("id")).id();
        paymentService.stubFor(post(urlEqualTo(VERIFY)).willReturn(aResponse().withStatus(400)));

        Response response = pay(asha, bookingId);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("detail")).isEqualTo("Invalid payment signature");
        assertThat(status(bookingId)).isEqualTo("PENDING_PAYMENT");
        assertThat(seatStatuses(seats)).containsOnly("AVAILABLE");
    }

    @Test
    void paymentServiceCallbackConfirmsOnce() {
        TestShow show = newShow();
        List<Long> seats = show.seats("A", 2);
        long bookingId = checkout(asha, hold(asha, show, seats).text("id")).id();

        assertThat(paidCallback(paymentServiceAccount, bookingId, PAYMENT_ID).status()).isEqualTo(204);
        assertThat(paidCallback(paymentServiceAccount, bookingId, PAYMENT_ID).status()).isEqualTo(204);

        assertThat(status(bookingId)).isEqualTo("CONFIRMED");
        assertThat(seatStatuses(seats)).containsOnly("BOOKED");
        assertThat(paidCallback(paymentServiceAccount, bookingId, 999).status()).isEqualTo(409);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsSayWhoConfirmedThePayment(CapturedOutput output) {
        TestShow show = newShow();
        long bookingId = checkout(asha, hold(asha, show, show.seats("B", 1)).text("id")).id();

        assertThat(pay(asha, bookingId).status()).isEqualTo(200);
        assertThat(paidCallback(paymentServiceAccount, bookingId, PAYMENT_ID).status()).isEqualTo(204);

        assertThat(output.getAll()).contains("Booking " + bookingId + " started from hold",
                "Booking " + bookingId + " confirmed (reported by browser)",
                "Booking " + bookingId + " is already CONFIRMED; webhook report changes nothing");
    }

    @Test
    void internalEndpointIsOnlyForThePaymentService() {
        TestShow show = newShow();
        long bookingId = checkout(asha, hold(asha, show, show.seats("A", 1)).text("id")).id();

        assertThat(paidCallback(null, bookingId, PAYMENT_ID).status()).isEqualTo(401);
        assertThat(paidCallback(asha, bookingId, PAYMENT_ID).status()).isEqualTo(403);
        assertThat(paidCallback(admin, bookingId, PAYMENT_ID).status()).isEqualTo(403);
        assertThat(
                paidCallback(TestUser.service("seatlock-backend", "PAYMENTS_CLIENT"), bookingId, PAYMENT_ID).status())
                .isEqualTo(403);
        assertThat(status(bookingId)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void browserAndWebhookRacingConfirmOnce() {
        TestShow show = newShow();
        List<Long> seats = show.seats("C", 2);
        long bookingId = checkout(asha, hold(asha, show, seats).text("id")).id();

        List<Response> responses = Concurrently.run(10,
                n -> n % 2 == 0 ? pay(asha, bookingId) : paidCallback(paymentServiceAccount, bookingId, PAYMENT_ID));

        assertThat(responses).extracting(Response::status).containsOnly(200, 204);
        assertThat(status(bookingId)).isEqualTo("CONFIRMED");
        assertThat(seatStatuses(seats)).containsOnly("BOOKED");
        assertThat(paymentService.findAll(postRequestedFor(urlEqualTo(REFUND)))).isEmpty();
    }

    @Test
    void paidAfterTheHoldWasLostFailsAndRefunds() {
        TestShow show = newShow();
        List<Long> seats = show.seats("A", 2);
        String holdId = hold(asha, show, seats).text("id");
        long bookingId = checkout(asha, holdId).id();
        // The hold is gone (e.g. expired) and someone else has the seats now
        api.send("DELETE", "/holds/" + holdId, asha.token(), null);
        assertThat(hold(ravi, show, seats).status()).isEqualTo(201);

        Response paid = pay(asha, bookingId);
        Response again = pay(asha, bookingId);

        assertThat(paid.status()).isEqualTo(200);
        assertThat(paid.text("status")).isEqualTo("FAILED");
        assertThat(again.text("status")).isEqualTo("FAILED");
        assertThat(seatStatuses(seats)).containsOnly("AVAILABLE");
        List<LoggedRequest> refunds = paymentService.findAll(postRequestedFor(urlEqualTo(REFUND)));
        assertThat(refunds).hasSize(1);
        assertThat(refunds.getFirst().getBodyAsString()).contains("\"reason\":\"BOOKING_FAILED\"");
        // Money was taken, so it shows up in my bookings
        assertThat(ids(api.get("/bookings", asha).body().get("items"), "id")).containsExactly(bookingId);
    }

    @Test
    void unpaidBookingsExpireAndStayOutOfMyBookings() {
        TestShow show = newShow();
        long overdue = checkout(asha, hold(asha, show, show.seats("A", 1)).text("id")).id();
        jdbc.sql("update booking.booking set payment_expires_at = now() - interval '1 minute' where id = :id")
                .param("id", overdue).update();

        expiryJob.expireOverdue();

        Response booking = api.get("/bookings/" + overdue, asha);
        assertThat(booking.text("status")).isEqualTo("EXPIRED");
        assertThat(booking.body().get("paymentExpiresAt").isNull()).isTrue();
        assertThat(jdbc.sql("select status from booking.booking_seat where booking_id = :id").param("id", overdue)
                .query(String.class).list()).containsOnly("EXPIRED");
        assertThat(api.get("/bookings", asha).body().get("totalItems").asLong()).isZero();
    }

    @Test
    void sweepLeavesBookingsWithTimeLeftAlone() {
        TestShow show = newShow();
        long bookingId = checkout(asha, hold(asha, show, show.seats("A", 1)).text("id")).id();

        expiryJob.expireOverdue();

        assertThat(status(bookingId)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void lateWebhookForAnExpiredBookingRefunds() {
        TestShow show = newShow();
        String holdId = hold(asha, show, show.seats("A", 1)).text("id");
        long bookingId = checkout(asha, holdId).id();
        jdbc.sql("update booking.booking set payment_expires_at = now() - interval '1 minute' where id = :id")
                .param("id", bookingId).update();
        expiryJob.expireOverdue();
        api.send("DELETE", "/holds/" + holdId, asha.token(), null);

        assertThat(paidCallback(paymentServiceAccount, bookingId, PAYMENT_ID).status()).isEqualTo(204);

        assertThat(status(bookingId)).isEqualTo("FAILED");
        assertThat(paymentService.findAll(postRequestedFor(urlEqualTo(REFUND)))).hasSize(1);
    }

    @Test
    void cancellingAPaidBookingRefundsIt() {
        TestShow show = newShow();
        long bookingId = book(asha, show, show.seats("A", 1)).id();

        Response cancelled = api.post("/bookings/" + bookingId + "/cancel", asha);

        assertThat(cancelled.text("status")).isEqualTo("CANCELLED");
        List<LoggedRequest> refunds = paymentService.findAll(postRequestedFor(urlEqualTo(REFUND)));
        assertThat(refunds).hasSize(1);
        assertThat(refunds.getFirst().getBodyAsString()).contains("\"reason\":\"BOOKING_CANCELLED\"");
    }

    @Test
    void unpaidBookingCannotBeCancelled() {
        TestShow show = newShow();
        long bookingId = checkout(asha, hold(asha, show, show.seats("A", 1)).text("id")).id();

        Response response = api.post("/bookings/" + bookingId + "/cancel", asha);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.text("detail")).isEqualTo("Only confirmed bookings can be cancelled");
    }

    private Response paidCallback(TestUser caller, long bookingId, long paymentId) {
        return api.post("/internal/bookings/" + bookingId + "/paid", caller, Map.of("paymentId", paymentId));
    }

    private String status(long bookingId) {
        return jdbc.sql("select status from booking.booking where id = :id").param("id", bookingId).query(String.class)
                .single();
    }

    private long bookingsOfHold(String holdId) {
        return jdbc.sql("select count(*) from booking.booking where hold_id = :holdId").param("holdId", holdId)
                .query(Long.class).single();
    }
}
