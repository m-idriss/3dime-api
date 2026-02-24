package com.dime.api.feature.shared.health;

import com.dime.api.feature.notion.NotionClient;
import com.dime.api.feature.shared.BearerTokenUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Readiness
@ApplicationScoped
@Slf4j
public class NotionHealthCheck implements HealthCheck {

    private static final long CACHE_TTL_MS = 15_000;
    private static final String CHECK_NAME = "notion";

    @Inject
    @RestClient
    NotionClient notionClient;

    @ConfigProperty(name = "notion.token")
    String token;

    @ConfigProperty(name = "notion.version")
    String version;

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
            notionClient.getMe(BearerTokenUtil.ensureBearer(token), version);
            long latencyMs = System.currentTimeMillis() - start;
            return HealthCheckResponse.named(CHECK_NAME)
                    .up()
                    .withData("latencyMs", latencyMs)
                    .build();
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("Notion health check failed (non-critical)", e);
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
