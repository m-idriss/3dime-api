package com.dime.api.feature.subscription;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Subscription checkout request", requiredProperties = { "planId", "billingCycle" })
public record CheckoutRequest(
        @NotBlank(message = "planId is required")
        @Pattern(regexp = "pro|business", message = "planId must be 'pro' or 'business'")
        @Schema(enumeration = { "pro", "business" }) String planId,

        @NotBlank(message = "billingCycle is required")
        @Pattern(regexp = "monthly|yearly", message = "billingCycle must be 'monthly' or 'yearly'")
        @Schema(enumeration = { "monthly", "yearly" }) String billingCycle,

        String userId,

        @Email(message = "email must be valid")
        String email
) {}
