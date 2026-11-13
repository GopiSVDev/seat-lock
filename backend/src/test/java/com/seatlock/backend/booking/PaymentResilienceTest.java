package com.seatlock.backend.booking;

import com.seatlock.backend.support.Api.Response;
import com.seatlock.backend.support.IntegrationTest;
import com.seatlock.backend.support.TestShow;
import com.seatlock.backend.support.TestUser;
import com.zaxxer.hikari.HikariDataSource;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

// A slow or failing payment service must not take the rest of the backend down with it
class PaymentResilienceTest extends IntegrationTest {

    // Test properties: 20 request threads, at most 10 payment calls at once, breaker open for 1 s
    private static final int THREADS = 20;
    private static final int PAYMENT_CALLS = 10;

    @Autowired
    private DataSource dataSource;

    @Test
    void slowPaymentServiceDoesNotStallUnrelatedReads() throws Exception {
        int checkouts = THREADS + 2;
        TestShow show = newShow();
        List<Long> seats = show.seatIdsByRow().values().stream().flatMap(List::stream).toList();
        List<TestUser> buyers = new ArrayList<>();
        List<String> holds = new ArrayList<>();
        for (int i = 0; i < checkouts; i++) {
            TestUser buyer = TestUser.customer("Buyer " + i);
            buyers.add(buyer);
            holds.add(hold(buyer, show, List.of(seats.get(i))).text("id"));
        }
        stubPaymentServiceSlow(Duration.ofSeconds(5));

        ExecutorService pool = Executors.newFixedThreadPool(checkouts);
        List<Future<Response>> results = new ArrayList<>();
        try {
            for (int i = 0; i < checkouts; i++) {
                int n = i;
                results.add(pool.submit(() -> checkout(buyers.get(n), holds.get(n))));
            }
            // The first 10 checkouts now wait on the payment service; the rest were turned away
            Thread.sleep(1500);

            HttpRequest layout = HttpRequest
                    .newBuilder(URI.create("http://localhost:" + port + "/api/v1/shows/" + show.showId() + "/layout"))
                    .timeout(Duration.ofSeconds(2)).build();
            long start = System.nanoTime();
            HttpResponse<Void> response;
            try {
                response = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().send(layout,
                        HttpResponse.BodyHandlers.discarding());
            } catch (HttpTimeoutException e) {
                fail("The seat map got no answer within 2 s: every request thread is waiting on the payment service");
                return;
            }

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }

        List<Response> responses = new ArrayList<>();
        for (Future<Response> result : results) {
            responses.add(result.get());
        }
        List<Response> busy = responses.stream().filter(r -> r.status() == 503).toList();
        assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(PAYMENT_CALLS);
        assertThat(busy).hasSize(checkouts - PAYMENT_CALLS);
        assertThat(busy).allSatisfy(r -> {
            assertThat(r.text("detail")).isEqualTo("Payments are busy, try again in 5 seconds");
            assertThat(r.header("Retry-After")).isEqualTo("5");
        });
    }

    @Test
    void failingPaymentServiceOpensTheBreakerUntilItRecovers() throws Exception {
        TestShow show = newShow();
        TestUser buyer = TestUser.customer("Buyer");
        String holdId = hold(buyer, show, show.seats("A", 1)).text("id");
        paymentService.stubFor(post(urlEqualTo("/payments")).willReturn(aResponse().withStatus(503)));

        // 5 calls are enough to judge; all failed
        for (int i = 0; i < 5; i++) {
            assertThat(checkout(buyer, holdId).status()).isEqualTo(502);
        }
        Response refused = checkout(buyer, holdId);

        assertThat(refused.status()).isEqualTo(503);
        assertThat(refused.text("detail")).isEqualTo("Payments are unavailable, try again in 1 second");
        assertThat(refused.header("Retry-After")).isEqualTo("1");
        paymentService.verify(5, postRequestedFor(urlEqualTo("/payments")));
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        stubPaymentServiceUp();
        Thread.sleep(1200);
        // Two trial calls succeed: closed again
        assertThat(checkout(buyer, holdId).status()).isEqualTo(200);
        assertThat(checkout(buyer, holdId).status()).isEqualTo(200);
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void rejectedSignaturesDoNotOpenTheBreaker() {
        TestShow show = newShow();
        TestUser buyer = TestUser.customer("Buyer");
        long bookingId = checkout(buyer, hold(buyer, show, show.seats("A", 1)).text("id")).id();
        paymentService.stubFor(
                post(urlEqualTo("/payments/" + PAYMENT_ID + "/verify")).willReturn(aResponse().withStatus(400)));

        for (int i = 0; i < 6; i++) {
            assertThat(pay(buyer, bookingId).status()).isEqualTo(400);
        }

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker().getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void slowRefundHoldsNoDatabaseConnection() throws Exception {
        TestShow show = newShow();
        TestUser buyer = TestUser.customer("Buyer");
        long bookingId = confirm(buyer, hold(buyer, show, show.seats("A", 1)).text("id")).id();
        paymentService
                .stubFor(post(urlPathMatching("/payments/\\d+/refund")).willReturn(okJson("{}").withFixedDelay(2000)));

        CompletableFuture<Response> cancel = CompletableFuture
                .supplyAsync(() -> api.post("/api/v1/bookings/" + bookingId + "/cancel", buyer));
        // The cancel itself takes milliseconds; after 1 s it is waiting on the refund
        Thread.sleep(1000);

        assertThat(cancel).isNotDone();
        assertThat(dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections()).isZero();
        Response cancelled = cancel.get(10, TimeUnit.SECONDS);
        assertThat(cancelled.status()).isEqualTo(200);
        assertThat(cancelled.text("status")).isEqualTo("CANCELLED");
    }

    private CircuitBreaker breaker() {
        return circuitBreakers.circuitBreaker(PAYMENT_BREAKER);
    }
}
