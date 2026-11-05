package com.seatlock.backend.shared.security;

import org.springframework.security.oauth2.jwt.Jwt;

// The caller, from a validated token; id is the token's sub, which never changes
public record CurrentUser(String id, String name) {

    public static CurrentUser from(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = jwt.getClaimAsString("preferred_username");
        }
        return new CurrentUser(jwt.getSubject(), name != null ? name : jwt.getSubject());
    }
}
