package com.seatlock.backend.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Every controller of this app serves under /api/v1, except @InternalEndpoint ones.
// Springdoc's own controllers live in another package and keep their paths.
@Configuration
public class ApiVersionConfig implements WebMvcConfigurer {

    public static final String API_V1 = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_V1, HandlerTypePredicate.forBasePackage("com.seatlock.backend")
                .and(type -> !type.isAnnotationPresent(InternalEndpoint.class)));
    }
}
