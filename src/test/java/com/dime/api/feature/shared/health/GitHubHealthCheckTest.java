package com.dime.api.feature.shared.health;

import com.dime.api.feature.github.GitHubClient;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GitHubHealthCheckTest {

    GitHubHealthCheck check;

    @BeforeEach
    void setup() {
        check = new GitHubHealthCheck();
        check.gitHubClient = mock(GitHubClient.class);
        check.token = Optional.empty();
        check.cachedResponse = null;
        check.lastCheckedAt = 0;
    }

    @Test
    void testUp() {
        when(check.gitHubClient.getRateLimit(any()))
                .thenReturn(JsonNodeFactory.instance.objectNode().put("rate", "ok"));

        HealthCheckResponse response = check.doCheck();

        assertEquals("github", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertTrue(response.getData().isPresent());
        assertTrue(response.getData().get().containsKey("latencyMs"));
        assertFalse(response.getData().get().containsKey("error"));
    }

    @Test
    void testDegradedUp_onException() {
        when(check.gitHubClient.getRateLimit(any())).thenThrow(new RuntimeException("connection refused"));

        HealthCheckResponse response = check.doCheck();

        // Non-critical: still returns UP but with error data
        assertEquals("github", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertTrue(response.getData().isPresent());
        assertEquals("DOWN", response.getData().get().get("status"));
        assertEquals("connection refused", response.getData().get().get("error"));
    }

    @Test
    void testUsesTokenWhenPresent() {
        check.token = Optional.of("my-token");
        when(check.gitHubClient.getRateLimit("Bearer my-token"))
                .thenReturn(JsonNodeFactory.instance.objectNode());

        HealthCheckResponse response = check.doCheck();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        verify(check.gitHubClient).getRateLimit("Bearer my-token");
    }

    @Test
    void testCacheReturnsCachedResponse() {
        when(check.gitHubClient.getRateLimit(any()))
                .thenReturn(JsonNodeFactory.instance.objectNode());

        check.call();
        check.call();

        verify(check.gitHubClient, times(1)).getRateLimit(any());
    }
}
