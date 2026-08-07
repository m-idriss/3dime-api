package com.dime.api.feature.shared.exception;

import jakarta.ws.rs.core.Response;

/** Authentication is required or the supplied identity cannot be accepted. */
public class AuthenticationException extends BusinessException {

    public AuthenticationException(String message) {
        super("AUTHENTICATION_REQUIRED", message);
    }

    @Override
    public int getHttpStatusCode() {
        return Response.Status.UNAUTHORIZED.getStatusCode();
    }
}
