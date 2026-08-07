package com.dime.api.feature.subscription;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Subscription cancellation confirmation",
        requiredProperties = { "success", "message" })
public record SubscriptionCancellationResponse(boolean success, String message) {
}
