package com.seatlock.backend.shared.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    // Reuses Spring's Lettuce client (same host, port, Testcontainers wiring); Bucket4j needs byte[] values
    @Bean
    @Lazy
    StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(LettuceConnectionFactory factory) {
        RedisClient client = (RedisClient) factory.getRequiredNativeClient();
        return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    @Bean
    @Lazy
    ProxyManager<String> rateLimitBuckets(StatefulRedisConnection<String, byte[]> rateLimitRedisConnection) {
        return Bucket4jLettuce.casBasedBuilder(rateLimitRedisConnection)
                // A bucket key lives until the bucket is full again, then Redis drops it
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                // Lettuce queues commands while Redis is down; don't let a request wait on that
                .requestTimeout(Duration.ofSeconds(1))
                .build();
    }
}
