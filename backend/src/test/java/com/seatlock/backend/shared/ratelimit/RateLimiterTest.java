package com.seatlock.backend.shared.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisConnectionException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Stopping the shared Redis container would break the other tests, so Redis failing is simulated
class RateLimiterTest {

    private static final BucketConfiguration ONE_PER_MINUTE = BucketConfiguration.builder()
            .addLimit(limit -> limit.capacity(1).refillGreedy(1, Duration.ofMinutes(1))).build();

    @Test
    @SuppressWarnings("unchecked")
    void unreachableRedisLetsTheRequestThrough() {
        ProxyManager<String> buckets = mock(ProxyManager.class);
        when(buckets.getProxy(anyString(), any())).thenThrow(new RedisConnectionException("Unable to connect"));

        assertThat(new RateLimiter(buckets).tryAcquire("rate:test", ONE_PER_MINUTE)).isEmpty();
    }
}
