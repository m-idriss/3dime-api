package com.dime.api.feature.subscription;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Hosted checkout session", requiredProperties = { "sessionUrl" })
public record CheckoutResponse(String sessionUrl) {}
