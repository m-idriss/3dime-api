package com.dime.api.feature.shared.health;

import com.dime.api.feature.converter.GeminiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
@Slf4j
public class GeminiHealthCheck implements HealthCheck {

    private static final long CACHE_TTL_MS = 15_000;
    private static final String CHECK_NAME = "gemini";

    @Inject
    GeminiService geminiService;

    volatile HealthCheckResponse cachedResponse;
    volatile long lastCheckedAt = 0;

    @Override
    public HealthCheckResponse call() {
        long now = System.currentTimeMillis();
        if (cachedResponse != null && (now - lastCheckedAt) < CACHE_TTL_MS) {
            return cachedResponse;
        }
        cachedResponse = doCheck();
        lastCheckedAt = System.currentTimeMillis();
        return cachedResponse;
    }

    HealthCheckResponse doCheck() {
        long start = System.currentTimeMillis();
        try {
            geminiService.ping();
            long latencyMs = System.currentTimeMillis() - start;
            return HealthCheckResponse.named(CHECK_NAME)
                    .up()
                    .withData("latencyMs", latencyMs)
                    .build();
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("Gemini health check failed", e);
            return HealthCheckResponse.named(CHECK_NAME)
                    .down()
                    .withData("latencyMs", latencyMs)
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
