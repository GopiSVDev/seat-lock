package com.seatlock.backend.inventory.internal.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class HoldWebConfig implements WebMvcConfigurer {

    private final HoldRateLimitInterceptor holdRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(holdRateLimitInterceptor).addPathPatterns("/holds", "/holds/best-available");
    }
}
