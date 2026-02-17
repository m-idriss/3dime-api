package com.dime.api.feature.shared.exception;

/**
 * Base exception for business logic errors.
 * These are expected errors that should return 4xx status codes.
 */
public abstract class BusinessException extends RuntimeException {
    
    private final String errorCode;
    private final Object details;

    protected BusinessException(String errorCode, String message) {
        this(errorCode, message, null, null);
    }

    protected BusinessException(String errorCode, String message, Object details) {
        this(errorCode, message, details, null);
    }

    protected BusinessException(String errorCode, String message, Throwable cause) {
        this(errorCode, message, null, cause);
    }

    protected BusinessException(String errorCode, String message, Object details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }

    public abstract int getHttpStatusCode();
}