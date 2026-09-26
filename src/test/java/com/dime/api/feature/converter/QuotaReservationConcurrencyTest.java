package com.dime.api.feature.converter;

import com.dime.api.feature.shared.exception.QuotaException;
import com.google.api.core.ApiFutures;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.Transaction;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuotaReservationConcurrencyTest {

    private QuotaService quotaService;
    private InMemoryFirestoreHarness firestore;

    @BeforeEach
    void setup() {
        quotaService = new QuotaService();
        quotaService.quotaLimitFree = 3;
        quotaService.quotaLimitPlus = 15;
        quotaService.quotaLimitPro = 100;
        quotaService.quotaLimitBusiness = 120;
        quotaService.quotaLimitUnlimited = 1_000_000;
        quotaService.reservationTtlMinutes = 15;
        quotaService.freeDeviceLimit = 3;
        quotaService.freeNetworkDailyLimit = 12;
        quotaService.freeNetworkAccountThreshold = 3;
        quotaService.freeGlobalDailyLimit = 500;
        quotaService.init();

        firestore = new InMemoryFirestoreHarness();
        @SuppressWarnings("unchecked")
        Instance<Firestore> firestoreInstance = mock(Instance.class);
        when(firestoreInstance.get()).thenReturn(firestore.firestore);
        quotaService.firestoreInstance = firestoreInstance;
        quotaService.notionQuotaService = mock(NotionQuotaService.class);
    }

    @Test
    void repeatedPaymentFulfillmentGrantsExactlyOneCredit() throws Exception {
        firestore.userQuota = new UserQuota(PlanType.FREE, 3, 3, Timestamp.now(), Timestamp.now(), Timestamp.now());
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> calls = new ArrayList<>();
            for (int i = 0; i < 20; i++) calls.add(() -> {
                quotaService.grantPaidCredit("user", "cs_same_payment"); return null;
            });
            for (var result : executor.invokeAll(calls)) result.get();
            assertEquals(1, firestore.userQuota.paidCredits);
            assertEquals(1, firestore.payments.size());
            assertEquals(3, firestore.userQuota.quotaUsed);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void successfulPaidConversionCannotLaterRefundTheCredit() {
        firestore.userQuota = new UserQuota(PlanType.FREE, 3, 3, Timestamp.now(), Timestamp.now(), Timestamp.now());
        firestore.userQuota.paidCredits = 1;
        quotaService.reserveQuota("user", null, "paid-success", 1);
        quotaService.completeReservation("user", "paid-success", "test", "BEGIN:VCALENDAR", 1);
        quotaService.failReservation("user", "paid-success", "test", "late error", true);
        assertEquals(0, firestore.userQuota.paidCredits);
    }

    @Test
    void purchasedCreditIsConsumedAfterMonthlyQuotaAndRestoredOnceOnFailure() {
        firestore.userQuota = new UserQuota(PlanType.FREE, 3, 3, Timestamp.now(), Timestamp.now(), Timestamp.now());
        firestore.userQuota.paidCredits = 1;
        var result = quotaService.reserveQuota("paid-user", null, "credit-request", 1);
        assertTrue(result.reservation().paidCreditUsed);
        assertEquals(0, firestore.userQuota.paidCredits);
        assertEquals(3, firestore.userQuota.quotaUsed);
        quotaService.failReservation("paid-user", "credit-request", "test", "failed", true);
        quotaService.failReservation("paid-user", "credit-request", "test", "failed", true);
        assertEquals(1, firestore.userQuota.paidCredits);
        assertEquals(3, firestore.userQuota.quotaUsed);
    }

    @Test
    void freeMonthlyAllowanceIsUsedBeforePurchasedCredits() {
        firestore.userQuota = new UserQuota(PlanType.FREE, 0, 3, Timestamp.now(), Timestamp.now(), Timestamp.now());
        firestore.userQuota.paidCredits = 1;
        assertFalse(quotaService.reserveQuota("user", null, "monthly-first", 1).reservation().paidCreditUsed);
        assertEquals(1, firestore.userQuota.paidCredits);
        assertEquals(1, firestore.userQuota.quotaUsed);
    }

    @Test
    void purchasedCreditWorksWhenFreeInstallationAllowanceIsExhausted() {
        var identity = new QuotaIdentityService.QuotaIdentity("device-paid", null, "account");
        for (int i = 0; i < 3; i++) quotaService.reserveQuota("user", null, "free-" + i, 1, identity);
        firestore.userQuota.quotaUsed = 0;
        firestore.userQuota.paidCredits = 1;
        var reservation = quotaService.reserveQuota("user", null, "paid", 1, identity).reservation();
        assertTrue(reservation.paidCreditUsed);
        assertFalse(reservation.freeProtectionApplied);
        assertEquals(3, firestore.subject("quotaDevices", "device-paid").usageCount);
        assertEquals(0, firestore.userQuota.quotaUsed);
    }

    @Test
    void concurrentAttemptsCannotSpendOneCreditTwice() throws Exception {
        firestore.userQuota = new UserQuota(PlanType.FREE, 3, 3, Timestamp.now(), Timestamp.now(), Timestamp.now());
        firestore.userQuota.paidCredits = 1;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                String key = "paid-" + i;
                attempts.add(() -> {
                    try { quotaService.reserveQuota("user", null, key, 1); return true; }
                    catch (QuotaException e) { return false; }
                });
            }
            long success = 0;
            for (var result : executor.invokeAll(attempts)) if (result.get()) success++;
            assertEquals(1, success);
            assertEquals(0, firestore.userQuota.paidCredits);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void plusLimitAndLegacyLimitsRemainDistinct() {
        assertEquals(List.of(new QuotaService.PlanInfo(PlanType.FREE, 3),
                new QuotaService.PlanInfo(PlanType.PLUS, 15)), quotaService.getQuotaLimits());
        firestore.userQuota = new UserQuota(PlanType.PLUS, 14, 15, Timestamp.now(), Timestamp.now(), Timestamp.now());
        quotaService.reserveQuota("plus", null, "last", 1);
        assertThrows(QuotaException.class, () -> quotaService.reserveQuota("plus", null, "over", 1));
        firestore.userQuota.setPlanType(PlanType.PRO);
        assertEquals(100, quotaService.reserveQuota("legacy", null, "pro", 1).limit());
    }

    @Test
    void fiftyConcurrentRequestsAtFinalBoundary_reserveOnlyRemainingAllowance() throws Exception {
        firestore.userQuota = new UserQuota(
                PlanType.FREE,
                2,
                3,
                Timestamp.now(),
                Timestamp.now(),
                Timestamp.now());

        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                String key = "request-" + i;
                attempts.add(() -> {
                    try {
                        quotaService.reserveQuota("boundary-user", null, key, 1);
                        return true;
                    } catch (QuotaException e) {
                        return false;
                    }
                });
            }

            long successfulReservations = executor.invokeAll(attempts).stream()
                    .filter(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new AssertionError(e);
                        }
                    })
                    .count();

            assertEquals(1, successfulReservations);
            assertEquals(3, firestore.userQuota.quotaUsed);
            assertEquals(1, firestore.reservations.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void replayingSameIdempotencyKey_reusesReservationWithoutSecondCharge() {
        QuotaService.QuotaReservationResult first = quotaService.reserveQuota("same-key-user", null, "same-key", 1);
        QuotaService.QuotaReservationResult second = quotaService.reserveQuota("same-key-user", null, "same-key", 1);

        assertFalse(first.replay());
        assertTrue(second.replay());
        assertEquals(1, firestore.userQuota.quotaUsed);
        assertEquals(1, firestore.reservations.size());
    }

    @Test
    void quotaBoundaryThrowsQuotaExceptionWithoutWritingReservation() {
        firestore.userQuota = new UserQuota(
                PlanType.FREE,
                3,
                3,
                Timestamp.now(),
                Timestamp.now(),
                Timestamp.now());

        assertThrows(QuotaException.class,
                () -> quotaService.reserveQuota("full-user", null, "blocked-request", 1));
        assertEquals(3, firestore.userQuota.quotaUsed);
        assertEquals(0, firestore.reservations.size());
    }

    @Test
    void changingAccountDoesNotResetTheInstallationAllowance() {
        for (int i = 0; i < 3; i++) {
            firestore.userQuota = null; // Represents a fresh Firebase account document.
            quotaService.reserveQuota("account-" + i, null, "switch-" + i, 1,
                    new QuotaIdentityService.QuotaIdentity("shared-device", null, "account-hash-" + i));
        }

        firestore.userQuota = null;
        QuotaException blocked = assertThrows(QuotaException.class,
                () -> quotaService.reserveQuota("account-4", null, "switch-4", 1,
                        new QuotaIdentityService.QuotaIdentity("shared-device", null, "account-hash-4")));

        assertEquals("device", ((Map<?, ?>) blocked.getDetails()).get("scope"));
        assertEquals(3, firestore.subject("quotaDevices", "shared-device").usageCount);
    }

    @Test
    void failedConversionRefundsAccountAndProtectionCounters() {
        QuotaIdentityService.QuotaIdentity identity =
                new QuotaIdentityService.QuotaIdentity("device-refund", "network-refund", "account-refund");

        quotaService.reserveQuota("refund-user", null, "refund-key", 1, identity);
        quotaService.failReservation("refund-user", "refund-key", "claude", "provider failure", true);

        assertEquals(0, firestore.userQuota.quotaUsed);
        assertEquals(0, firestore.subject("quotaDevices", "device-refund").usageCount);
        assertEquals(0, firestore.subject("quotaNetworks", "network-refund").usageCount);
        assertEquals(0, firestore.subject("quotaGlobal", LocalDate.now(ZoneOffset.UTC).toString()).usageCount);
        assertEquals(QuotaReservationState.REFUNDED,
                firestore.reservations.get("refund-key").getStateType());
    }

    private static class InMemoryFirestoreHarness {

        final Firestore firestore = mock(Firestore.class);
        final CollectionReference users = mock(CollectionReference.class);
        final CollectionReference devices = mock(CollectionReference.class);
        final CollectionReference networks = mock(CollectionReference.class);
        final CollectionReference globals = mock(CollectionReference.class);
        final DocumentReference userDoc = mock(DocumentReference.class);
        final CollectionReference reservationCollection = mock(CollectionReference.class);
        final Transaction transaction = mock(Transaction.class);
        final Map<String, QuotaReservation> reservations = new HashMap<>();
        final Map<DocumentReference, String> reservationKeys = new IdentityHashMap<>();
        final Map<String, DocumentReference> reservationRefs = new HashMap<>();
        final Map<String, DocumentReference> subjectRefs = new HashMap<>();
        final Map<DocumentReference, QuotaSubject> subjects = new IdentityHashMap<>();
        UserQuota userQuota;
        final CollectionReference paymentCollection = mock(CollectionReference.class);
        final Map<String, DocumentReference> paymentRefs = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.Set<DocumentReference> payments = new java.util.HashSet<>();

        InMemoryFirestoreHarness() {
            when(userDoc.collection("creditPayments")).thenReturn(paymentCollection);
            when(paymentCollection.document(any())).thenAnswer(invocation -> paymentRefs.computeIfAbsent(
                    invocation.getArgument(0), ignored -> mock(DocumentReference.class)));
            when(firestore.collection("users")).thenReturn(users);
            when(firestore.collection("quotaDevices")).thenReturn(devices);
            when(firestore.collection("quotaNetworks")).thenReturn(networks);
            when(firestore.collection("quotaGlobal")).thenReturn(globals);
            when(users.document(any())).thenReturn(userDoc);
            when(userDoc.get()).thenAnswer(ignored -> ApiFutures.immediateFuture(snapshotFor(userDoc)));
            mockSubjectCollection(devices, "quotaDevices");
            mockSubjectCollection(networks, "quotaNetworks");
            mockSubjectCollection(globals, "quotaGlobal");
            when(userDoc.collection("quotaReservations")).thenReturn(reservationCollection);
            when(reservationCollection.document(any())).thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                synchronized (this) {
                    return reservationRefs.computeIfAbsent(key, ignored -> {
                        DocumentReference ref = mock(DocumentReference.class);
                        reservationKeys.put(ref, key);
                        return ref;
                    });
                }
            });

            when(firestore.runTransaction(any())).thenAnswer(invocation -> {
                synchronized (this) {
                    @SuppressWarnings("unchecked")
                    Transaction.Function<Object> function = invocation.getArgument(0);
                    return ApiFutures.immediateFuture(function.updateCallback(transaction));
                }
            });
            when(transaction.get(any(DocumentReference.class))).thenAnswer(invocation ->
                    ApiFutures.immediateFuture(snapshotFor(invocation.getArgument(0))));
            doAnswer(invocation -> {
                applySet(invocation.getArgument(0), invocation.getArgument(1));
                return transaction;
            }).when(transaction).set(any(DocumentReference.class), any(Map.class), any(SetOptions.class));
            doAnswer(invocation -> {
                applySet(invocation.getArgument(0), invocation.getArgument(1));
                return transaction;
            }).when(transaction).set(any(DocumentReference.class), any(Object.class), any(SetOptions.class));
            doAnswer(invocation -> {
                applySet(invocation.getArgument(0), invocation.getArgument(1));
                return transaction;
            }).when(transaction).set(any(DocumentReference.class), any(Object.class));
            doAnswer(invocation -> {
                applySet(invocation.getArgument(0), invocation.getArgument(1));
                return transaction;
            }).when(transaction).set(any(DocumentReference.class), any(Map.class));
            doAnswer(invocation -> {
                applyUpdate(invocation.getArgument(0), invocation.getArgument(1));
                return transaction;
            }).when(transaction).update(any(DocumentReference.class), any(Map.class));
        }

        private void mockSubjectCollection(CollectionReference collection, String collectionName) {
            when(collection.document(any())).thenAnswer(invocation -> {
                String id = invocation.getArgument(0);
                synchronized (this) {
                    return subjectRefs.computeIfAbsent(collectionName + ":" + id,
                            ignored -> mock(DocumentReference.class));
                }
            });
        }

        QuotaSubject subject(String collectionName, String id) {
            return subjects.get(subjectRefs.get(collectionName + ":" + id));
        }

        private DocumentSnapshot snapshotFor(DocumentReference ref) {
            DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
            if (paymentRefs.containsValue(ref)) {
                when(snapshot.exists()).thenReturn(payments.contains(ref));
                return snapshot;
            }
            if (ref == userDoc) {
                when(snapshot.exists()).thenReturn(userQuota != null);
                when(snapshot.toObject(UserQuota.class)).thenReturn(userQuota);
                return snapshot;
            }

            QuotaSubject subject = subjects.get(ref);
            if (subject != null || subjectRefs.containsValue(ref)) {
                when(snapshot.exists()).thenReturn(subject != null);
                when(snapshot.toObject(QuotaSubject.class)).thenReturn(subject);
                return snapshot;
            }

            String key = reservationKeys.get(ref);
            QuotaReservation reservation = reservations.get(key);
            when(snapshot.exists()).thenReturn(reservation != null);
            when(snapshot.toObject(QuotaReservation.class)).thenReturn(reservation);
            return snapshot;
        }

        private void applySet(DocumentReference ref, Object value) {
            if (paymentRefs.containsValue(ref)) { payments.add(ref); return; }
            if (ref == userDoc && value instanceof UserQuota quota) { userQuota = quota; return; }
            if (ref == userDoc && value instanceof Map<?, ?> updates) {
                if (userQuota == null) {
                    userQuota = new UserQuota();
                }
                applyQuotaUpdates(userQuota, updates);
                return;
            }

            if (value instanceof QuotaSubject subject && subjectRefs.containsValue(ref)) {
                subjects.put(ref, subject);
                return;
            }

            String key = reservationKeys.get(ref);
            if (key != null && value instanceof QuotaReservation reservation) {
                reservations.put(key, reservation);
            }
        }

        private void applyUpdate(DocumentReference ref, Object value) {
            if (!(value instanceof Map<?, ?> updates)) {
                return;
            }

            if (ref == userDoc && userQuota != null) {
                applyQuotaUpdates(userQuota, updates);
                return;
            }

            QuotaSubject subject = subjects.get(ref);
            if (subject != null) {
                Object usageCount = updates.get("usageCount");
                if (usageCount instanceof Number valueNumber) {
                    subject.usageCount = valueNumber.longValue();
                }
                return;
            }

            String key = reservationKeys.get(ref);
            QuotaReservation reservation = reservations.get(key);
            if (reservation != null) {
                Object state = updates.get("state");
                if (state instanceof String stateValue) {
                    reservation.state = stateValue;
                }
            }
        }

        private void applyQuotaUpdates(UserQuota quota, Map<?, ?> updates) {
            Object plan = updates.get("plan");
            if (plan instanceof String planValue) {
                quota.plan = planValue;
            }
            Object paidCredits = updates.get("paidCredits");
            if (paidCredits instanceof Number n) quota.paidCredits = n.longValue();
            Object quotaUsed = updates.get("quotaUsed");
            if (quotaUsed instanceof Number quotaUsedValue) {
                quota.quotaUsed = quotaUsedValue.longValue();
            }
            Object quotaLimit = updates.get("quotaLimit");
            if (quotaLimit instanceof Number quotaLimitValue) {
                quota.quotaLimit = quotaLimitValue.longValue();
            }
            Object periodStart = updates.get("periodStart");
            if (periodStart instanceof Timestamp periodStartValue) {
                quota.periodStart = periodStartValue;
            }
            Object createdAt = updates.get("createdAt");
            if (createdAt instanceof Timestamp createdAtValue) {
                quota.createdAt = createdAtValue;
            }
            Object updatedAt = updates.get("updatedAt");
            if (updatedAt instanceof Timestamp updatedAtValue) {
                quota.updatedAt = updatedAtValue;
            }
            Object email = updates.get("email");
            if (email instanceof String emailValue) {
                quota.email = emailValue;
            }
        }
    }
}
