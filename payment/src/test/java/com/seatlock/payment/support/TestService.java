package com.seatlock.payment.support;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

// A calling service for tests. The token is "test.<client>.<roles>" in base64url, so it fits the
// Bearer token syntax; TestInfrastructure's decoder turns it into a JWT like Keycloak's service account tokens.
public record TestService(String clientId, List<String> roles) {

    private static final String PREFIX = "test.";

    public static TestService backend() {
        return new TestService("seatlock-backend", List.of("PAYMENTS_CLIENT"));
    }

    public static TestService withRoles(String... roles) {
        return new TestService("some-client", List.of(roles));
    }

    public String token() {
        return PREFIX + encode(clientId) + "." + encode(String.join(",", roles));
    }

    static Jwt decode(String token) {
        if (!token.startsWith(PREFIX)) {
            throw new BadJwtException("Not a test token");
        }
        String[] parts = token.substring(PREFIX.length()).split("\\.", -1);
        Instant now = Instant.now();
        return Jwt.withTokenValue(token).header("alg", "none").subject("service-account-" + decodePart(parts[0]))
                .claim("azp", decodePart(parts[0]))
                .claim("realm_access", Map.of("roles", List.of(decodePart(parts[1]).split(",")))).issuedAt(now)
                .expiresAt(now.plusSeconds(300)).build();
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
