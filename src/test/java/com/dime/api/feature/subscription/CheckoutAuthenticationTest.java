package com.dime.api.feature.subscription;

import com.dime.api.feature.converter.QuotaService;
import com.dime.api.feature.shared.exception.AuthenticationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CheckoutAuthenticationTest {
    @Test void clientSuppliedUserIdCannotAuthorizeSubscription() {
        SubscriptionResource resource = new SubscriptionResource();
        resource.stripeService = mock(StripeService.class);
        resource.quotaService = mock(QuotaService.class);
        var context = mock(ContainerRequestContext.class);
        assertThrows(AuthenticationException.class, () -> resource.createCheckout(
                new CheckoutRequest("plus", "monthly", "forged", "forged@example.test"), context));
        verifyNoInteractions(resource.stripeService, resource.quotaService);
    }
    @Test void creditPurchaseAndPaymentConfirmationRequireVerifiedIdentity() {
        SubscriptionResource resource = new SubscriptionResource();
        resource.stripeService = mock(StripeService.class);
        var context = mock(ContainerRequestContext.class);
        assertThrows(AuthenticationException.class, () -> resource.buyCredit(context));
        assertThrows(AuthenticationException.class, () -> resource.checkoutStatus("cs_someone_else", context));
        verifyNoInteractions(resource.stripeService);
    }
}
