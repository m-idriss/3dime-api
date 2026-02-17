package com.dime.api.feature.shared.exception;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exception mapper for Bean Validation constraint violations
 */
@Slf4j
@Provider
@ApplicationScoped
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Inject
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        String path = uriInfo != null ? uriInfo.getPath() : "unknown";
        
        // Collect all validation errors
        Map<String, String> validationErrors = exception.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                    violation -> getFieldName(violation),
                    ConstraintViolation::getMessage,
                    (existing, replacement) -> existing // Keep first error for duplicate fields
                ));

        String message = validationErrors.size() == 1 
            ? validationErrors.values().iterator().next()
            : "Request validation failed";

        log.warn("Validation error on {}: {}", path, validationErrors);

        ErrorResponse errorResponse = new ErrorResponse(
            "Validation Error",
            message,
            "VALIDATION_ERROR",
            validationErrors,
            Response.Status.BAD_REQUEST.getStatusCode()
        );
        errorResponse.setPath(path);

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(errorResponse)
                .build();
    }

    private String getFieldName(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        if (propertyPath.isEmpty()) {
            return "request";
        }
        // Return the last part of the property path (e.g., "files[0].dataUrl" -> "dataUrl")
        String[] parts = propertyPath.split("\\.");
        return parts[parts.length - 1];
    }
}