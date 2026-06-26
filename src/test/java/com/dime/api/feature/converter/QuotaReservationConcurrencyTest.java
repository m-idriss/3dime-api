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
        quotaService.quotaLimitPro = 100;
        quotaService.quotaLimitBusiness = 120;
        quotaService.quotaLimitUnlimited = 1_000_000;
        quotaService.reservationTtlMinutes = 15;
        quotaService.init();

        firestore = new InMemoryFirestoreHarness();
        @SuppressWarnings("unchecked")
        Instance<Firestore> firestoreInstance = mock(Instance.class);
        when(firestoreInstance.get()).thenReturn(firestore.firestore);
        quotaService.firestoreInstance = firestoreInstance;
        quotaService.notionQuotaService = mock(NotionQuotaService.class);
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

    private static class InMemoryFirestoreHarness {

        final Firestore firestore = mock(Firestore.class);
        final CollectionReference users = mock(CollectionReference.class);
        final DocumentReference userDoc = mock(DocumentReference.class);
        final CollectionReference reservationCollection = mock(CollectionReference.class);
        final Transaction transaction = mock(Transaction.class);
        final Map<String, QuotaReservation> reservations = new HashMap<>();
        final Map<DocumentReference, String> reservationKeys = new IdentityHashMap<>();
        final Map<String, DocumentReference> reservationRefs = new HashMap<>();
        UserQuota userQuota;

        InMemoryFirestoreHarness() {
            when(firestore.collection("users")).thenReturn(users);
            when(users.document(any())).thenReturn(userDoc);
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
                applyUpdate(invocation.getArgument(0), invocation.getArgument(1));
                return transaction;
            }).when(transaction).update(any(DocumentReference.class), any(Map.class));
        }

        private DocumentSnapshot snapshotFor(DocumentReference ref) {
            DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
            if (ref == userDoc) {
                when(snapshot.exists()).thenReturn(userQuota != null);
                when(snapshot.toObject(UserQuota.class)).thenReturn(userQuota);
                return snapshot;
            }

            String key = reservationKeys.get(ref);
            QuotaReservation reservation = reservations.get(key);
            when(snapshot.exists()).thenReturn(reservation != null);
            when(snapshot.toObject(QuotaReservation.class)).thenReturn(reservation);
            return snapshot;
        }

        private void applySet(DocumentReference ref, Object value) {
            if (ref == userDoc && value instanceof Map<?, ?> updates) {
                if (userQuota == null) {
                    userQuota = new UserQuota();
                }
                applyQuotaUpdates(userQuota, updates);
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
