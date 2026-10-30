package com.dime.api.feature.subscription;

import com.dime.api.feature.converter.PlanType;
import com.dime.api.feature.converter.UserQuota;
import com.dime.api.feature.shared.exception.DatastoreUnavailableException;
import com.dime.api.feature.shared.exception.ValidationException;
import com.google.cloud.firestore.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** One durable checkout slot per account; only a provider-confirmed expired session can be replaced. */
@ApplicationScoped
public class SubscriptionCheckoutStore {
    @Inject Instance<Firestore> firestoreInstance;

    public static class Slot {
        public String token;
        public String billingCycle;
        public String priceId;
        public String email;
        public String sessionId;
        public long createdAt;
        public Slot() {}
        Slot(String cycle, String price, String email) {
            token = UUID.randomUUID().toString(); billingCycle = cycle; priceId = price;
            this.email = email; createdAt = Instant.now().getEpochSecond();
        }
    }

    public Slot acquire(String uid, String cycle, String price, String email) {
        return change(uid, null, cycle, price, email);
    }

    public Slot replaceExpired(String uid, String previousToken, String cycle, String price, String email) {
        return change(uid, previousToken, cycle, price, email);
    }

    private Slot change(String uid, String previousToken, String cycle, String price, String email) {
        try {
            Firestore db = firestoreInstance.get();
            DocumentReference user = db.collection("users").document(uid);
            DocumentReference ref = user.collection("billingCheckout").document("subscription");
            return db.runTransaction(tx -> {
                DocumentSnapshot userDoc = tx.get(user).get();
                DocumentSnapshot checkoutDoc = tx.get(ref).get();
                UserQuota quota = userDoc.exists() ? userDoc.toObject(UserQuota.class) : null;
                if (userDoc.exists() && quota == null) throw new IllegalStateException("Invalid quota record");
                if (quota != null && (quota.stripeSubscriptionId != null || quota.getPlanType() != PlanType.FREE))
                    throw new ValidationException("Manage your existing subscription before starting another one.");
                Slot slot = checkoutDoc.exists() ? checkoutDoc.toObject(Slot.class) : null;
                if (checkoutDoc.exists() && (slot == null || slot.token == null))
                    throw new IllegalStateException("Invalid checkout record");
                if (previousToken == null && slot != null) return slot;
                if (previousToken != null && (slot == null || !previousToken.equals(slot.token)))
                    throw new ValidationException("Checkout changed. Please retry.");
                Slot next = new Slot(cycle, price, email);
                tx.set(ref, next);
                return next;
            }).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null && !(cause instanceof ValidationException)) cause = cause.getCause();
            if (cause instanceof ValidationException validation) throw validation;
            throw new DatastoreUnavailableException("Unable to reserve subscription checkout.", e);
        }
    }

    public void saveSession(String uid, String token, String sessionId) {
        try {
            Firestore db = firestoreInstance.get();
            DocumentReference ref = db.collection("users").document(uid).collection("billingCheckout").document("subscription");
            db.runTransaction(tx -> {
                Slot slot = tx.get(ref).get().toObject(Slot.class);
                if (slot == null || !token.equals(slot.token)) throw new IllegalStateException("Checkout changed");
                if (slot.sessionId != null && !sessionId.equals(slot.sessionId)) throw new IllegalStateException("Checkout session mismatch");
                tx.update(ref, "sessionId", sessionId);
                return null;
            }).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new DatastoreUnavailableException("Unable to save subscription checkout.", e);
        }
    }
}
