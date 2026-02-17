package com.dime.api.feature.shared.exception;

import jakarta.ws.rs.core.Response;

/**
 * Exception for quota-related errors (429 Too Many Requests)
 */
public class QuotaException extends BusinessException {

    public QuotaException(String message) {
        super("QUOTA_EXCEEDED", message);
    }

    public QuotaException(String message, Object details) {
        super("QUOTA_EXCEEDED", message, details);
    }

    @Override
    public int getHttpStatusCode() {
        return Response.Status.TOO_MANY_REQUESTS.getStatusCode();
    }
}