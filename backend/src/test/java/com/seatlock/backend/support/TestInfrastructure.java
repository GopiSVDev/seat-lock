package com.seatlock.backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

    // One real Postgres for the whole run (Spring caches the context); Flyway applies every migration
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }

    // Replaces the Keycloak-backed decoder: test tokens carry the user, so no Keycloak is needed
    @Bean
    JwtDecoder testJwtDecoder() {
        return TestUser::decode;
    }
}
