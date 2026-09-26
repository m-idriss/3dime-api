package com.dime.api.feature.subscription;

import com.dime.api.feature.converter.QuotaService;
import com.dime.api.feature.converter.PlanType;
import com.stripe.model.Price;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StripePurchaseTest {
    @Test void checkoutRetriesUseSameStripeIdempotencyKey() throws Exception {
        StripeService service = new StripeService();
        service.checkoutStore = mock(SubscriptionCheckoutStore.class);
        service.pricePlusMonthly = java.util.Optional.of("price_monthly");
        service.successUrl = "https://example.com/success"; service.cancelUrl = "https://example.com/cancel";
        var slot = new SubscriptionCheckoutStore.Slot("monthly", "price_monthly", "test@example.com");
        when(service.checkoutStore.acquire(anyString(), anyString(), anyString(), any())).thenReturn(slot);
        Price price = new Price(); price.setActive(true); price.setCurrency("eur"); price.setUnitAmount(299L);
        Price.Recurring recurrence = new Price.Recurring(); recurrence.setInterval("month"); recurrence.setIntervalCount(1L); price.setRecurring(recurrence);
        Session session = new Session(); session.setId("cs_same"); session.setStatus("open"); session.setUrl("https://checkout.stripe.com/same");
        try (var prices = mockStatic(Price.class); var sessions = mockStatic(Session.class)) {
            prices.when(() -> Price.retrieve("price_monthly")).thenReturn(price);
            java.util.Set<String> keys = new java.util.HashSet<>();
            sessions.when(() -> Session.create(any(com.stripe.param.checkout.SessionCreateParams.class), any(com.stripe.net.RequestOptions.class)))
                    .thenAnswer(call -> { keys.add(((com.stripe.net.RequestOptions)call.getArgument(1)).getIdempotencyKey()); return session; });
            assertEquals(session.getUrl(), service.createCheckoutSession("plus", "monthly", "uid", "test@example.com"));
            assertEquals(session.getUrl(), service.createCheckoutSession("plus", "monthly", "uid", "test@example.com"));
            assertEquals(java.util.Set.of("photocalia-subscription-" + slot.token), keys);
            slot.sessionId = session.getId(); session.setStatus("complete"); session.setPaymentStatus("unpaid");
            sessions.when(() -> Session.retrieve(session.getId())).thenReturn(session);
            assertThrows(IllegalArgumentException.class, () -> service.createCheckoutSession("plus", "monthly", "uid", "test@example.com"));
            verify(service.checkoutStore, never()).replaceExpired(anyString(), anyString(), anyString(), anyString(), any());
        }
    }

    @Test void anotherAccountsCheckoutCannotBeFulfilled() throws Exception {
        StripeService service = new StripeService(); service.quotaService = mock(QuotaService.class);
        try (var sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.retrieve("cs_test_credit")).thenReturn(creditSession("paid", 99L));
            assertThrows(com.dime.api.feature.shared.exception.AuthenticationException.class,
                    () -> service.confirmCheckout("cs_test_credit", "other-user"));
            verifyNoInteractions(service.quotaService);
        }
    }
    @Test void olderWebhookApiVersionStillFulfillsThroughFreshSessionRetrieval() throws Exception {
        StripeService service = new StripeService(); service.quotaService = mock(QuotaService.class);
        com.stripe.model.Event event = com.stripe.net.ApiResource.GSON.fromJson(
                "{\"id\":\"evt_old\",\"object\":\"event\",\"api_version\":\"2020-08-27\","
                + "\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_credit\",\"object\":\"checkout.session\"}}}",
                com.stripe.model.Event.class);
        try (var sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.retrieve("cs_test_credit")).thenReturn(creditSession("paid", 99L));
            service.handleCheckoutEvent(event);
            verify(service.quotaService).grantPaidCredit("uid-1", "cs_test_credit");
        }
    }
    @Test void unpaidCheckoutDoesNotGrantCredit() throws Exception {
        StripeService service = new StripeService();
        service.quotaService = mock(QuotaService.class);
        Session session = creditSession("unpaid", 99L);
        assertFalse(service.fulfillCheckout(session).fulfilled());
        verifyNoInteractions(service.quotaService);
    }
    @Test void paidCheckoutGrantsCreditToAuthenticatedMetadataOwner() throws Exception {
        StripeService service = new StripeService();
        service.quotaService = mock(QuotaService.class);
        assertTrue(service.fulfillCheckout(creditSession("paid", 99L)).fulfilled());
        verify(service.quotaService).grantPaidCredit("uid-1", "cs_test_credit");
    }
    @Test void wrongAmountCannotGrantCredit() {
        StripeService service = new StripeService();
        service.quotaService = mock(QuotaService.class);
        assertThrows(IllegalStateException.class, () -> service.fulfillCheckout(creditSession("paid", 1L)));
        verifyNoInteractions(service.quotaService);
    }
    @Test void wrongRecurringPriceCannotBeSoldAsPlus() {
        Price price = new Price();
        price.setActive(true); price.setCurrency("eur"); price.setUnitAmount(499L);
        Price.Recurring recurring = new Price.Recurring();
        recurring.setInterval("month"); recurring.setIntervalCount(1L); price.setRecurring(recurring);
        assertThrows(IllegalStateException.class, () -> StripeService.validatePlusPrice(price, "monthly"));
        price.setUnitAmount(299L);
        assertDoesNotThrow(() -> StripeService.validatePlusPrice(price, "monthly"));
    }
    @Test void incompleteSubscriptionDoesNotActivatePlus() {
        StripeService service = new StripeService(); service.quotaService = mock(QuotaService.class);
        Subscription sub = new Subscription(); sub.setId("sub_1"); sub.setCustomer("cus_1");
        sub.setMetadata(Map.of("userId", "uid", "planId", "plus")); sub.setStatus("incomplete");
        service.syncSubscription(sub); verifyNoInteractions(service.quotaService);
        sub.setStatus("active"); service.syncSubscription(sub);
        verify(service.quotaService).syncSubscription("uid", PlanType.PLUS, "sub_1", "cus_1");
    }
    private Session creditSession(String status, long amount) {
        Session session = new Session(); session.setId("cs_test_credit");
        session.setMetadata(Map.of("userId", "uid-1", "purchaseType", "conversion_credit"));
        session.setMode("payment"); session.setStatus("complete"); session.setPaymentStatus(status);
        session.setCurrency("eur"); session.setAmountTotal(amount); return session;
    }
}
