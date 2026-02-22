package com.dime.api.feature.converter;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
public class QuotaServiceTest {

    @Inject
    QuotaService quotaService;

    Firestore firestoreMock;
    NotionQuotaService notionQuotaServiceMock;

    @BeforeEach
    public void setup() {
        firestoreMock = mock(Firestore.class);
        notionQuotaServiceMock = mock(NotionQuotaService.class);
        quotaService.firestore = firestoreMock;
        quotaService.notionQuotaService = notionQuotaServiceMock;
    }

    @Test
    public void testIncrementUsageHandlesException() {
        // Simulate exception in Firestore
        when(firestoreMock.collection(any())).thenThrow(new RuntimeException("Firestore error"));
        assertDoesNotThrow(() -> quotaService.incrementUsage("user1"));
    }

    @Test
    public void testGetQuotaStatusHandlesException() {
        var collectionRef = mock(com.google.cloud.firestore.CollectionReference.class);
        var docRef = mock(com.google.cloud.firestore.DocumentReference.class);
        var apiFuture = mock(com.google.api.core.ApiFuture.class);
        when(firestoreMock.collection(any())).thenReturn(collectionRef);
        when(collectionRef.document(any())).thenReturn(docRef);
        when(docRef.get()).thenReturn(apiFuture);
        try {
            when(apiFuture.get()).thenThrow(new RuntimeException("Firestore error"));
        } catch (Exception e) {
            fail("Mock setup failed: " + e.getMessage());
        }
        assertNotNull(quotaService.getQuotaStatus("user1"));
    }

    @Test
    public void testCheckQuota_neverThrows_andDefaultsToAllow() {
        // checkQuota must never throw regardless of Firestore state;
        // it defaults to allowed=true on errors (to not block users)
        assertDoesNotThrow(() -> {
            QuotaService.QuotaCheckResult result = quotaService.checkQuota("resilience-test-user");
            assertTrue(result.allowed());
        });
    }

    @Test
    public void testFindAll_neverThrows_andReturnsNonNull() {
        assertDoesNotThrow(() -> assertNotNull(quotaService.findAll()));
    }

    @Test
    public void testDeleteQuota_neverThrows() {
        assertDoesNotThrow(() -> quotaService.deleteQuota("non-existent-user-delete"));
    }

    @Test
    public void testUpdateQuota_neverThrows() {
        UserQuota quota = new UserQuota();
        assertDoesNotThrow(() -> quotaService.updateQuota("non-existent-user-update", quota));
    }
}
