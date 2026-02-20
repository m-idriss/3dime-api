package com.dime.api.feature.shared.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import jakarta.ws.rs.core.Response;

class ValidationExceptionTest {
    @Test
    void testValidationExceptionStatusCode() {
        ValidationException ex = new ValidationException("Invalid input");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getHttpStatusCode());
        assertEquals("VALIDATION_ERROR", ex.getErrorCode());
        assertEquals("Invalid input", ex.getMessage());
    }

    @Test
    void testValidationExceptionWithDetails() {
        Object details = new Object();
        ValidationException ex = new ValidationException("Invalid input", details);
        assertEquals(details, ex.getDetails());
    }
}

class ExternalServiceExceptionTest {
    @Test
    void testExternalServiceExceptionStatusCode() {
        ExternalServiceException ex = new ExternalServiceException("GitHub", "API error");
        assertEquals(Response.Status.BAD_GATEWAY.getStatusCode(), ex.getHttpStatusCode());
        assertEquals("EXTERNAL_SERVICE_ERROR", ex.getErrorCode());
        assertEquals("API error", ex.getMessage());
        assertEquals("GitHub", ex.getServiceName());
    }
}

class ErrorResponseTest {
    @Test
    void testErrorResponseOfBusinessException() {
        BusinessException ex = new ValidationException("Invalid input");
        ErrorResponse response = ErrorResponse.of(ex, "/test");
        assertEquals("Validation", response.getError());
        assertEquals("Invalid input", response.getMessage());
        assertEquals("VALIDATION_ERROR", response.getErrorCode());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("/test", response.getPath());
    }

}
