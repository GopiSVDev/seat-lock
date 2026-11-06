package com.seatlock.backend.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// Real HTTP against the running app, so security filters, error handlers and JSON are all exercised
public class Api {

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final String baseUrl;
    private final JsonMapper json;

    public Api(String baseUrl, JsonMapper json) {
        this.baseUrl = baseUrl;
        this.json = json;
    }

    public Response get(String path, TestUser user) {
        return send("GET", path, tokenOf(user), null);
    }

    public Response post(String path, TestUser user, Object body) {
        return send("POST", path, tokenOf(user), body);
    }

    public Response post(String path, TestUser user) {
        return send("POST", path, tokenOf(user), null);
    }

    public Response send(String method, String path, String bearerToken, Object body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        if (body != null) {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            boolean isJson = response.headers().firstValue("Content-Type").orElse("").contains("json");
            JsonNode node = isJson && !response.body().isBlank() ? json.readTree(response.body()) : null;
            return new Response(response.statusCode(), node, response.headers());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String tokenOf(TestUser user) {
        return user == null ? null : user.token();
    }

    public record Response(int status, JsonNode body, HttpHeaders headers) {

        public String header(String name) {
            return headers.firstValue(name).orElse(null);
        }

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
