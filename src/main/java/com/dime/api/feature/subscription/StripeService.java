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

    @ConfigProperty(name = "stripe.api.key")
    Optional<String> apiKey;

    @ConfigProperty(name = "stripe.webhook.secret")
    Optional<String> webhookSecret;

    @ConfigProperty(name = "stripe.price.pro.monthly")
    Optional<String> pricePrMonthly;

    @ConfigProperty(name = "stripe.price.pro.yearly")
    Optional<String> pricePrYearly;

    @ConfigProperty(name = "stripe.price.business.monthly")
    Optional<String> priceBusinessMonthly;

    @ConfigProperty(name = "stripe.price.business.yearly")
    Optional<String> priceBusinessYearly;

    @ConfigProperty(name = "stripe.price.coffee")
    Optional<String> priceCoffee;

    @ConfigProperty(name = "stripe.price.snack")
    Optional<String> priceSnack;

    @ConfigProperty(name = "stripe.price.meal")
    Optional<String> priceMeal;

    @ConfigProperty(name = "stripe.success.url", defaultValue = "https://photocalia.com/subscription/success")
    String successUrl;

    @ConfigProperty(name = "stripe.cancel.url", defaultValue = "https://photocalia.com/pricing")
    String cancelUrl;

    @ConfigProperty(name = "stripe.donation.success.url", defaultValue = "https://photocalia.com/donation/success")
    String donationSuccessUrl;

    @ConfigProperty(name = "stripe.donation.cancel.url", defaultValue = "https://photocalia.com/pricing")
    String donationCancelUrl;

    @PostConstruct
    void init() {
        if (apiKey.filter(k -> !k.isBlank()).isPresent()) {
            Stripe.apiKey = apiKey.get();
            log.info("Stripe SDK initialised");
        } else {
            log.warn("STRIPE_SECRET_KEY not configured — subscription checkout will be unavailable");
        }
    }

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
                .build();

        Session session = Session.create(params);
        log.info("Created Stripe Checkout Session {} for user {} (plan={}, cycle={})",
                session.getId(), userId, planId, billingCycle);
        return session.getUrl();
    }

    public String createDonationSession(String productId, String email) throws StripeException {
        String priceId = switch (productId) {
            case "coffee" -> priceCoffee.orElseThrow(() -> new IllegalStateException("STRIPE_PRICE_COFFEE not configured"));
            case "snack" -> priceSnack.orElseThrow(() -> new IllegalStateException("STRIPE_PRICE_SNACK not configured"));
            case "meal" -> priceMeal.orElseThrow(() -> new IllegalStateException("STRIPE_PRICE_MEAL not configured"));
            default -> throw new IllegalArgumentException("Unknown donation product: " + productId);
        };

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(donationSuccessUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(donationCancelUrl)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(1L)
                                .build());

        if (email != null && !email.isBlank()) {
            builder.setCustomerEmail(email);
        }

        Session session = Session.create(builder.build());
        log.info("Created donation Checkout Session {} for product {}", session.getId(), productId);
        return session.getUrl();
    }

    public Event constructWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        String secret = webhookSecret.filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalStateException("STRIPE_WEBHOOK_SECRET not configured"));
        return Webhook.constructEvent(payload, sigHeader, secret);
    }

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
                case "business" -> PlanType.BUSINESS;
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
            case "pro_monthly" -> pricePrMonthly.orElseThrow(() ->
                    new IllegalStateException("STRIPE_PRICE_PRO_MONTHLY not configured"));
            case "pro_yearly" -> pricePrYearly.orElseThrow(() ->
                    new IllegalStateException("STRIPE_PRICE_PRO_YEARLY not configured"));
            case "business_monthly" -> priceBusinessMonthly.orElseThrow(() ->
                    new IllegalStateException("STRIPE_PRICE_BUSINESS_MONTHLY not configured"));
            case "business_yearly" -> priceBusinessYearly.orElseThrow(() ->
                    new IllegalStateException("STRIPE_PRICE_BUSINESS_YEARLY not configured"));
            default -> throw new IllegalArgumentException(
                    "Unknown plan/cycle combination: " + planId + "/" + billingCycle);
        };
    }
}
