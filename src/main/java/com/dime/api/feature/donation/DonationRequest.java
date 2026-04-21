package com.dime.api.feature.donation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DonationRequest(
        @NotBlank(message = "productId is required")
        @Pattern(regexp = "coffee|snack|meal", message = "productId must be 'coffee', 'snack' or 'meal'")
        String productId,

        @Email(message = "email must be valid")
        String email
) {}
