package com.seatlock.backend.support;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// A logged-in user for tests. The token is "test.<id>.<name>.<roles>" in base64url, so it fits the
// Bearer token syntax; TestInfrastructure's decoder turns it into a JWT like Keycloak's.
public record TestUser(String id, String name, List<String> roles) {

    private static final String PREFIX = "test.";

    public static TestUser customer(String name) {
        return new TestUser(UUID.randomUUID().toString(), name, List.of("CUSTOMER"));
    }

    public static TestUser admin() {
        return new TestUser(UUID.randomUUID().toString(), "Test Admin", List.of("CUSTOMER", "ADMIN"));
    }

    // A service account's token, like Keycloak's client credentials tokens
    public static TestUser service(String clientId, String role) {
        return new TestUser("service-account-" + clientId, clientId, List.of(role));
    }

    public String token() {
        return PREFIX + encode(id) + "." + encode(name) + "." + encode(String.join(",", roles));
    }

    static Jwt decode(String token) {
        if (!token.startsWith(PREFIX)) {
            throw new BadJwtException("Not a test token");
        }
        String[] parts = token.substring(PREFIX.length()).split("\\.");
        Instant now = Instant.now();
        return Jwt.withTokenValue(token).header("alg", "none").subject(decodePart(parts[0]))
                .claim("name", decodePart(parts[1]))
                .claim("realm_access", Map.of("roles", List.of(decodePart(parts[2]).split(",")))).issuedAt(now)
                .expiresAt(now.plusSeconds(300)).build();
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
