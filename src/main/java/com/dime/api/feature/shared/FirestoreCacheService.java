package com.dime.api.feature.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@ApplicationScoped
public class FirestoreCacheService {

    private static final String COLLECTION = "cache";

    @Inject
    Instance<Firestore> firestoreInstance;

    @Inject
    ObjectMapper objectMapper;

    Firestore firestore() {
        return firestoreInstance.get();
    }

    public <T> Optional<T> read(String key, Class<T> type) {
        try {
            DocumentSnapshot doc = firestore().collection(COLLECTION).document(key).get().get(5, TimeUnit.SECONDS);
            if (doc.exists()) {
                String json = doc.getString("data");
                if (json != null) {
                    return Optional.of(objectMapper.readValue(json, type));
                }
            }
        } catch (Exception t) {
            log.warn("Failed to read cache from Firestore for key: {}", key, t);
        }
        return Optional.empty();
    }

    public <T> Optional<T> read(String key, TypeReference<T> type) {
        try {
            DocumentSnapshot doc = firestore().collection(COLLECTION).document(key).get().get(5, TimeUnit.SECONDS);
            if (doc.exists()) {
                String json = doc.getString("data");
                if (json != null) {
                    return Optional.of(objectMapper.readValue(json, type));
                }
            }
        } catch (Exception t) {
            log.warn("Failed to read cache from Firestore for key: {}", key, t);
        }
        return Optional.empty();
    }

    public void write(String key, Object data) {
        CompletableFuture.runAsync(() -> {
            try {
                String json = objectMapper.writeValueAsString(data);
                firestore().collection(COLLECTION).document(key)
                        .set(Map.of("data", json, "updatedAt", Timestamp.now())).get(10, TimeUnit.SECONDS);
                log.debug("Written cache to Firestore for key: {}", key);
            } catch (Exception t) {
                log.warn("Failed to write cache to Firestore for key: {}", key, t);
            }
        });
    }
}
