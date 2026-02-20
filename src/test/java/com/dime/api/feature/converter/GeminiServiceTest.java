package com.dime.api.feature.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
public class GeminiServiceTest {
    @Inject
    GeminiService geminiService;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        geminiService.geminiClient = mock(GeminiClient.class);
        geminiService.objectMapper = objectMapper;
        geminiService.apiKeyJson = Optional.of("{}\n");
    }

    @Test
    public void testCleanIcs() {
        String dirty = "BEGIN:VCALENDAR\nSUMMARY:Test\nEND:VCALENDAR";
        String cleaned = geminiService.cleanIcs(dirty);
        assertNotNull(cleaned);
        assertTrue(cleaned.contains("VCALENDAR"));
    }

    @Test
    public void testGetAccessTokenHandlesException() {
        geminiService.apiKeyJson = Optional.empty();
        // Reset cachedCredentials to ensure code path is exercised
        try {
            var field = GeminiService.class.getDeclaredField("cachedCredentials");
            field.setAccessible(true);
            field.set(geminiService, null);
        } catch (Exception e) {
            fail("Reflection error resetting cachedCredentials: " + e.getMessage());
        }
        // Should not throw, should fallback to application default credentials or
        // return a token
        try {
            String token = geminiService.getAccessToken();
            assertNotNull(token);
        } catch (IOException e) {
            // Acceptable if environment does not provide application default credentials
            assertTrue(e.getMessage().contains("credentials"));
        }
    }
}
