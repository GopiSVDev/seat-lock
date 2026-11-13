package com.seatlock.backend.shared.web;

import com.seatlock.backend.shared.exception.ServiceUnavailableException;
import com.seatlock.backend.shared.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

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

    @Test
    void retryAfterIsRoundedUpToWholeSeconds() {
        ResponseEntity<ProblemDetail> response = new GlobalExceptionHandler()
                .handleTooManyRequests(new TooManyRequestsException("hold requests", Duration.ofMillis(5200)));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("6");
        assertThat(response.getBody().getDetail()).isEqualTo("Too many hold requests, try again in 6 seconds");
    }

    @Test
    void refusedDependencyIs503WithRetryAfter() {
        ResponseEntity<ProblemDetail> response = new GlobalExceptionHandler()
                .handleServiceUnavailable(new ServiceUnavailableException("Payments are unavailable", Duration.ZERO));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("1");
        assertThat(response.getBody().getDetail()).isEqualTo("Payments are unavailable, try again in 1 second");
    }
}
