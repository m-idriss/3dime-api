package com.dime.api.feature.converter;

import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuotaServiceTest {

    QuotaService quotaService;
    Firestore firestoreMock;
    NotionQuotaService notionQuotaServiceMock;

    @BeforeEach
    public void setup() {
        quotaService = new QuotaService();
        quotaService.quotaLimitFree = 3;
        quotaService.quotaLimitPro = 100;
        quotaService.quotaLimitUnlimited = 1000000;
        quotaService.init();
        firestoreMock = mock(Firestore.class);
        notionQuotaServiceMock = mock(NotionQuotaService.class);
        quotaService.firestore = firestoreMock;
        quotaService.notionQuotaService = notionQuotaServiceMock;
    }

    @Test
    public void testIncrementUsageHandlesException() {
        when(firestoreMock.collection(any())).thenThrow(new RuntimeException("Firestore error"));
        assertDoesNotThrow(() -> quotaService.incrementUsage("user1"));
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
            QuotaService.QuotaCheckResult result = quotaService.checkQuota("resilience-test-user");
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
}
