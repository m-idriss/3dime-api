package com.dime.api.feature.shared.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiter for login attempts to prevent brute-force attacks.
 * Allows 5 login attempts per IP per 5 minutes.
 */
@Slf4j
@Provider
public class LoginRateLimitFilter implements ContainerRequestFilter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MINUTES = 5;
    private static final long WINDOW_MS = TimeUnit.MINUTES.toMillis(WINDOW_MINUTES);

    private static final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> attemptsByIp = new ConcurrentHashMap<>();

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();

        // Only apply rate limiting to login endpoint
        if (!"/j_security_check".equals(path)) {
            return;
        }

        // Only rate limit POST requests (actual login attempts)
        if (!"POST".equals(requestContext.getMethod())) {
            return;
        }

        String clientIp = getClientIp(requestContext);
        long now = System.currentTimeMillis();

        // Get or create attempt queue for this IP
        ConcurrentLinkedQueue<Long> attempts = attemptsByIp.computeIfAbsent(
                clientIp,
                k -> new ConcurrentLinkedQueue<>()
        );

        // Remove old attempts outside the time window
        attempts.removeIf(timestamp -> now - timestamp > WINDOW_MS);

        // Check if limit exceeded
        if (attempts.size() >= MAX_ATTEMPTS) {
            log.warn("Login rate limit exceeded for IP: {} (attempt count: {})", clientIp, attempts.size());
            requestContext.abortWith(
                    Response.status(429)
                            .entity(Map.of(
                                    "success", false,
                                    "error", "Too Many Requests",
                                    "message", "Too many login attempts. Please try again in 5 minutes."
                            ))
                            .build()
            );
            return;
        }

        // Record this attempt
        attempts.add(now);
        log.debug("Login attempt recorded for IP: {} (attempt count: {})", clientIp, attempts.size());
    }

    /**
     * Extract client IP from request, checking for X-Forwarded-For header (Cloud Run proxy).
     */
    private String getClientIp(ContainerRequestContext requestContext) {
        String forwardedFor = requestContext.getHeaderString("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs; take the first one
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = requestContext.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }

        // Fallback to direct connection IP
        return requestContext.getSecurityContext().getUserPrincipal() != null
                ? requestContext.getSecurityContext().getUserPrincipal().getName()
                : "unknown";
    }
}
