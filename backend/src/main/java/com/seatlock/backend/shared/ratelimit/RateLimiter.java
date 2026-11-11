package com.seatlock.backend.shared.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

// Token buckets in Redis, shared by every backend instance
@Slf4j
@Component
public class RateLimiter {

    private final ProxyManager<String> buckets;

    // Lazy: the Redis connection opens on first use, so the app starts (and fails open) without Redis
    public RateLimiter(@Lazy ProxyManager<String> buckets) {
        this.buckets = buckets;
    }

    // Empty if allowed, else the wait for the next token.
    // Fails open: a limit protects capacity, it never decides who gets a seat.
    public Optional<Duration> tryAcquire(String key, BucketConfiguration limit) {
        try {
            ConsumptionProbe probe = buckets.getProxy(key, () -> limit).tryConsumeAndReturnRemaining(1);
            return probe.isConsumed()
                    ? Optional.empty()
                    : Optional.of(Duration.ofNanos(probe.getNanosToWaitForRefill()));
        } catch (RuntimeException e) {
            log.warn("Rate limit skipped for {}: {}", key, e.toString());
            return Optional.empty();
        }
    }
}
