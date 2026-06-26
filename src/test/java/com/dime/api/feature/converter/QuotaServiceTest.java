package com.dime.api.feature.converter;

import com.dime.api.feature.shared.exception.DatastoreUnavailableException;
import com.dime.api.feature.shared.exception.ValidationException;
import com.google.cloud.firestore.Firestore;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuotaServiceTest {

    QuotaService quotaService;
    Firestore firestoreMock;
    Instance<Firestore> firestoreInstanceMock;
    NotionQuotaService notionQuotaServiceMock;

    @BeforeEach
    public void setup() {
        quotaService = new QuotaService();
        quotaService.quotaLimitFree = 3;
        quotaService.quotaLimitPro = 100;
        quotaService.quotaLimitBusiness = 120;
        quotaService.quotaLimitUnlimited = 1000000;
        quotaService.init();
        firestoreMock = mock(Firestore.class);
        firestoreInstanceMock = mock(Instance.class);
        when(firestoreInstanceMock.get()).thenReturn(firestoreMock);
        notionQuotaServiceMock = mock(NotionQuotaService.class);
        quotaService.firestoreInstance = firestoreInstanceMock;
        quotaService.notionQuotaService = notionQuotaServiceMock;
    }

    @Test
    public void testIncrementUsageHandlesException() {
        when(firestoreMock.collection(any())).thenThrow(new RuntimeException("Firestore error"));
        assertDoesNotThrow(() -> quotaService.incrementUsage("user1", null));
    }

    @Test
    public void testGetQuotaStatusHandlesException() {
        when(firestoreMock.collection(any())).thenThrow(new RuntimeException("Firestore error"));
        assertDoesNotThrow(() -> quotaService.getQuotaStatus("user1"));
    }

    @Test
    public void testCheckQuota_neverThrows_andDefaultsToAllow() {
        when(firestoreMock.collection(any())).thenThrow(new RuntimeException("Firestore error"));
        assertDoesNotThrow(() -> {
            QuotaService.QuotaCheckResult result = quotaService.checkQuota("resilience-test-user", null);
            assertTrue(result.allowed());
        });
    }

    @Test
    public void testFindAll_neverThrows_andReturnsNonNull() {
        when(firestoreMock.collection(any())).thenThrow(new RuntimeException("Firestore error"));
        assertDoesNotThrow(() -> assertNotNull(quotaService.findAll()));
    }

    @Test
    public void testDeleteQuota_neverThrows() {
        when(firestoreMock.collection(any())).thenThrow(new RuntimeException("Firestore error"));
        assertDoesNotThrow(() -> quotaService.deleteQuota("non-existent-user-delete"));
    }

    @Test
    public void testUpdateQuota_neverThrows() {
        when(firestoreMock.collection(any())).thenThrow(new RuntimeException("Firestore error"));
        UserQuota quota = new UserQuota();
        assertDoesNotThrow(() -> quotaService.updateQuota("non-existent-user-update", quota));
    }

    @Test
    public void testGetQuotaLimits_doesNotTouchFirestore() {
        quotaService.getQuotaLimits();

        verifyNoInteractions(firestoreInstanceMock);
    }

    @Test
    public void testCheckQuota_doesNotSwallowLinkageErrorFromFirestoreBean() {
        when(firestoreInstanceMock.get())
                .thenThrow(new NoSuchMethodError("GrpcTelemetry.newClientInterceptor"));

        assertThrows(NoSuchMethodError.class, () -> quotaService.checkQuota("linkage-error-user", null));
    }

    @Test
    public void testReserveQuota_requiresIdempotencyKey() {
        assertThrows(ValidationException.class, () -> quotaService.reserveQuota("user1", null, null, 1));
        assertThrows(ValidationException.class, () -> quotaService.reserveQuota("user1", null, " ", 1));

        verifyNoInteractions(firestoreInstanceMock);
    }

    @Test
    public void testReserveQuota_failsClosedWhenFirestoreUnavailable() {
        when(firestoreMock.collection(any())).thenThrow(new RuntimeException("Firestore unavailable"));

        assertThrows(DatastoreUnavailableException.class,
                () -> quotaService.reserveQuota("user1", null, "request-1", 1));
    }
}
