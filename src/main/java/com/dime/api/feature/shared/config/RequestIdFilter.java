package com.dime.api.feature.shared.config;

import com.dime.api.feature.shared.exception.ErrorResponse;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.UUID;

/** Provides one safe correlation identifier for every API response and error body. */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER = "X-Request-ID";
    private static final String PROPERTY = RequestIdFilter.class.getName() + ".requestId";
    private static final int MAX_LENGTH = 128;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String supplied = requestContext.getHeaderString(HEADER);
        String requestId = isSafe(supplied) ? supplied : UUID.randomUUID().toString();
        requestContext.setProperty(PROPERTY, requestId);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        String requestId = (String) requestContext.getProperty(PROPERTY);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        responseContext.getHeaders().putSingle(HEADER, requestId);
        if (responseContext.getEntity() instanceof ErrorResponse errorResponse) {
            errorResponse.setRequestId(requestId);
        }
    }

    private boolean isSafe(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_LENGTH
                && value.matches("[A-Za-z0-9._:-]+");
    }
}
