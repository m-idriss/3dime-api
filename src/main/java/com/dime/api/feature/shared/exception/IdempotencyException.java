package com.dime.api.feature.shared.exception;

import jakarta.ws.rs.core.Response;

/**
 * Exception for conflicting idempotent conversion retries.
 */
public class IdempotencyException extends BusinessException {

    public IdempotencyException(String message, Object details) {
        super("IDEMPOTENCY_CONFLICT", message, details);
    }

    @Override
    public int getHttpStatusCode() {
        return Response.Status.CONFLICT.getStatusCode();
    }
}
