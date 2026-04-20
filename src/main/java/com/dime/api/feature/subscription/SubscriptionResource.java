package com.dime.api.feature.subscription;

import com.dime.api.feature.converter.QuotaService;
import com.dime.api.feature.converter.UserQuota;
import com.dime.api.feature.shared.config.FirebaseAuthFilter;
import com.stripe.exception.StripeException;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Slf4j
@Path("/subscriptions")
@Tag(name = "subscriptions", description = "Stripe subscription management")
@Extension(name = "x-smallrye-profile-public", value = "")
public class SubscriptionResource {

    @Inject
    StripeService stripeService;

    @Inject
    QuotaService quotaService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create Stripe Checkout Session", description = "Creates a Stripe Checkout Session for the requested subscription plan and returns the hosted payment URL")
    public Response createCheckout(@Valid CheckoutRequest request,
            @Context ContainerRequestContext requestContext) {

        String verifiedUid = (String) requestContext.getProperty(FirebaseAuthFilter.FIREBASE_UID);
        String verifiedEmail = (String) requestContext.getProperty(FirebaseAuthFilter.FIREBASE_EMAIL);

        String userId = verifiedUid != null ? verifiedUid : request.userId();
        String email = verifiedEmail != null ? verifiedEmail : request.email();

        if (userId == null || userId.isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Authentication required to subscribe"))
                    .build();
        }

        log.info("Creating checkout session for user {} (plan={}, cycle={})",
                userId, request.planId(), request.billingCycle());

        try {
            String sessionUrl = stripeService.createCheckoutSession(
                    request.planId(), request.billingCycle(), userId, email);
            return Response.ok(new CheckoutResponse(sessionUrl)).build();
        } catch (StripeException e) {
            log.error("Stripe error creating checkout for user {}: {}", userId, e.getMessage(), e);
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(Map.of("error", "Payment provider error. Please try again.", "code", e.getCode()))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get subscription status", description = "Returns the current subscription plan and status for a user")
    public Response getStatus(@QueryParam("userId") @NotBlank String userId,
            @Context ContainerRequestContext requestContext) {

        String verifiedUid = (String) requestContext.getProperty(FirebaseAuthFilter.FIREBASE_UID);
        String effectiveUserId = verifiedUid != null ? verifiedUid : userId;

        UserQuota quota = quotaService.getQuotaStatus(effectiveUserId);
        if (quota == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "User not found"))
                    .build();
        }

        String planId = quota.getPlanType().name().toLowerCase();
        String status = quota.stripeSubscriptionId != null ? "active" : "free";
        String currentPeriodEnd = null; // populated by webhook when subscription is created

        return Response.ok(new SubscriptionStatusResponse(planId, status, currentPeriodEnd)).build();
    }

    @POST
    @Path("/cancel")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Cancel subscription", description = "Cancels the user's active subscription at period end")
    public Response cancelSubscription(@Context ContainerRequestContext requestContext) {
        String verifiedUid = (String) requestContext.getProperty(FirebaseAuthFilter.FIREBASE_UID);
        if (verifiedUid == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Authentication required"))
                    .build();
        }

        UserQuota quota = quotaService.getQuotaStatus(verifiedUid);
        if (quota == null || quota.stripeSubscriptionId == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "No active subscription found"))
                    .build();
        }

        try {
            com.stripe.model.Subscription subscription =
                    com.stripe.model.Subscription.retrieve(quota.stripeSubscriptionId);
            subscription.cancel();
            log.info("Cancelled subscription {} for user {}", quota.stripeSubscriptionId, verifiedUid);
            return Response.ok(Map.of("success", true, "message", "Subscription cancelled")).build();
        } catch (StripeException e) {
            log.error("Stripe error cancelling subscription for user {}: {}", verifiedUid, e.getMessage(), e);
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(Map.of("error", "Failed to cancel subscription. Please try again."))
                    .build();
        }
    }
}
