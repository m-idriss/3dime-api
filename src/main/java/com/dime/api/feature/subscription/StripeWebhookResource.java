package com.dime.api.feature.subscription;

import com.dime.api.feature.converter.PlanType;
import com.dime.api.feature.converter.QuotaService;
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
import java.util.Optional;

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

    @Inject
    QuotaService quotaService;

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

        switch (event.getType()) {
            case "customer.subscription.created", "customer.subscription.updated" ->
                    handleSubscriptionActivated(event);
            case "customer.subscription.deleted" ->
                    handleSubscriptionDeleted(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }

        // Always return 200 to acknowledge receipt — Stripe retries on non-2xx
        return Response.ok(Map.of("received", true)).build();
    }

    private void handleSubscriptionActivated(Event event) {
        Optional<Map.Entry<String, PlanType>> resolved =
                stripeService.resolveUserPlanFromSubscription(event);

        resolved.ifPresentOrElse(
                entry -> {
                    String userId = entry.getKey();
                    PlanType planType = entry.getValue();
                    log.info("Activating plan {} for user {} (event={})", planType, userId, event.getId());
                    quotaService.updateUserPlan(userId, planType);
                },
                () -> log.warn("Could not resolve user/plan from event {}", event.getId()));
    }

    private void handleSubscriptionDeleted(Event event) {
        Optional<Map.Entry<String, PlanType>> resolved =
                stripeService.resolveUserPlanFromSubscription(event);

        resolved.ifPresentOrElse(
                entry -> {
                    String userId = entry.getKey();
                    log.info("Downgrading user {} to FREE after subscription cancellation (event={})",
                            userId, event.getId());
                    quotaService.updateUserPlan(userId, PlanType.FREE);
                },
                () -> log.warn("Could not resolve user from cancelled subscription event {}", event.getId()));
    }
}
