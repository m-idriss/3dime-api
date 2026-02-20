package com.dime.api.feature.converter;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class QuotaService {

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

    public record UserQuotaWrapper(String userId, UserQuota quota) {
    }

    public QuotaCheckResult checkQuota(@NonNull String userId) {
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
                resetQuota(userId);
                userQuota.quotaUsed = 0;
            }

            long limit = QUOTA_LIMITS.getOrDefault(userQuota.getPlanType(), 10L);
            long remaining = Math.max(0, limit - userQuota.quotaUsed);
            boolean allowed = userQuota.quotaUsed < limit;

            return new QuotaCheckResult(allowed, remaining, limit, userQuota.getPlanType());

        } catch (InterruptedException | ExecutionException e) {
            log.error("Error checking quota for user {}", userId, e);
            // Default allow on error to not block users
            return new QuotaCheckResult(true, -1, -1, DEFAULT_PLAN);
        }
    }

    public void incrementUsage(@NonNull String userId) {
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

    public UserQuota getQuotaStatus(@NonNull String userId) {
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

    private UserQuota createUser(@NonNull String userId) throws ExecutionException, InterruptedException {
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

    private void createUserInTransaction(@NonNull Transaction transaction, @NonNull DocumentReference docRef) {
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

    private void resetQuota(@NonNull String userId) {
        Timestamp now = Timestamp.now();
        firestore.collection(COLLECTION_NAME).document(userId).update(
                "quotaUsed", 0,
                "periodStart", now,
                "updatedAt", now);
        log.info("Reset quota for user {}", userId);
    }

    private boolean isNewMonth(Timestamp periodStart) {
        if (periodStart == null)
            return true;

        ZonedDateTime periodDate = periodStart.toDate().toInstant().atZone(ZoneId.of("UTC"));
        ZonedDateTime now = Instant.now().atZone(ZoneId.of("UTC"));

        return periodDate.getMonth() != now.getMonth() || periodDate.getYear() != now.getYear();
    }

    public List<UserQuotaWrapper> findAll() {
        try {
            return firestore.collection(COLLECTION_NAME).get().get().getDocuments()
                    .stream()
                    .map(doc -> new UserQuotaWrapper(doc.getId(), doc.toObject(UserQuota.class)))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error fetching all user quotas", e);
            return List.of();
        }
    }

    public void updateQuota(@NonNull String userId, UserQuota quota) {
        try {
            quota.updatedAt = Timestamp.now();
            firestore.collection(COLLECTION_NAME).document(userId).set(quota, SetOptions.merge()).get();
            log.info("Updated quota for user {}", userId);

            // Sync to Notion after update
            try {
                notionQuotaService.syncToNotion(
                        userId,
                        quota.quotaUsed,
                        quota.getPlanType(),
                        quota.periodStart != null ? quota.periodStart.toDate().toInstant() : Instant.now());
            } catch (Exception e) {
                log.warn("Failed to sync to Notion for user {} after update (non-blocking)", userId, e);
            }

        } catch (InterruptedException | ExecutionException e) {
            log.error("Error updating quota for user {}", userId, e);
        }
    }

    public void deleteQuota(@NonNull String userId) {
        try {
            firestore.collection(COLLECTION_NAME).document(userId).delete().get();
            log.info("Deleted quota for user {}", userId);

            // Delete from Notion after deletion
            try {
                notionQuotaService.deleteFromNotion(userId);
            } catch (Exception e) {
                log.warn("Failed to delete from Notion for user {} (non-blocking)", userId, e);
            }

        } catch (InterruptedException | ExecutionException e) {
            log.error("Error deleting quota for user {}", userId, e);
        }
    }

    public void syncToNotion(List<String> userIds) {
        log.info("Starting targeted quota sync TO Notion for {} users",
                userIds == null || userIds.isEmpty() ? "all" : userIds.size());

        List<UserQuotaWrapper> allQuotas = findAll();

        if (userIds != null && !userIds.isEmpty()) {
            allQuotas = allQuotas.stream()
                    .filter(wrapper -> userIds.contains(wrapper.userId()))
                    .toList();
        }

        for (UserQuotaWrapper wrapper : allQuotas) {
            if (wrapper.quota() != null) {
                try {
                    notionQuotaService.syncToNotion(
                            wrapper.userId(),
                            wrapper.quota().quotaUsed,
                            wrapper.quota().getPlanType(),
                            wrapper.quota().periodStart != null ? wrapper.quota().periodStart.toDate().toInstant()
                                    : Instant.now());
                } catch (Exception e) {
                    log.warn("Failed to sync user {} to Notion during sync", wrapper.userId(), e);
                }
            }
        }
        log.info("Completed quota sync TO Notion for {} users", allQuotas.size());
    }

    public void syncFromNotion(List<String> userIds) {
        log.info("Starting targeted quota sync FROM Notion to Firestore for {} users",
                userIds == null || userIds.isEmpty() ? "all" : userIds.size());

        List<NotionQuotaService.QuotaData> notionData = notionQuotaService.fetchAllFromNotion();

        if (userIds != null && !userIds.isEmpty()) {
            notionData = notionData.stream()
                    .filter(data -> userIds.contains(data.userId()))
                    .toList();
        }

        for (NotionQuotaService.QuotaData data : notionData) {
            try {
                updateQuotaFromNotion(data);
            } catch (Exception e) {
                log.warn("Failed to sync user {} from Notion", data.userId(), e);
            }
        }
        log.info("Completed quota sync FROM Notion for {} records", notionData.size());
    }

    private void updateQuotaFromNotion(@NonNull NotionQuotaService.QuotaData data) {
        try {
            DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(data.userId());
            Timestamp periodStart = Timestamp.ofTimeSecondsAndNanos(data.lastReset().getEpochSecond(),
                    data.lastReset().getNano());

            long limit = QUOTA_LIMITS.getOrDefault(data.plan(), 10L);

            firestore.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(docRef).get();
                Timestamp now = Timestamp.now();

                if (!snapshot.exists()) {
                    UserQuota newUser = new UserQuota(
                            data.plan(),
                            data.usageCount(),
                            limit,
                            periodStart,
                            now,
                            now);
                    transaction.set(docRef, newUser);
                } else {
                    transaction.update(docRef,
                            "plan", data.plan().name(),
                            "quotaUsed", data.usageCount(),
                            "quotaLimit", limit,
                            "periodStart", periodStart,
                            "updatedAt", now);
                }
                return null;
            }).get();
            log.info("Synced user {} from Notion to Firestore", data.userId());
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error updating user {} from Notion data", data.userId(), e);
        }
    }
}
