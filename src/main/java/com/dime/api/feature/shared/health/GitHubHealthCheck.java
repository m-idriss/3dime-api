package com.dime.api.feature.shared.health;

import com.dime.api.feature.github.GitHubClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;

@Readiness
@ApplicationScoped
@Slf4j
public class GitHubHealthCheck implements HealthCheck {

    private static final long CACHE_TTL_MS = 15_000;
    private static final String CHECK_NAME = "github";

    @Inject
    @RestClient
    GitHubClient gitHubClient;

    @ConfigProperty(name = "github.token")
    Optional<String> token;

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
            String authHeader = token.filter(t -> !t.trim().isEmpty())
                    .map(t -> t.startsWith("Bearer ") ? t : "Bearer " + t)
                    .orElse(null);
            gitHubClient.getRateLimit(authHeader);
            long latencyMs = System.currentTimeMillis() - start;
            return HealthCheckResponse.named(CHECK_NAME)
                    .up()
                    .withData("latencyMs", latencyMs)
                    .build();
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("GitHub health check failed (non-critical)", e);
            // Non-critical: return UP with error data to avoid failing readiness
            return HealthCheckResponse.named(CHECK_NAME)
                    .up()
                    .withData("latencyMs", latencyMs)
                    .withData("status", "DOWN")
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
