package com.dime.api.feature.subscription;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Current subscription status", requiredProperties = { "planId", "status" })
public record SubscriptionStatusResponse(
        @Schema(enumeration = { "free", "pro", "business", "unlimited" }) String planId,
        @Schema(enumeration = { "active", "trialing", "past_due", "canceled", "free" }) String status,
        String currentPeriodEnd
) {}
