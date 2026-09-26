package com.dime.api.feature.subscription;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(requiredProperties = { "fulfilled", "kind" })
public record CheckoutStatusResponse(boolean fulfilled,
        @Schema(enumeration = { "credit", "subscription" }) String kind) {}
