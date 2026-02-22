package com.dime.api.feature.shared;

/**
 * Shared utility for ensuring HTTP Authorization header values include the "Bearer " prefix.
 */
public final class BearerTokenUtil {

    private BearerTokenUtil() {
    }

    /**
     * Returns the token with a "Bearer " prefix, adding it only if not already present.
     *
     * @param raw raw token string (may already start with "Bearer ")
     * @return properly prefixed bearer token, or {@code null} if input is {@code null}
     */
    public static String ensureBearer(String raw) {
        if (raw == null)
            return null;
        return raw.startsWith("Bearer ") ? raw : "Bearer " + raw;
    }
}
