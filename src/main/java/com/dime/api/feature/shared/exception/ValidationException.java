package com.dime.api.feature.shared.exception;

import jakarta.ws.rs.core.Response;

/**
 * Exception for validation errors (400 Bad Request)
 */
public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }

    public ValidationException(String message, Object details) {
        super("VALIDATION_ERROR", message, details);
    }

    @Override
    public int getHttpStatusCode() {
        return Response.Status.BAD_REQUEST.getStatusCode();
    }
}