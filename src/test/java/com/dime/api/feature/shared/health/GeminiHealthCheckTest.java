package com.dime.api.feature.shared.health;

import com.dime.api.feature.converter.GeminiService;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiHealthCheckTest {

    GeminiHealthCheck check;

    @BeforeEach
    void setup() {
        check = new GeminiHealthCheck();
        check.geminiService = mock(GeminiService.class);
        check.cachedResponse = null;
        check.lastCheckedAt = 0;
    }

    @Test
    void testUp() throws Exception {
        doNothing().when(check.geminiService).ping();

        HealthCheckResponse response = check.doCheck();

        assertEquals("gemini", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertTrue(response.getData().isPresent());
        assertTrue(response.getData().get().containsKey("latencyMs"));
    }

    @Test
    void testDown_onIOException() throws Exception {
        doThrow(new IOException("credentials not found")).when(check.geminiService).ping();

        HealthCheckResponse response = check.doCheck();

        assertEquals("gemini", response.getName());
        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertTrue(response.getData().isPresent());
        assertEquals("credentials not found", response.getData().get().get("error"));
    }

    @Test
    void testDown_onRuntimeException() throws Exception {
        doThrow(new RuntimeException("token refresh failed")).when(check.geminiService).ping();

        HealthCheckResponse response = check.doCheck();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertNotNull(response.getData().get().get("error"));
    }

    @Test
    void testCacheReturnsCachedResponse() throws Exception {
        doNothing().when(check.geminiService).ping();

        check.call();
        check.call();

        verify(check.geminiService, times(1)).ping();
    }
}
