package com.seatlock.backend.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// One line per request, outside Spring Security so 401/403 are logged too. The address is the client's,
// taken from the gateway's X-Forwarded-For (server.forward-headers-strategy).
// Never headers or bodies: they carry tokens and signatures.
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Health checks and metric scrapes every few seconds would drown everything else
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            String query = request.getQueryString();
            log.info("{} {} {} -> {} ({} ms)", request.getRemoteAddr(), request.getMethod(),
                    query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query,
                    response.getStatus(), (System.nanoTime() - start) / 1_000_000);
        }
    }
}
