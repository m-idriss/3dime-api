package com.dime.api.feature.subscription;

import com.dime.api.feature.converter.QuotaService;
import com.dime.api.feature.converter.UserQuota;
import com.dime.api.feature.shared.config.FirebaseAuthFilter;
import com.dime.api.feature.shared.exception.AuthenticationException;
import com.dime.api.feature.shared.exception.ErrorResponse;
import com.dime.api.feature.shared.exception.ExternalServiceException;
import com.dime.api.feature.shared.exception.ValidationException;
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
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
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
    @APIResponse(responseCode = "200", description = "Checkout session created", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CheckoutResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid checkout request", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "Authentication required", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "502", description = "Payment provider error", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response createCheckout(@Valid CheckoutRequest request,
            @Context ContainerRequestContext requestContext) {

        String verifiedUid = (String) requestContext.getProperty(FirebaseAuthFilter.FIREBASE_UID);
        String verifiedEmail = (String) requestContext.getProperty(FirebaseAuthFilter.FIREBASE_EMAIL);

        String userId = verifiedUid != null ? verifiedUid : request.userId();
        String email = verifiedEmail != null ? verifiedEmail : request.email();

        if (userId == null || userId.isBlank()) {
            throw new AuthenticationException("Authentication required to subscribe");
        }

        log.info("Creating checkout session for user {} (plan={}, cycle={})",
                userId, request.planId(), request.billingCycle());

        try {
            String sessionUrl = stripeService.createCheckoutSession(
                    request.planId(), request.billingCycle(), userId, email);
            return Response.ok(new CheckoutResponse(sessionUrl)).build();
        } catch (StripeException e) {
            log.error("Stripe error creating checkout for user {}: {}", userId, e.getMessage(), e);
            throw new ExternalServiceException("Stripe", "Payment provider error. Please try again.", e);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
    }

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get subscription status", description = "Returns the current subscription plan and status for a user")
    @APIResponse(responseCode = "200", description = "Subscription status retrieved", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SubscriptionStatusResponse.class)))
    @APIResponse(responseCode = "404", description = "User not found", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
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
    @APIResponse(responseCode = "200", description = "Subscription cancelled", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SubscriptionCancellationResponse.class)))
    @APIResponse(responseCode = "401", description = "Authentication required", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "No active subscription", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "502", description = "Payment provider error", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response cancelSubscription(@Context ContainerRequestContext requestContext) {
        String verifiedUid = (String) requestContext.getProperty(FirebaseAuthFilter.FIREBASE_UID);
        if (verifiedUid == null) {
            throw new AuthenticationException("Authentication required");
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
            return Response.ok(new SubscriptionCancellationResponse(true, "Subscription cancelled")).build();
        } catch (StripeException e) {
            log.error("Stripe error cancelling subscription for user {}: {}", verifiedUid, e.getMessage(), e);
            throw new ExternalServiceException("Stripe", "Failed to cancel subscription. Please try again.", e);
        }
    }
}
