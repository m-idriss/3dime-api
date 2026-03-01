package com.dime.api.feature.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@ApplicationScoped
public class FirestoreCacheService {

    private static final String COLLECTION = "cache";

    @Inject
    Firestore firestore;

    @Inject
    ObjectMapper objectMapper;

    public <T> Optional<T> read(String key, Class<T> type) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(key).get().get();
            if (doc.exists()) {
                String json = doc.getString("data");
                if (json != null) {
                    return Optional.of(objectMapper.readValue(json, type));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read cache from Firestore for key: {}", key, e);
        }
        return Optional.empty();
    }

    public <T> Optional<T> read(String key, TypeReference<T> type) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(key).get().get();
            if (doc.exists()) {
                String json = doc.getString("data");
                if (json != null) {
                    return Optional.of(objectMapper.readValue(json, type));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read cache from Firestore for key: {}", key, e);
        }
        return Optional.empty();
    }

    public void write(String key, Object data) {
        CompletableFuture.runAsync(() -> {
            try {
                String json = objectMapper.writeValueAsString(data);
                firestore.collection(COLLECTION).document(key)
                        .set(Map.of("data", json, "updatedAt", Timestamp.now())).get();
                log.debug("Written cache to Firestore for key: {}", key);
            } catch (Exception e) {
                log.warn("Failed to write cache to Firestore for key: {}", key, e);
            }
        });
    }
}
