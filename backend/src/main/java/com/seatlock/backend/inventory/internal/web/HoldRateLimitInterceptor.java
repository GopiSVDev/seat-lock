package com.seatlock.backend.inventory.internal.web;

import com.seatlock.backend.shared.exception.TooManyRequestsException;
import com.seatlock.backend.shared.ratelimit.RateLimiter;
import com.seatlock.backend.shared.security.CurrentUser;
import io.github.bucket4j.BucketConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

// One bucket per user for both hold endpoints. Keyed by user, not IP: mobile carriers put thousands of phones behind one IP.
@Component
@RequiredArgsConstructor
public class HoldRateLimitInterceptor implements HandlerInterceptor {

    // A buyer changing seats a few times never notices; a script gets one hold every 6 seconds
    private static final int BURST = 10;
    private static final BucketConfiguration HOLDS = BucketConfiguration.builder()
            .addLimit(limit -> limit.capacity(BURST).refillGreedy(BURST, Duration.ofMinutes(1))).build();

    private final RateLimiter rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Security runs first, so a hold request without a token never gets here
        if (!HttpMethod.POST.matches(request.getMethod())
                || !(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
            return true;
        }
        rateLimiter.tryAcquire("rate:holds:" + CurrentUser.from(token.getToken()).id(), HOLDS).ifPresent(wait -> {
            throw new TooManyRequestsException("hold requests", wait);
        });
        return true;
    }
}
