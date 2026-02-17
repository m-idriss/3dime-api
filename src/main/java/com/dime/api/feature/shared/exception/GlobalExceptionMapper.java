package com.dime.api.feature.shared.exception;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

/**
 * Global exception handler for all unhandled exceptions
 */
@Slf4j
@Provider
@ApplicationScoped
@Priority(Priorities.USER)
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        String path = uriInfo != null ? uriInfo.getPath() : "unknown";

        // Business exceptions - expected errors
        if (exception instanceof BusinessException) {
            BusinessException businessEx = (BusinessException) exception;
            log.warn("Business exception on {}: {} - {}", path, businessEx.getErrorCode(), businessEx.getMessage());

            ErrorResponse errorResponse = ErrorResponse.of(businessEx, path);
            return Response.status(businessEx.getHttpStatusCode())
                    .entity(errorResponse)
                    .build();
        }

        // JAX-RS WebApplicationException (includes client REST calls)
        if (exception instanceof WebApplicationException) {
            WebApplicationException webEx = (WebApplicationException) exception;
            int status = webEx.getResponse().getStatus();
            String message = webEx.getMessage();

            // For external service calls, treat as external service error
            if (exception instanceof ClientWebApplicationException) {
                log.warn("External service error on {}: {} - {}", path, status, message, exception);

                ErrorResponse errorResponse = ErrorResponse.externalService("External API",
                        "External service returned error: " + message);
                errorResponse.setPath(path);
                return Response.status(Response.Status.BAD_GATEWAY)
                        .entity(errorResponse)
                        .build();
            }

            log.warn("Web application exception on {}: {} - {}", path, status, message);
            ErrorResponse errorResponse = new ErrorResponse(
                    "HTTP Error",
                    message != null ? message : "HTTP error occurred",
                    "HTTP_ERROR",
                    null,
                    status);
            errorResponse.setPath(path);
            return Response.status(status).entity(errorResponse).build();
        }

        // Runtime exceptions from services
        if (exception instanceof RuntimeException) {
            String message = exception.getMessage();

            // Check if it's a known pattern (like what NotionService throws)
            if (message != null && message.contains("API error")) {
                log.warn("External API error on {}: {}", path, message, exception);

                ErrorResponse errorResponse = ErrorResponse.externalService("External API", message);
                errorResponse.setPath(path);
                return Response.status(Response.Status.BAD_GATEWAY)
                        .entity(errorResponse)
                        .build();
            }
        }

        // Unexpected exceptions - log full stack trace and return generic error
        log.error("Unexpected exception on {}: {}", path, exception.getMessage(), exception);

        ErrorResponse errorResponse = ErrorResponse.internalError(
                "An unexpected error occurred. Please try again later.");
        errorResponse.setPath(path);

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(errorResponse)
                .build();
    }
}