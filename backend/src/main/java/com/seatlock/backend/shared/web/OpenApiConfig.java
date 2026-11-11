package com.seatlock.backend.shared.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String KEYCLOAK = "keycloak";

    // Browser-facing issuer: Swagger UI redirects the user's browser there to log in
    @Bean
    OpenAPI seatLockOpenApi(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        OAuthFlow authorizationCode = new OAuthFlow().authorizationUrl(issuer + "/protocol/openid-connect/auth")
                .tokenUrl(issuer + "/protocol/openid-connect/token")
                .scopes(new Scopes().addString("openid", "OpenID Connect"));

        return new OpenAPI()
                .info(new Info().title("SeatLock API").version("v1")
                        .description("Movie ticket booking: catalogue (movies, theatres, screens, shows), "
                                + "seat inventory and bookings. Never sells the same seat twice. "
                                + "GET on catalogue and seat maps is public; creating catalogue data needs ADMIN; "
                                + "bookings need a logged-in user (Authorize)."))
                .components(new Components().addSecuritySchemes(KEYCLOAK,
                        new SecurityScheme().type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows().authorizationCode(authorizationCode))))
                .addSecurityItem(new SecurityRequirement().addList(KEYCLOAK));
    }
}
