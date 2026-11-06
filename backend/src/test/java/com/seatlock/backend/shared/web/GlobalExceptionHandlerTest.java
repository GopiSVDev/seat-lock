package com.seatlock.backend.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

// Stopping the shared Redis container would break the other tests, so this one calls the handler directly
class GlobalExceptionHandlerTest {

    @Test
    void unreachableRedisIs503() {
        ProblemDetail problem = new GlobalExceptionHandler()
                .handleUnavailable(new RedisConnectionFailureException("Unable to connect to Redis"));

        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getDetail()).isEqualTo("Service temporarily unavailable");
    }
}
