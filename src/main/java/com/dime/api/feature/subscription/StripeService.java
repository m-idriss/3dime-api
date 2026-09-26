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

    @ConfigProperty(name = "stripe.price.plus.monthly")
    Optional<String> pricePlusMonthly;

    @ConfigProperty(name = "stripe.price.plus.yearly")
    Optional<String> pricePlusYearly;

    @jakarta.inject.Inject
    com.dime.api.feature.converter.QuotaService quotaService;

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

        if (!"plus".equals(planId)) throw new IllegalArgumentException("Only Plus is available for new subscriptions");
        String priceId = resolvePriceId(planId, billingCycle);
        validatePlusPrice(com.stripe.model.Price.retrieve(priceId), billingCycle);

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

    static void validatePlusPrice(com.stripe.model.Price price, String billingCycle) {
        long amount = "yearly".equals(billingCycle) ? 2999L : 299L;
        String interval = "yearly".equals(billingCycle) ? "year" : "month";
        if (!Boolean.TRUE.equals(price.getActive()) || !"eur".equals(price.getCurrency())
                || !Long.valueOf(amount).equals(price.getUnitAmount()) || price.getRecurring() == null
                || !interval.equals(price.getRecurring().getInterval())
                || !Long.valueOf(1).equals(price.getRecurring().getIntervalCount())) {
            throw new IllegalStateException("Stripe Plus price does not match the published EUR offer");
        }
    }

    public String createCreditSession(String userId, String email) throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomerEmail(email)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .putMetadata("userId", userId)
                .putMetadata("purchaseType", "conversion_credit")
                .addLineItem(SessionCreateParams.LineItem.builder().setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("eur").setUnitAmount(99L)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("PhotoCalia — 1 conversion")
                                        .setDescription("One conversion credit. No subscription. Credit restored if conversion fails.")
                                        .build()).build()).build())
                .build();
        return Session.create(params).getUrl();
    }

    public CheckoutStatusResponse confirmCheckout(String sessionId, String verifiedUserId) throws StripeException {
        Session session = Session.retrieve(sessionId);
        if (session.getMetadata() == null || !verifiedUserId.equals(session.getMetadata().get("userId"))) {
            throw new com.dime.api.feature.shared.exception.AuthenticationException("Checkout belongs to another account");
        }
        return fulfillCheckout(session);
    }

    CheckoutStatusResponse fulfillCheckout(Session session) throws StripeException {
        Map<String, String> metadata = session.getMetadata();
        String userId = metadata == null ? null : metadata.get("userId");
        boolean credit = metadata != null && "conversion_credit".equals(metadata.get("purchaseType"));
        String kind = credit ? "credit" : "subscription";
        if (userId == null || userId.isBlank() || !"complete".equals(session.getStatus())
                || !"paid".equals(session.getPaymentStatus())) {
            return new CheckoutStatusResponse(false, kind);
        }
        if (credit) {
            if (!"payment".equals(session.getMode()) || !"eur".equals(session.getCurrency())
                    || !Long.valueOf(99).equals(session.getAmountTotal())) {
                throw new IllegalStateException("Unexpected conversion credit payment");
            }
            quotaService.grantPaidCredit(userId, session.getId());
            return new CheckoutStatusResponse(true, kind);
        }
        if ("subscription".equals(session.getMode()) && session.getSubscription() != null) {
            Subscription subscription = Subscription.retrieve(session.getSubscription());
            syncSubscription(subscription);
            return new CheckoutStatusResponse("active".equals(subscription.getStatus()), kind);
        }
        return new CheckoutStatusResponse(false, kind);
    }

    public void handleCheckoutEvent(Event event) throws StripeException {
        // Signed events can use an older API version than the SDK. Read only the ID and
        // retrieve current provider state with this SDK's API version before fulfillment.
        fulfillCheckout(Session.retrieve(eventObjectId(event)));
    }

    public void handleSubscriptionEvent(Event event) throws StripeException {
        syncSubscription(Subscription.retrieve(eventObjectId(event)));
    }

    private String eventObjectId(Event event) {
        try {
            String id = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(event.getDataObjectDeserializer().getRawJson()).path("id").asText();
            if (id.isBlank()) throw new IllegalStateException("Stripe event has no object ID");
            return id;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read signed Stripe event object ID", e);
        }
    }

    void syncSubscription(Subscription subscription) {
        String userId = subscription.getMetadata().get("userId");
        String planId = subscription.getMetadata().get("planId");
        if (userId == null || userId.isBlank() || planId == null) return;
        PlanType plan = switch (planId) {
            case "plus" -> PlanType.PLUS;
            case "pro" -> PlanType.PRO;
            case "business" -> PlanType.BUSINESS;
            default -> throw new IllegalStateException("Unknown subscription plan");
        };
        String status = subscription.getStatus();
        if ("incomplete".equals(status)) return;
        if (!"active".equals(status) && !"trialing".equals(status) && !"past_due".equals(status)) {
            plan = PlanType.FREE;
        }
        quotaService.syncSubscription(userId, plan, subscription.getId(), subscription.getCustomer());
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
                case "plus" -> PlanType.PLUS;
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
            case "plus_monthly" -> pricePlusMonthly.filter(p -> !p.isBlank()).orElseThrow(() ->
                    new IllegalStateException("STRIPE_PRICE_PLUS_MONTHLY not configured"));
            case "plus_yearly" -> pricePlusYearly.filter(p -> !p.isBlank()).orElseThrow(() ->
                    new IllegalStateException("STRIPE_PRICE_PLUS_YEARLY not configured"));
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
