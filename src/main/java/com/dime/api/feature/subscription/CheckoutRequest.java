package com.dime.api.feature.subscription;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CheckoutRequest(
        @NotBlank(message = "planId is required")
        @Pattern(regexp = "pro|business", message = "planId must be 'pro' or 'business'")
        String planId,

        @NotBlank(message = "billingCycle is required")
        @Pattern(regexp = "monthly|yearly", message = "billingCycle must be 'monthly' or 'yearly'")
        String billingCycle,

        String userId,

        @Email(message = "email must be valid")
        String email
) {}
