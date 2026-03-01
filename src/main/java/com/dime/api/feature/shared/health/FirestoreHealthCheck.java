package com.dime.api.feature.shared.health;

import com.google.cloud.firestore.Firestore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.util.concurrent.TimeUnit;

@Readiness
@ApplicationScoped
@Slf4j
public class FirestoreHealthCheck implements HealthCheck {

    private static final long CACHE_TTL_MS = 15_000;
    private static final String CHECK_NAME = "firestore";

    @Inject
    Firestore firestore;

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
            firestore.collection("users").limit(1).get().get(2, TimeUnit.SECONDS);
            long latencyMs = System.currentTimeMillis() - start;
            return HealthCheckResponse.named(CHECK_NAME)
                    .up()
                    .withData("latencyMs", latencyMs)
                    .build();
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("Firestore health check failed", e);
            return HealthCheckResponse.named(CHECK_NAME)
                    .down()
                    .withData("latencyMs", latencyMs)
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
