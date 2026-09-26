# PhotoCalia pricing and payment fulfillment

New public offer: FREE (3 monthly conversions), PLUS (15 monthly conversions), and one additional conversion credit for EUR 0.99. Plus costs EUR 2.99/month or EUR 29.99/year. Annual billing retains a monthly allowance; quotas reset at the start of the UTC calendar month without rollover.

PRO (100) and BUSINESS (120) remain legacy entitlements. Do not change their limits or existing Stripe prices/subscriptions. They are not accepted in new checkout requests and are excluded from the public plans listing. The administrative `/v1/users/plans` endpoint still exposes every configured legacy limit. PLUS uses its own enum value and `QUOTA_LIMIT_PLUS` setting (default 15).

## Configuration

Create separate Stripe recurring EUR prices: 299 cents/month and 2999 cents/year. Set `STRIPE_PRICE_PLUS_MONTHLY` and `STRIPE_PRICE_PLUS_YEARLY`. The API validates their amount, currency, active flag and recurrence before creating checkout. Leave existing PRO/BUSINESS configuration intact.

One-time conversion checkout uses server-side price data fixed at 99 cents, EUR, quantity 1. No client-provided price, user ID or credit count is trusted. All checkout/status operations require a verified Firebase identity.

Subscribe the Stripe webhook to:
- checkout.session.completed
- checkout.session.async_payment_succeeded
- customer.subscription.created
- customer.subscription.updated
- customer.subscription.deleted

Fulfill only paid, completed Checkout Sessions. The webhook and authenticated confirmation endpoint share fulfillment. Firestore stores one payment ledger entry per Checkout Session under users/{uid}/creditPayments/{sessionId}; granting a credit and recording the ledger occur in one transaction. Database errors propagate for webhook retries.

Paid credits are distinct from monthly usage. Reserve one atomically when monthly/free-protection allowance is exhausted; refund it on processing failure or expired reservation. Use monthly allowance first. Never reset paid credits on subscription changes or at month boundaries. One source file is one conversion; PDF pages are sent together.

The frontend polls /subscriptions/checkout-status?sessionId=... after return; the return URL alone does not activate a purchase. It can preserve pending source files in account-bound local IndexedDB for the checkout handoff; files are removed on resume or discarded on next access after one hour.

When the account reads its quota or starts a conversion, expired reservations are reconciled transactionally. Credits are restored once, expired keys stay terminal, and a new attempt must use a new idempotency key. Late completion cannot consume a restored credit again.

Subscription checkout uses a transactional per-account slot under `users/{uid}/billingCheckout/subscription`. Repeated attempts reuse one Stripe idempotency key and Checkout Session. Changing billing cycle first expires the old session at Stripe; a completed payment blocks a second checkout until the subscription is terminal. Storage errors fail closed. If a session was created but its identifier could not be persisted, retries recover it through the same Stripe key for up to 23 hours; beyond that, support must reconcile the uncertain session before releasing the slot (Stripe may prune idempotency keys after 24 hours).

Cancellation schedules cancel_at_period_end. Existing subscription changes should be handled through support; do not create overlapping subscriptions.

## Release checks

Deploy backend before frontend. Configure prices and webhook events in Stripe test mode first. Verify one-time paid/unpaid flows, replayed webhook delivery, quota boundary concurrency, provider failure refunds, cross-account access denial, annual price/interval and cancellation at period end. Repeat a hosted Checkout test with the deployment before enabling production purchase traffic. Never charge a real card as an automated check.

The local implementation and unit tests do not establish that Stripe production configuration is activated.
