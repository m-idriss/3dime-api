package com.dime.api.feature.subscription;

public record SubscriptionStatusResponse(
        String planId,
        String status,
        String currentPeriodEnd
) {}
