package com.dime.api.feature.shared.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class FirebaseConfig {

    @ConfigProperty(name = "gemini.api.key")
    Optional<String> serviceAccountJson;

    @Produces
    @Singleton
    public FirebaseAuth firebaseAuth() {
        if (serviceAccountJson.isEmpty() || serviceAccountJson.get().isBlank()) {
            log.warn("No service account JSON configured — Firebase token validation disabled");
            return null;
        }

        try {
            if (FirebaseApp.getApps().isEmpty()) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(serviceAccountJson.get().getBytes(StandardCharsets.UTF_8)));
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();
                FirebaseApp.initializeApp(options);
            }
            log.info("Firebase Auth initialized for token validation");
            return FirebaseAuth.getInstance();
        } catch (IOException e) {
            log.error("Failed to initialize Firebase Auth", e);
            return null;
        }
    }
}
