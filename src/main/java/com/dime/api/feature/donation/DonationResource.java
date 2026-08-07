package com.dime.api.feature.donation;

import com.dime.api.feature.subscription.CheckoutResponse;
import com.dime.api.feature.subscription.StripeService;
import com.dime.api.feature.shared.exception.ErrorResponse;
import com.dime.api.feature.shared.exception.ExternalServiceException;
import com.dime.api.feature.shared.exception.ValidationException;
import com.stripe.exception.StripeException;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Slf4j
@Path("/donations")
@Tag(name = "donations", description = "One-off donation checkout")
@Extension(name = "x-smallrye-profile-public", value = "")
public class DonationResource {

    @Inject
    StripeService stripeService;

    @POST
    @Path("/checkout")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create donation Checkout Session", description = "Creates a Stripe one-off payment session for coffee, snack or meal and returns the hosted payment URL")
    @APIResponse(responseCode = "200", description = "Checkout session created", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CheckoutResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid donation request", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "502", description = "Payment provider error", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response createCheckout(@Valid DonationRequest request) {
        log.info("Creating donation checkout session for product {}", request.productId());

        try {
            String sessionUrl = stripeService.createDonationSession(request.productId(), request.email());
            return Response.ok(new CheckoutResponse(sessionUrl)).build();
        } catch (StripeException e) {
            log.error("Stripe error creating donation checkout for product {}: {}", request.productId(), e.getMessage(), e);
            throw new ExternalServiceException("Stripe", "Payment provider error. Please try again.", e);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
    }
}
