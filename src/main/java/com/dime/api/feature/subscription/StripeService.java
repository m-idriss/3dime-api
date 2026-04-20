package com.dime.api.feature.subscription;

import com.dime.api.feature.converter.PlanType;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class StripeService {

    @ConfigProperty(name = "stripe.api.key", defaultValue = "")
    String apiKey;

    @ConfigProperty(name = "stripe.webhook.secret", defaultValue = "")
    String webhookSecret;

    @ConfigProperty(name = "stripe.price.pro.monthly", defaultValue = "")
    String pricePrMonthly;

    @ConfigProperty(name = "stripe.price.pro.yearly", defaultValue = "")
    String pricePrYearly;

    @ConfigProperty(name = "stripe.price.business.monthly", defaultValue = "")
    String priceBusinessMonthly;

    @ConfigProperty(name = "stripe.price.business.yearly", defaultValue = "")
    String priceBusinessYearly;

    @ConfigProperty(name = "stripe.success.url", defaultValue = "https://photocalia.com/subscription/success")
    String successUrl;

    @ConfigProperty(name = "stripe.cancel.url", defaultValue = "https://photocalia.com/pricing")
    String cancelUrl;

    @PostConstruct
    void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            Stripe.apiKey = apiKey;
            log.info("Stripe SDK initialised");
        } else {
            log.warn("STRIPE_SECRET_KEY not configured — subscription checkout will be unavailable");
        }
    }

    /**
     * Creates a Stripe Checkout Session for the given plan and billing cycle.
     * The statement descriptor suffix is set to "PHOTOCALIA" so customers see
     * "3DIME PHOTOCALIA" on their bank statement instead of just "3DIME".
     */
    public String createCheckoutSession(String planId, String billingCycle, String userId, String email)
            throws StripeException {

        String priceId = resolvePriceId(planId, billingCycle);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomerEmail(email)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(1L)
                                .build())
                .putMetadata("userId", userId)
                .putMetadata("planId", planId)
                .putMetadata("billingCycle", billingCycle)
                .setSubscriptionData(
                        SessionCreateParams.SubscriptionData.builder()
                                .putMetadata("userId", userId)
                                .putMetadata("planId", planId)
                                .build())
                .setPaymentIntentData(
                        SessionCreateParams.PaymentIntentData.builder()
                                .setStatementDescriptorSuffix("PHOTOCALIA")
                                .build())
                .build();

        Session session = Session.create(params);
        log.info("Created Stripe Checkout Session {} for user {} (plan={}, cycle={})",
                session.getId(), userId, planId, billingCycle);
        return session.getUrl();
    }

    /**
     * Validates and constructs a Stripe Event from a webhook payload.
     * Throws SignatureVerificationException if the signature is invalid.
     */
    public Event constructWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }

    /**
     * Extracts userId from Stripe Subscription metadata and maps the Stripe
     * plan status to a {@link PlanType}.
     */
    public Optional<Map.Entry<String, PlanType>> resolveUserPlanFromSubscription(Event event) {
        try {
            Subscription subscription = (Subscription) event.getDataObjectDeserializer()
                    .getObject()
                    .orElse(null);

            if (subscription == null) {
                log.warn("Could not deserialise subscription from event {}", event.getId());
                return Optional.empty();
            }

            String userId = subscription.getMetadata().get("userId");
            if (userId == null || userId.isBlank()) {
                log.warn("No userId in subscription metadata for event {}", event.getId());
                return Optional.empty();
            }

            String planId = subscription.getMetadata().getOrDefault("planId", "free");
            PlanType planType = switch (planId) {
                case "business" -> PlanType.UNLIMITED;
                case "pro" -> PlanType.PRO;
                default -> PlanType.FREE;
            };

            return Optional.of(Map.entry(userId, planType));
        } catch (Exception e) {
            log.error("Error resolving user plan from Stripe event {}: {}", event.getId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    private String resolvePriceId(String planId, String billingCycle) {
        return switch (planId + "_" + billingCycle) {
            case "pro_monthly" -> pricePrMonthly;
            case "pro_yearly" -> pricePrYearly;
            case "business_monthly" -> priceBusinessMonthly;
            case "business_yearly" -> priceBusinessYearly;
            default -> throw new IllegalArgumentException(
                    "Unknown plan/cycle combination: " + planId + "/" + billingCycle);
        };
    }
}
