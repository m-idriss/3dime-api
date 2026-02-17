package com.dime.api.feature.shared.exception;

import jakarta.ws.rs.core.Response;

/**
 * Exception for processing errors where input is valid but processing fails (422 Unprocessable Entity)
 */
public class ProcessingException extends BusinessException {

    public ProcessingException(String message) {
        super("PROCESSING_ERROR", message);
    }

    public ProcessingException(String message, Object details) {
        super("PROCESSING_ERROR", message, details);
    }

    public ProcessingException(String message, Throwable cause) {
        super("PROCESSING_ERROR", message, cause);
    }

    @Override
    public int getHttpStatusCode() {
        return Response.Status.fromStatusCode(422).getStatusCode(); // Unprocessable Entity
    }
}