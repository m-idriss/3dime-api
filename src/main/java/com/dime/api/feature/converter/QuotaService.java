package com.dime.api.feature.converter;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@ApplicationScoped
public class QuotaService {

    private static final String COLLECTION_NAME = "users";
    private static final PlanType DEFAULT_PLAN = PlanType.FREE;
    private static final long DEFAULT_QUOTA_LIMIT = 10L;
    private static final Map<PlanType, Long> QUOTA_LIMITS = Map.of(
            PlanType.FREE, DEFAULT_QUOTA_LIMIT,
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
                return new QuotaCheckResult(true, newUser.quotaLimit, newUser.quotaLimit, newUser.getPlanType());
            }

            UserQuota userQuota = document.toObject(UserQuota.class);
            if (userQuota == null) {
                // Fallback if deserialization fails
                return new QuotaCheckResult(true, 0, 0, DEFAULT_PLAN);
            }

            // Check for new month
            if (isNewMonth(userQuota.periodStart)) {
                resetQuota(userId, userQuota.getPlanType());
                userQuota.quotaUsed = 0;
                // Update the quota limit in memory since it was just reset in the database
                userQuota.quotaLimit = getQuotaLimitForPlan(userQuota.getPlanType());
            }

            long limit = userQuota.quotaLimit;
            long remaining = Math.max(0, limit - userQuota.quotaUsed);
            boolean allowed = userQuota.quotaUsed < limit;

            return new QuotaCheckResult(allowed, remaining, limit, userQuota.getPlanType());

        } catch (InterruptedException | ExecutionException e) {
            log.error("Error checking quota for user {}", userId, e);
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

            log.info("Incremented usage for user {}", userId);

            // Async sync to Notion (non-blocking)
            try {
                DocumentSnapshot snapshot = docRef.get().get();
                if (snapshot.exists()) {
                    UserQuota quota = snapshot.toObject(UserQuota.class);
                    if (quota != null) {
                        notionQuotaService.syncToNotion(
                                userId,
                                quota.quotaUsed,
                                quota.getPlanType(),
                                quota.periodStart.toDate().toInstant());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to sync to Notion for user {} (non-blocking)", userId, e);
            }

        } catch (InterruptedException | ExecutionException e) {
            log.error("Error incrementing usage for user {}", userId, e);
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
            log.error("Error fetching quota status for {}", userId, e);
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
        log.info("Created new user {}", userId);
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
        long newLimit = getQuotaLimitForPlan(plan);
        firestore.collection(COLLECTION_NAME).document(userId).update(
                "quotaUsed", 0,
                "quotaLimit", newLimit,
                "periodStart", now,
                "updatedAt", now);
        log.info("Reset quota for user {} with plan {} (limit: {})", userId, plan, newLimit);
    }

    private boolean isNewMonth(Timestamp periodStart) {
        if (periodStart == null)
            return true;

        ZonedDateTime periodDate = periodStart.toDate().toInstant().atZone(ZoneId.of("UTC"));
        ZonedDateTime now = Instant.now().atZone(ZoneId.of("UTC"));

        return periodDate.getMonth() != now.getMonth() || periodDate.getYear() != now.getYear();
    }

    private long getQuotaLimitForPlan(PlanType plan) {
        return QUOTA_LIMITS.getOrDefault(plan, DEFAULT_QUOTA_LIMIT);
    }
}
