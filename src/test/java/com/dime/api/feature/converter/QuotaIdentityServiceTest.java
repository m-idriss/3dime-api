package com.dime.api.feature.converter;

import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuotaIdentityServiceTest {

    private QuotaIdentityService service;
    private ContainerRequestContext requestContext;

    @BeforeEach
    void setup() {
        service = new QuotaIdentityService();
        service.pepper = "test-only-secret";
        requestContext = mock(ContainerRequestContext.class);
    }

    @Test
    void sameInstallationSurvivesAccountChangeWithoutStoringRawIdentifiers() {
        String installationId = "inst_12345678901234567890";
        when(requestContext.getHeaderString("X-Forwarded-For"))
                .thenReturn("203.0.113.42, 10.0.0.1");

        QuotaIdentityService.QuotaIdentity first = service.resolve("first-user", installationId, requestContext);
        QuotaIdentityService.QuotaIdentity second = service.resolve("second-user", installationId, requestContext);

        assertEquals(first.deviceHash(), second.deviceHash());
        assertEquals(first.networkHash(), second.networkHash());
        assertNotEquals(first.accountHash(), second.accountHash());
        assertEquals(64, first.deviceHash().length());
        assertFalse(first.deviceHash().contains(installationId));
        assertFalse(first.networkHash().contains("203.0.113.42"));
    }

    @Test
    void malformedInstallationFallsBackToAccountScopedLegacyIdentity() {
        QuotaIdentityService.QuotaIdentity first = service.resolve("first-user", "invalid", requestContext);
        QuotaIdentityService.QuotaIdentity second = service.resolve("second-user", "invalid", requestContext);

        assertNotEquals(first.deviceHash(), second.deviceHash());
        assertNull(first.networkHash());
    }
}
