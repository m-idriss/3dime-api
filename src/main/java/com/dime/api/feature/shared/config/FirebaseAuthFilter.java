package com.dime.api.feature.shared.config;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Provider
public class FirebaseAuthFilter implements ContainerRequestFilter {

    public static final String FIREBASE_UID = "firebase.uid";
    public static final String FIREBASE_EMAIL = "firebase.email";

    @Inject
    Instance<FirebaseAuth> firebaseAuth;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String authHeader = requestContext.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }

        if (firebaseAuth.isUnsatisfied() || firebaseAuth.get() == null) {
            log.debug("Firebase Auth not available — skipping token validation");
            return;
        }

        String token = authHeader.substring(7);
        try {
            FirebaseToken decodedToken = firebaseAuth.get().verifyIdToken(token);
            requestContext.setProperty(FIREBASE_UID, decodedToken.getUid());
            requestContext.setProperty(FIREBASE_EMAIL, decodedToken.getEmail());
            log.debug("Firebase token verified for user: {}", decodedToken.getUid());
        } catch (FirebaseAuthException e) {
            log.warn("Invalid Firebase token: {}", e.getMessage());
        }
    }
}
