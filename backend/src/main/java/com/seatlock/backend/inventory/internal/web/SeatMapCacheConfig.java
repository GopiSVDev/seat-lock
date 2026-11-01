package com.seatlock.backend.inventory.internal.web;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

// The two caches need different settings, which spring.cache.caffeine.spec (one spec for all) can't express.
@Configuration
public class SeatMapCacheConfig {

    static final String LAYOUTS = "seatLayouts";
    static final String AVAILABILITY = "seatAvailability";

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(List.of()); // no caches created on demand: an unknown name fails

        // A show's layout never changes, so no expiry; ~6 KB each, 2000 shows ≈ 12 MB
        cacheManager.registerCustomCache(LAYOUTS, Caffeine.newBuilder()
                .maximumSize(2000)
                .recordStats()
                .build());
        cacheManager.registerCustomCache(AVAILABILITY, Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofSeconds(1))
                .recordStats()
                .build());
        return cacheManager;
    }
}
