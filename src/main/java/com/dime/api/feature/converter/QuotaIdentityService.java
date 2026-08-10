package com.dime.api.feature.converter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.regex.Pattern;

@ApplicationScoped
public class QuotaIdentityService {

    private static final Pattern INSTALLATION_ID = Pattern.compile("[A-Za-z0-9_-]{20,128}");

    @ConfigProperty(name = "quota.identity.pepper", defaultValue = "photocalia-local-only")
    String pepper;

    public record QuotaIdentity(String deviceHash, String networkHash, String accountHash) {
    }

    public QuotaIdentity resolve(String userId, String installationId, ContainerRequestContext requestContext) {
        String normalizedInstallation = normalizeInstallationId(installationId, userId);
        String clientNetwork = clientNetwork(requestContext);
        return new QuotaIdentity(
                hash("device", normalizedInstallation),
                clientNetwork != null ? hash("network", clientNetwork) : null,
                hash("account", userId));
    }

    String normalizeInstallationId(String installationId, String userId) {
        if (installationId != null) {
            String normalized = installationId.trim();
            if (INSTALLATION_ID.matcher(normalized).matches()) {
                return normalized;
            }
        }
        return "legacy:" + userId;
    }

    String clientNetwork(ContainerRequestContext requestContext) {
        String forwardedFor = requestContext.getHeaderString("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",", 2)[0].trim();
            return first.isBlank() ? null : first;
        }
        String realIp = requestContext.getHeaderString("X-Real-IP");
        return realIp == null || realIp.isBlank() ? null : realIp.trim();
    }

    String hash(String namespace, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal((namespace + ':' + value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive quota identity", e);
        }
    }
}
