package com.dime.api.feature.converter;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Public quota status", requiredProperties = { "success", "quota", "enabled" })
public record QuotaStatusResponse(
        boolean success,
        Quota quota,
        boolean enabled) {

    @Schema(description = "Quota usage for the active monthly period",
            requiredProperties = { "usageCount", "limit", "remaining", "plan" })
    public record Quota(
            long usageCount,
            long limit,
            long remaining,
            PlanType plan) {
    }

    public static QuotaStatusResponse from(UserQuota userQuota) {
        long remaining = Math.max(0, userQuota.quotaLimit - userQuota.quotaUsed);
        return new QuotaStatusResponse(
                true,
                new Quota(userQuota.quotaUsed, userQuota.quotaLimit, remaining, userQuota.getPlanType()),
                true);
    }
}
