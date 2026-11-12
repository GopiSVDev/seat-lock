package com.seatlock.backend.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserTest {

    @Test
    void usesSubjectAndName() {
        CurrentUser user = CurrentUser
                .from(jwt().claim("name", "Asha Kumar").claim("preferred_username", "asha").build());

        assertThat(user).isEqualTo(new CurrentUser("sub-1", "Asha Kumar"));
    }

    @Test
    void fallsBackToUsernameWhenNameIsMissing() {
        assertThat(CurrentUser.from(jwt().claim("preferred_username", "asha").build()).name()).isEqualTo("asha");
    }

    @Test
    void fallsBackToSubjectWhenNoNameAtAll() {
        assertThat(CurrentUser.from(jwt().build()).name()).isEqualTo("sub-1");
    }

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("token").header("alg", "none").subject("sub-1");
    }
}
