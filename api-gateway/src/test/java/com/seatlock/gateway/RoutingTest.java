package com.seatlock.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

// The backend and the payment service are WireMock servers; the gateway in between is the real one
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RoutingTest {

    private static final WireMockServer backend = new WireMockServer(options().dynamicPort());
    private static final WireMockServer payment = new WireMockServer(options().dynamicPort());

    static {
        backend.start();
        payment.start();
    }

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("backend.base-url", backend::baseUrl);
        registry.add("payment.base-url", payment::baseUrl);
    }

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @BeforeEach
    void resetServices() {
        backend.resetAll();
        payment.resetAll();
        backend.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
        payment.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
    }

    @Test
    void apiRequestsReachTheBackendUnchanged() throws Exception {
        backend.stubFor(post("/api/v1/holds?source=app")
                .willReturn(aResponse().withStatus(201).withHeader("Location", "/api/v1/holds/h1")
                        .withHeader("Content-Type", "application/json").withBody("{\"id\":\"h1\"}")));

        HttpResponse<String> response = send(HttpRequest.newBuilder(url("/api/v1/holds?source=app"))
                .header("Authorization", "Bearer token-1").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"showId\":1,\"showSeatIds\":[2,3]}")));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location")).hasValue("/api/v1/holds/h1");
        assertThat(response.body()).isEqualTo("{\"id\":\"h1\"}");
        backend.verify(postRequestedFor(urlEqualTo("/api/v1/holds?source=app"))
                .withHeader("Authorization", equalTo("Bearer token-1"))
                .withRequestBody(equalToJson("{\"showId\":1,\"showSeatIds\":[2,3]}")));
        assertThat(payment.getAllServeEvents()).isEmpty();
    }

    @Test
    void backendErrorsPassThrough() throws Exception {
        backend.stubFor(get("/api/v1/bookings/9").willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/problem+json").withBody("{\"status\":404}")));

        HttpResponse<String> response = send(HttpRequest.newBuilder(url("/api/v1/bookings/9")));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(response.body()).isEqualTo("{\"status\":404}");
    }

    @Test
    void toolingPagesGoToTheBackendAndRedirectsGoBackToTheBrowser() throws Exception {
        backend.stubFor(get("/swagger-ui.html")
                .willReturn(aResponse().withStatus(302).withHeader("Location", "/swagger-ui/index.html")));

        HttpResponse<String> swagger = send(HttpRequest.newBuilder(url("/swagger-ui.html")));

        assertThat(swagger.statusCode()).isEqualTo(302);
        assertThat(swagger.headers().firstValue("Location")).hasValue("/swagger-ui/index.html");
        for (String path : List.of("/swagger-ui/index.html", "/v3/api-docs", "/dev/checkout.html")) {
            assertThat(send(HttpRequest.newBuilder(url(path))).statusCode()).as(path).isEqualTo(200);
        }
        assertThat(backend.getAllServeEvents()).hasSize(4);
    }

    // The signature covers the raw bytes, so the body must arrive exactly as Razorpay sent it
    @Test
    void webhookReachesThePaymentServiceByteForByte() throws Exception {
        String body = "{ \"event\":\"payment.captured\" ,\n  \"payload\": {} }";

        HttpResponse<String> response = send(
                HttpRequest.newBuilder(url("/webhooks/razorpay")).header("Content-Type", "application/json")
                        .header("X-Razorpay-Signature", "abc123").POST(HttpRequest.BodyPublishers.ofString(body)));

        assertThat(response.statusCode()).isEqualTo(200);
        payment.verify(postRequestedFor(urlEqualTo("/webhooks/razorpay"))
                .withHeader("X-Razorpay-Signature", equalTo("abc123")).withRequestBody(equalTo(body)));
        assertThat(backend.getAllServeEvents()).isEmpty();
    }

    @Test
    void internalAndOperationsPathsAreNotRouted(CapturedOutput output) throws Exception {
        for (String path : List.of("/internal/bookings/1/paid", "/payments", "/payments/1/refund")) {
            assertThat(send(HttpRequest.newBuilder(url(path)).POST(HttpRequest.BodyPublishers.noBody())).statusCode())
                    .as(path).isEqualTo(404);
        }
        for (String path : List.of("/actuator/health", "/actuator/prometheus", "/api/v2/movies", "/cities")) {
            assertThat(send(HttpRequest.newBuilder(url(path))).statusCode()).as(path).isEqualTo(404);
        }

        assertThat(backend.getAllServeEvents()).isEmpty();
        assertThat(payment.getAllServeEvents()).isEmpty();
        awaitLogged(output, "POST /internal/bookings/1/paid -> 404");
    }

    @Test
    void servicesLearnWhoCalledThroughForwardedHeaders() throws Exception {
        send(HttpRequest.newBuilder(url("/api/v1/movies")));

        backend.verify(
                getRequestedFor(urlEqualTo("/api/v1/movies")).withHeader("X-Forwarded-For", containing("127.0.0.1"))
                        .withHeader("X-Forwarded-Proto", equalTo("http")));
    }

    @Test
    void serviceDownIs502() throws Exception {
        backend.stubFor(get("/api/v1/movies").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        HttpResponse<String> response = send(HttpRequest.newBuilder(url("/api/v1/movies")));

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(response.body()).contains("Service unavailable");
    }

    @Test
    void serviceTooSlowIs504() throws Exception {
        backend.stubFor(get("/api/v1/movies").willReturn(aResponse().withStatus(200).withFixedDelay(3000)));

        HttpResponse<String> response = send(HttpRequest.newBuilder(url("/api/v1/movies")));

        assertThat(response.statusCode()).isEqualTo(504);
        assertThat(response.body()).contains("Service took too long to answer");
    }

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    // Request lines are written once the response is done, which can be just after the client has it
    private static void awaitLogged(CapturedOutput output, String line) throws InterruptedException {
        for (int i = 0; i < 40 && !output.getAll().contains(line); i++) {
            Thread.sleep(50);
        }
        assertThat(output.getAll()).contains(line);
    }
}
