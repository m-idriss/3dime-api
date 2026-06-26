package com.dime.api.feature.shared.exception;

import jakarta.ws.rs.core.Response;

/**
 * Exception for quota datastore outages. Conversion must fail closed when quota
 * cannot be reserved atomically.
 */
public class DatastoreUnavailableException extends BusinessException {

    public DatastoreUnavailableException(String message, Throwable cause) {
        super("DATASTORE_UNAVAILABLE", message, null, cause);
    }

    @Override
    public int getHttpStatusCode() {
        return Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
    }
}
