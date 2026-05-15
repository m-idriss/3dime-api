package com.dime.api.feature.shared;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FirestoreCompatibilityTest {

    @Test
    void firestoreServiceCreation_doesNotThrowTelemetryLinkageError() {
        assertDoesNotThrow(() -> {
            try (Firestore firestore = FirestoreOptions.newBuilder()
                    .setProjectId("test-project")
                    .setCredentials(GoogleCredentials.create(
                            new AccessToken("test-token", new Date(Long.MAX_VALUE))))
                    .build()
                    .getService()) {
                assertNotNull(firestore);
            }
        });
    }
}


