package com.dime.api.feature.shared.exception;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * High-priority exception mapper specifically for business exceptions
 * This ensures our custom business exceptions are handled correctly
 */
@Slf4j
@Provider
@ApplicationScoped
@Priority(100) // Very high priority - processed before other mappers
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(BusinessException exception) {
        String path = uriInfo != null ? uriInfo.getPath() : "unknown";

        log.warn("Business exception on {}: {} - {}", path, exception.getErrorCode(), exception.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(exception, path);

        return Response.status(exception.getHttpStatusCode())
                .entity(errorResponse)
                .header("Content-Type", "application/json")
                .build();
    }
}