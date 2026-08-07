package com.dime.api.feature.donation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "One-off donation checkout request", requiredProperties = { "productId" })
public record DonationRequest(
        @NotBlank(message = "productId is required")
        @Pattern(regexp = "coffee|snack|meal", message = "productId must be 'coffee', 'snack' or 'meal'")
        @Schema(enumeration = { "coffee", "snack", "meal" }) String productId,

        @Email(message = "email must be valid")
        String email
) {}
