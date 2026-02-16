package com.threedime.api.feature.converter;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class QuotaService {

    private static final Logger LOG = Logger.getLogger(QuotaService.class);
    private static final String COLLECTION_NAME = "users";
    private static final PlanType DEFAULT_PLAN = PlanType.FREE;
    private static final Map<PlanType, Long> QUOTA_LIMITS = Map.of(
            PlanType.FREE, 10L,
            PlanType.PRO, 100L,
            PlanType.UNLIMITED, 1000000L);

    @Inject
    Firestore firestore;

    @Inject
    NotionQuotaService notionQuotaService;

    public record QuotaCheckResult(boolean allowed, long remaining, long limit, PlanType plan) {
    }

    public QuotaCheckResult checkQuota(String userId) {
        try {
            DocumentSnapshot document = firestore.collection(COLLECTION_NAME).document(userId).get().get();

            if (!document.exists()) {
                UserQuota newUser = createUser(userId);
                return new QuotaCheckResult(true, newUser.quotaLimit, newUser.quotaLimit, newUser.plan);
            }

            UserQuota userQuota = document.toObject(UserQuota.class);
            if (userQuota == null) {
                // Fallback if deserialization fails
                return new QuotaCheckResult(true, 0, 0, DEFAULT_PLAN);
            }

            // Check for new month
            if (isNewMonth(userQuota.periodStart)) {
                resetQuota(userId, userQuota.plan);
                userQuota.quotaUsed = 0;
            }

            long limit = QUOTA_LIMITS.getOrDefault(userQuota.plan, 10L);
            long remaining = Math.max(0, limit - userQuota.quotaUsed);
            boolean allowed = userQuota.quotaUsed < limit;

            return new QuotaCheckResult(allowed, remaining, limit, userQuota.plan);

        } catch (InterruptedException | ExecutionException e) {
            LOG.errorf(e, "Error checking quota for user %s", userId);
            // Default allow on error to not block users
            return new QuotaCheckResult(true, -1, -1, DEFAULT_PLAN);
        }
    }

    public void incrementUsage(String userId) {
        try {
            DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(userId);

            firestore.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(docRef).get();

                if (!snapshot.exists()) {
                    createUserInTransaction(transaction, docRef);
                } else {
                    transaction.update(docRef, "quotaUsed", FieldValue.increment(1));
                    transaction.update(docRef, "updatedAt", Timestamp.now());
                }
                return null;
            }).get();

            LOG.infof("Incremented usage for user %s", userId);

            // Async sync to Notion (non-blocking)
            try {
                DocumentSnapshot snapshot = docRef.get().get();
                if (snapshot.exists()) {
                    UserQuota quota = snapshot.toObject(UserQuota.class);
                    if (quota != null) {
                        notionQuotaService.syncToNotion(
                                userId,
                                quota.quotaUsed,
                                quota.plan,
                                quota.periodStart.toDate().toInstant());
                    }
                }
            } catch (Exception e) {
                LOG.warnf(e, "Failed to sync to Notion for user %s (non-blocking)", userId);
            }

        } catch (InterruptedException | ExecutionException e) {
            LOG.errorf(e, "Error incrementing usage for user %s", userId);
        }
    }

    public UserQuota getQuotaStatus(String userId) {
        try {
            DocumentSnapshot document = firestore.collection(COLLECTION_NAME).document(userId).get().get();
            if (document.exists()) {
                UserQuota userQuota = document.toObject(UserQuota.class);
                if (userQuota != null && isNewMonth(userQuota.periodStart)) {
                    userQuota.quotaUsed = 0; // Virtual reset for display
                }
                return userQuota;
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error fetching quota status for %s", userId);
        }
        return null;
    }

    private UserQuota createUser(String userId) throws ExecutionException, InterruptedException {
        Timestamp now = Timestamp.now();
        UserQuota newUser = new UserQuota(
                DEFAULT_PLAN,
                0,
                QUOTA_LIMITS.get(DEFAULT_PLAN),
                now,
                now,
                now);
        firestore.collection(COLLECTION_NAME).document(userId).set(newUser).get();
        LOG.infof("Created new user %s", userId);
        return newUser;
    }

    private void createUserInTransaction(Transaction transaction, DocumentReference docRef) {
        Timestamp now = Timestamp.now();
        UserQuota newUser = new UserQuota(
                DEFAULT_PLAN,
                1, // Start with 1 used
                QUOTA_LIMITS.get(DEFAULT_PLAN),
                now,
                now,
                now);
        transaction.set(docRef, newUser);
    }

    private void resetQuota(String userId, PlanType plan) {
        Timestamp now = Timestamp.now();
        firestore.collection(COLLECTION_NAME).document(userId).update(
                "quotaUsed", 0,
                "periodStart", now,
                "updatedAt", now);
        LOG.infof("Reset quota for user %s", userId);
    }

    private boolean isNewMonth(Timestamp periodStart) {
        if (periodStart == null)
            return true;

        ZonedDateTime periodDate = periodStart.toDate().toInstant().atZone(ZoneId.of("UTC"));
        ZonedDateTime now = Instant.now().atZone(ZoneId.of("UTC"));

        return periodDate.getMonth() != now.getMonth() || periodDate.getYear() != now.getYear();
    }
}
