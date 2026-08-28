package com.clipador.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ConcurrentRequestLimitFilter extends OncePerRequestFilter {
    private static final String UPLOAD_PATH = "/api/v1/videos/upload";
    private final Semaphore requests;
    private final Semaphore uploads;

    public ConcurrentRequestLimitFilter(ApiLimitsProperties properties, MeterRegistry registry) {
        requests = new Semaphore(properties.maxConcurrentRequests());
        uploads = new Semaphore(properties.maxConcurrentUploads());
        Gauge.builder("clipador.http.concurrent.available", requests, Semaphore::availablePermits)
                .description("Available concurrent HTTP request permits")
                .tags(Tags.of("kind", "request"))
                .register(registry);
        Gauge.builder("clipador.http.concurrent.available", uploads, Semaphore::availablePermits)
                .description("Available concurrent HTTP upload permits")
                .tags(Tags.of("kind", "upload"))
                .register(registry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!requests.tryAcquire()) {
            reject(response, "Too many concurrent requests");
            return;
        }
        boolean upload = "POST".equals(request.getMethod()) && UPLOAD_PATH.equals(request.getRequestURI());
        boolean uploadAcquired = !upload || uploads.tryAcquire();
        try {
            if (!uploadAcquired) {
                reject(response, "Too many concurrent uploads");
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            if (upload && uploadAcquired) uploads.release();
            requests.release();
        }
    }

    private void reject(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, "5");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"type\":\"https://clipador.local/problems/429\","
                + "\"title\":\"Capacity limit reached\",\"status\":429,\"detail\":\"" + detail + "\"}";
        response.getWriter().write(body);
    }
}
