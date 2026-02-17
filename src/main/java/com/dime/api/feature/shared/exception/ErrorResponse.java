package com.dime.api.feature.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standardized error response model used across all API endpoints
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    private boolean success = false;
    private String error;
    private String message;
    private String errorCode;
    private Object details;
    private String timestamp;
    private String path;
    private int status;

    public ErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
        this.timestamp = Instant.now().toString();
    }

    public ErrorResponse(String error, String message, String errorCode, int status) {
        this.error = error;
        this.message = message;
        this.errorCode = errorCode;
        this.status = status;
        this.timestamp = Instant.now().toString();
    }

    public ErrorResponse(String error, String message, String errorCode, Object details, int status) {
        this.error = error;
        this.message = message;
        this.errorCode = errorCode;
        this.details = details;
        this.status = status;
        this.timestamp = Instant.now().toString();
    }

    public static ErrorResponse of(BusinessException ex, String path) {
        ErrorResponse response = new ErrorResponse(
            ex.getClass().getSimpleName().replace("Exception", ""),
            ex.getMessage(),
            ex.getErrorCode(),
            ex.getDetails(),
            ex.getHttpStatusCode()
        );
        response.setPath(path);
        return response;
    }

    public static ErrorResponse validation(String message) {
        return new ErrorResponse("Validation Error", message, "VALIDATION_ERROR", 400);
    }

    public static ErrorResponse internalError(String message) {
        return new ErrorResponse("Internal Server Error", message, "INTERNAL_ERROR", 500);
    }

    public static ErrorResponse externalService(String serviceName, String message) {
        return new ErrorResponse(
            "External Service Error", 
            message, 
            "EXTERNAL_SERVICE_ERROR", 
            serviceName, 
            502
        );
    }
}