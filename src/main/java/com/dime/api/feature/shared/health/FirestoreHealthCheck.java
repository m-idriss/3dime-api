package com.dime.api.feature.shared.health;

import com.google.cloud.firestore.Firestore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
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
    Instance<Firestore> firestoreInstance;

    private final Object lock = new Object();
    volatile HealthCheckResponse cachedResponse;
    volatile long lastCheckedAt = 0;

    Firestore firestore() {
        return firestoreInstance.get();
    }

    @Override
    public HealthCheckResponse call() {
        long now = System.currentTimeMillis();
        if (cachedResponse != null && (now - lastCheckedAt) < CACHE_TTL_MS) {
            return cachedResponse;
        }
        synchronized (lock) {
            now = System.currentTimeMillis();
            if (cachedResponse != null && (now - lastCheckedAt) < CACHE_TTL_MS) {
                return cachedResponse;
            }
            cachedResponse = doCheck();
            lastCheckedAt = System.currentTimeMillis();
            return cachedResponse;
        }
    }

    HealthCheckResponse doCheck() {
        long start = System.currentTimeMillis();
        try {
            firestore().collection("users").limit(1).get().get(2, TimeUnit.SECONDS);
            long latencyMs = System.currentTimeMillis() - start;
            return HealthCheckResponse.named(CHECK_NAME)
                    .up()
                    .withData("latencyMs", latencyMs)
                    .build();
        } catch (Throwable t) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("Firestore health check failed", t);
            return HealthCheckResponse.named(CHECK_NAME)
                    .down()
                    .withData("latencyMs", latencyMs)
                    .withData("error", String.valueOf(t.getMessage()))
                    .build();
        }
    }
}
