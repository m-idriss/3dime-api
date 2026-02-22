package com.dime.api.feature.shared.health;

import com.dime.api.feature.notion.NotionClient;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotionHealthCheckTest {

    NotionHealthCheck check;

    @BeforeEach
    void setup() {
        check = new NotionHealthCheck();
        check.notionClient = mock(NotionClient.class);
        check.token = "test-token";
        check.version = "2022-02-22";
        check.cachedResponse = null;
        check.lastCheckedAt = 0;
    }

    @Test
    void testUp() {
        when(check.notionClient.getMe(any(), any()))
                .thenReturn(JsonNodeFactory.instance.objectNode().put("object", "user"));

        HealthCheckResponse response = check.doCheck();

        assertEquals("notion", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertTrue(response.getData().isPresent());
        assertTrue(response.getData().get().containsKey("latencyMs"));
        assertFalse(response.getData().get().containsKey("error"));
    }

    @Test
    void testDegradedUp_onException() {
        when(check.notionClient.getMe(any(), any())).thenThrow(new RuntimeException("timeout"));

        HealthCheckResponse response = check.doCheck();

        // Non-critical: still returns UP but with error data
        assertEquals("notion", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertTrue(response.getData().isPresent());
        assertEquals("DOWN", response.getData().get().get("status"));
        assertEquals("timeout", response.getData().get().get("error"));
    }

    @Test
    void testCacheReturnsCachedResponse() {
        when(check.notionClient.getMe(any(), any()))
                .thenReturn(JsonNodeFactory.instance.objectNode());

        check.call();
        check.call();

        verify(check.notionClient, times(1)).getMe(any(), any());
    }
}
