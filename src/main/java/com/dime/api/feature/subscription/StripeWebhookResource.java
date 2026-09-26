package com.dime.api.feature.subscription;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * Handles incoming Stripe webhook events.
 * Intentionally NOT protected by Firebase auth — Stripe sends its own signature header.
 */
@Slf4j
@Path("/webhooks/stripe")
@Tag(name = "webhooks", description = "Stripe event webhooks")
@Extension(name = "x-smallrye-profile-public", value = "")
public class StripeWebhookResource {

    @Inject
    StripeService stripeService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Receive Stripe webhook events",
               description = "Validates Stripe-Signature header and processes subscription lifecycle events")
    public Response handleWebhook(String payload,
            @HeaderParam("Stripe-Signature") String sigHeader) {

        if (sigHeader == null || sigHeader.isBlank()) {
            log.warn("Received webhook request without Stripe-Signature header");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing Stripe-Signature header"))
                    .build();
        }

        Event event;
        try {
            event = stripeService.constructWebhookEvent(payload, sigHeader);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Invalid webhook signature"))
                    .build();
        }

        log.info("Received Stripe event: {} ({})", event.getType(), event.getId());

        try {
            switch (event.getType()) {
                case "checkout.session.completed", "checkout.session.async_payment_succeeded" ->
                        stripeService.handleCheckoutEvent(event);
                case "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted" ->
                        stripeService.handleSubscriptionEvent(event);
                default -> log.debug("Unhandled Stripe event type: {}", event.getType());
            }
        } catch (com.stripe.exception.StripeException e) {
            throw new com.dime.api.feature.shared.exception.ExternalServiceException(
                    "Stripe", "Unable to reconcile webhook; retry required", e);
        }

        // Acknowledge only after successful fulfillment; failures propagate so Stripe retries.
        return Response.ok(Map.of("received", true)).build();
    }

}
