package com.seatlock.payment.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

// Real HTTP against the running app, so security filters, error handlers and JSON are all exercised
public class Api {

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final String baseUrl;
    private final JsonMapper json;

    public Api(String baseUrl, JsonMapper json) {
        this.baseUrl = baseUrl;
        this.json = json;
    }

    public Response get(String path, TestService caller) {
        return send("GET", path, headers(caller), null);
    }

    public Response post(String path, TestService caller, Object body) {
        return send("POST", path, headers(caller), json.writeValueAsString(body));
    }

    // Body sent byte for byte, e.g. a signed webhook
    public Response postRaw(String path, String body, Map<String, String> headers) {
        return send("POST", path, headers, body);
    }

    private Response send(String method, String path, Map<String, String> headers, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path));
        headers.forEach(request::header);
        if (body != null) {
            request.header("Content-Type", "application/json").method(method,
                    HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            boolean isJson = response.headers().firstValue("Content-Type").orElse("").contains("json");
            JsonNode node = isJson && !response.body().isBlank() ? json.readTree(response.body()) : null;
            return new Response(response.statusCode(), node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> headers(TestService caller) {
        return caller == null ? Map.of() : Map.of("Authorization", "Bearer " + caller.token());
    }

    public record Response(int status, JsonNode body) {

        public long id() {
            return body.get("id").asLong();
        }

        public String text(String field) {
            return body.get(field).asString();
        }

        @Override
        public String toString() {
            return status + " " + body;
        }
    }
}
