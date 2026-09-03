Yes. Looking at your plan together with Lemon Squeezy's current documentation, I would **change the architecture substantially**.

The good news is that what you're trying to build is actually a reasonable use case for Lemon Squeezy **if TukiWatch is a digital software product and the $5/month purchase grants access to software/features**. Lemon Squeezy explicitly supports subscription products with license keys, and the license automatically follows the subscription lifecycle. ([Lemon Squeezy Docs][1])

The biggest mistake in your current plan is that you're building your **own payment-to-token system on top of Lemon Squeezy's licensing system**, when Lemon Squeezy already provides most of what you need.

---

# 1. What you're currently trying to build

Your architecture is essentially:

```text
                 ┌─────────────────┐
                 │  Lemon Squeezy  │
                 │  $5/month       │
                 └────────┬────────┘
                          │
                     webhook
                          │
                          ▼
                 ┌─────────────────┐
                 │   TukiWatch API │
                 └────────┬────────┘
                          │
                    generate UUID
                          │
                          ▼
                 ┌─────────────────┐
                 │      Redis      │
                 │  token → tier   │
                 └────────┬────────┘
                          │
                    email token
                          │
                          ▼
                    ┌───────────┐
                    │   User    │
                    │ Mobile App│
                    └─────┬─────┘
                          │
                   X-Supporter-Token
                          │
                          ▼
                    TukiWatch API
```

So you're trying to create an **account-free entitlement system**.

Instead of:

> "Create an account → log in → subscription belongs to account"

you're saying:

> "Buy supporter subscription → receive an opaque token → put token in the app → token proves you're a supporter."

That's actually a good privacy-oriented design.

Your uploaded plan explicitly describes this as **"Static Token Authentication (privacy-first, account-free)"**, with Redis storing the supporter token and FastAPI validating it. 

The problem isn't the basic idea.

The problem is **who should be responsible for generating and managing the entitlement credential**.

Your plan currently makes **TukiWatch responsible** for something Lemon Squeezy already does.

---

# 2. Lemon Squeezy already gives you the thing you're inventing

This is the important discovery.

Lemon Squeezy has a feature specifically called **License Keys**.

Their documentation says license keys are intended to:

> control access to your software and other digital products

and customers receive a license key after purchasing a product with licensing enabled. ([Lemon Squeezy Docs][2])

So instead of:

```text
Lemon Squeezy
      ↓
webhook
      ↓
TukiWatch generates UUID
      ↓
Redis stores UUID
      ↓
TukiWatch emails UUID
```

you can potentially do:

```text
Lemon Squeezy
      ↓
Customer buys $5/month subscription
      ↓
Lemon Squeezy generates license key
      ↓
Customer receives license key
      ↓
TukiWatch verifies license key
      ↓
Supporter access
```

That's much cleaner.

And there's an especially important feature for you:

### Subscription + License Key

When a Lemon Squeezy product is a subscription **and has license keys enabled**, the license doesn't have a fixed 365-day lifetime.

The license remains active **while the subscription is active**.

When the subscription expires, the license expires too. ([Lemon Squeezy Docs][1])

That eliminates a major part of your current system.

---

# 3. Your current "365-day token" design is actually wrong for a monthly subscription

Your plan says:

```text
Token TTL = 365 days
```

and your code generates:

```python
expires_at = datetime.utcnow() + timedelta(days=365)
```

That's not a good representation of a $5/month subscription.

Imagine:

### January

Customer subscribes.

Your token:

```text
expires: January 2027
```

### February

Customer cancels.

Lemon Squeezy knows the subscription has ended/cancelled.

But your Redis token potentially remains valid until January 2027.

You would have to build additional webhook logic to revoke it.

Your plan recognizes this and proposes:

```text
subscription.expired → revoke token
```

But now you've created your own synchronization system.

Lemon Squeezy's license system already handles this relationship.

For a subscription product:

```text
Subscription ACTIVE
        ↓
License ACTIVE

Subscription cancelled
        ↓
Still active until billing period ends
        ↓
Subscription EXPIRED
        ↓
License EXPIRED
```

That's exactly what you want. ([Lemon Squeezy Docs][1])

---

# 4. I would redesign TukiWatch like this

I would use **three layers**:

```text
                  LEMON SQUEEZY
                       │
                  $5/month
                       │
                 License Key
                       │
                       ▼
              ┌─────────────────┐
              │   TukiWatch API │
              │                 │
              │ License verifier│
              └────────┬────────┘
                       │
                short-lived session
                       │
                       ▼
              ┌─────────────────┐
              │   Mobile App    │
              │                 │
              │ SecureStore      │
              └─────────────────┘
```

The distinction is important:

### Lemon Squeezy license key

**Payment entitlement**

> "This person has purchased TukiWatch."

### TukiWatch session token

**Temporary API authentication**

> "This particular installation has already proven that it owns a valid TukiWatch license."

You don't necessarily want the Lemon Squeezy license key being sent with every API request.

---

# 5. Recommended flow

Here's the architecture I'd build.

```text
                    CUSTOMER
                       │
                       │ buys $5/month
                       ▼
              ┌─────────────────┐
              │  Lemon Squeezy  │
              │                 │
              │ Subscription    │
              │ + License Key   │
              └────────┬────────┘
                       │
                       │ license key
                       ▼
              ┌─────────────────┐
              │   TukiWatch     │
              │   Mobile App    │
              └────────┬────────┘
                       │
                  enter/paste
                  license key
                       │
                       ▼
              ┌─────────────────┐
              │  TukiWatch API  │
              └────────┬────────┘
                       │
                  validate with
                  Lemon Squeezy
                       │
                       ▼
              ┌─────────────────┐
              │ Lemon Squeezy   │
              │ License API     │
              └────────┬────────┘
                       │
                valid = true
                       │
                       ▼
              TukiWatch creates
              local session
                       │
                       ▼
              Mobile stores
              session securely
```

Lemon Squeezy provides a dedicated License API with:

* activate
* validate
* deactivate

operations. ([Lemon Squeezy Docs][3])

---

# 6. You don't need SendGrid for the license

This is another unnecessary part of your current plan.

Your plan says:

```text
Lemon Squeezy
     ↓
Webhook
     ↓
Generate token
     ↓
SendGrid
     ↓
Email token
```

But Lemon Squeezy already generates the license key and emails it to the customer in their receipt when licensing is enabled. The customer can also find it through their Lemon Squeezy orders. ([Lemon Squeezy Docs][4])

So you can remove:

```text
SendGrid
custom token email
email → token mapping
```

at least for the basic licensing flow.

That's a significant simplification.

---

# 7. Your new architecture should look more like this

## Lemon Squeezy

Create:

```text
Product:
    TukiWatch Supporter

Variant:
    Supporter
    $5/month
```

Enable:

```text
Subscription
+
License Keys
```

Lemon Squeezy explicitly supports license keys on subscription products. ([Lemon Squeezy Docs][1])

---

# 8. TukiWatch API

You could have something like:

```text
POST /api/license/activate
POST /api/license/validate
POST /api/license/deactivate
```

But importantly, these aren't necessarily your own license system.

They're your **adapter around Lemon Squeezy's License API**.

For example:

```text
POST /api/license/activate

{
    "license_key": "XXXX-XXXX-XXXX"
}
```

Your server sends that to Lemon Squeezy:

```text
POST https://api.lemonsqueezy.com/v1/licenses/activate
```

Lemon Squeezy returns information about:

```text
license
product
variant
customer
subscription-related state
instance
```

The API specifically returns the product/variant information, and Lemon Squeezy recommends checking that the product/variant belongs to your product so that a license from another Lemon Squeezy product can't be used to unlock your application. ([Lemon Squeezy Docs][4])

That's important.

---

# 9. Don't simply check "valid = true"

Your server should conceptually do:

```python
result = lemonsqueezy.activate_license(license_key)

if not result.activated:
    reject()

if result.product_id != TUKIWATCH_PRODUCT_ID:
    reject()

if result.variant_id not in TUKIWATCH_VARIANTS:
    reject()

if result.license_key.status not in ["active", "inactive"]:
    reject()

accept()
```

The exact implementation can differ, but the security principle is important:

**A valid Lemon Squeezy license is not automatically a valid TukiWatch license.**

It must be a valid license **for your product**.

Lemon Squeezy specifically recommends verifying the store/product/variant identifiers. ([Lemon Squeezy Docs][4])

---

# 10. Then create a TukiWatch session

This is where I would retain part of your original idea.

After successful license verification:

```text
Lemon Squeezy license
        ↓
TukiWatch verifies it
        ↓
TukiWatch creates session
        ↓
Mobile app gets session token
```

For example:

```json
{
  "access_token": "random-short-lived-session-token",
  "tier": "supporter",
  "expires_in": 86400
}
```

Store that access token in:

```text
expo-secure-store
```

which you're already planning to use. Your uploaded plan already proposes SecureStore for supporter credentials. 

Then your normal API calls become:

```http
Authorization: Bearer <tuki_session>
```

rather than:

```http
X-Supporter-Token: <permanent-license-key>
```

---

# 11. Why I prefer this over sending the license key everywhere

A Lemon Squeezy license key is effectively a **credential**.

You don't want:

```text
every API request
       ↓
Lemon Squeezy license key
```

because if it leaks through logs, debugging, proxies, analytics, etc., someone could potentially use it.

Instead:

```text
License Key
     ↓
one-time / occasional verification
     ↓
TukiWatch session
     ↓
normal API traffic
```

That's much cleaner.

---

# 12. Redis becomes optional rather than fundamental

Your current plan makes Redis the heart of authentication:

```text
Redis
 ↓
token
 ↓
tier
 ↓
expiration
 ↓
revocation
```

I wouldn't do that.

Instead, Redis can become a **cache/session store**.

For example:

```text
Redis

session:<hash>
    tier=supporter
    license_id=123
    expires=...
```

You don't need to store the raw Lemon Squeezy license key.

You can hash identifiers.

Then:

```text
Mobile
  ↓
TukiWatch session
  ↓
Redis
  ↓
supporter
```

This is much closer to normal authentication architecture.

---

# 13. What happens when the subscription expires?

This is where the Lemon Squeezy integration becomes very nice.

You don't need to invent:

```text
365-day token
```

Instead:

```text
$5 subscription active
        ↓
license active
        ↓
TukiWatch supporter

subscription cancelled
        ↓
continues until end of billing period
        ↓
subscription expires
        ↓
license expires
        ↓
TukiWatch eventually sees
invalid license
        ↓
supporter access removed
```

Lemon Squeezy documents that subscription license keys remain active while the subscription is active and expire when the subscription expires. ([Lemon Squeezy Docs][1])

---

# 14. You should still use webhooks

But **for synchronization**, not for inventing your own license.

This changes your webhook architecture from:

```text
webhook
   ↓
generate license
   ↓
store license
   ↓
email license
   ↓
manage subscription
```

to:

```text
Lemon Squeezy
      │
      ├── license_key_created
      ├── subscription_payment_success
      ├── subscription_updated
      ├── subscription_cancelled
      └── subscription_expired
               │
               ▼
        TukiWatch webhook
               │
               ▼
       update local cache
```

Lemon Squeezy provides these webhook events, including `license_key_created`, subscription events and payment events. ([Lemon Squeezy Docs][5])

The webhook can therefore keep your local entitlement/session state fresh.

---

# 15. Your webhook should NOT be the only source of truth

This is another change I'd make.

Don't design:

```text
Webhook received
     ↓
supporter = true
```

forever.

Webhooks can fail, networks can fail, servers can go down, etc.

Lemon Squeezy says webhook requests that don't receive a 200 can be retried, and recommends storing webhook events locally or in a cache so they can be processed reliably. ([Lemon Squeezy Docs][6])

Instead:

### Webhook

Good for:

> "Something changed; update our local state."

### License API

Good for:

> "Is this license actually valid right now?"

That gives you two layers of reliability.

---

# 16. One problem with your proposed rate limiting

You currently have:

```text
Free user:
100 requests/min

Supporter:
1000 requests/min
```

and you want:

```text
free → IP-based
supporter → token-based
```

I would **not use the permanent Lemon Squeezy license key as the rate-limit identity**.

Instead:

```text
anonymous:
    ip:<ip>

authenticated supporter:
    session:<session_id/hash>
```

So:

```text
Free:

IP ────────────→ rate limiter


Supporter:

Session ───────→ rate limiter
                     │
                     ▼
                  supporter
```

This also solves your privacy objective better.

---

# 17. Your "no PII in API logs" goal is good

I would keep this.

Your current plan explicitly calls out:

> No email/PII in rate-limit keys

and:

> Token validation reveals no user data. 

That's a good principle.

But remember:

**Lemon Squeezy's license API response itself contains customer information**, including customer email. ([Lemon Squeezy Docs][4])

So your backend should treat the Lemon Squeezy response as sensitive and **not log the whole response**.

Do something like:

```text
LOG:

license verification successful
product_id=123
variant_id=456
license_status=active

DO NOT LOG:

customer_email
customer_name
full license key
full webhook payload
```

---

# 18. There's an even better privacy architecture

If you want TukiWatch to remain genuinely account-free:

```text
                  Lemon Squeezy
                       │
                  license key
                       │
                       ▼
                TukiWatch API
                       │
                verify license
                       │
                       ▼
              random session ID
                       │
                       ▼
                 Mobile App
```

Your database doesn't need:

```text
User
 ├── name
 ├── email
 ├── password
 └── account
```

Instead, you can have:

```text
Entitlement
 ├── internal ID
 ├── license ID
 ├── product ID
 ├── variant ID
 ├── status
 └── timestamps
```

You don't need to expose the Lemon Squeezy customer's identity to the rest of your system.

---

# 19. Activation is particularly useful for your mobile app

Lemon Squeezy's licensing system supports **license instances**.

When you activate a license, an instance is created. Lemon Squeezy describes an instance as a recorded use of the license key, and you can later validate/deactivate individual instances. ([Lemon Squeezy Docs][4])

That maps nicely to:

```text
License
   │
   ├── iPhone instance
   ├── Android instance
   └── tablet instance
```

You could set an activation limit depending on your business model.

For example:

```text
Supporter $5/month

Activation limit:
2 devices
```

Then a customer could use TukiWatch on:

```text
Phone
+
Tablet
```

but not share their license with 50 people.

Lemon Squeezy itself enforces the activation limit. ([Lemon Squeezy Docs][4])

That's considerably better than trying to invent your own device/token system.

---

# 20. Your new flow could therefore be

I'd make it:

```text
┌──────────────────────────────┐
│       LEMON SQUEEZY          │
│                              │
│ TukiWatch Supporter          │
│ $5/month                     │
│ Subscription + License Key   │
└──────────────┬───────────────┘
               │
               │ Customer receives license
               ▼
┌──────────────────────────────┐
│        TUKIWATCH APP         │
│                              │
│ "Enter your license key"     │
└──────────────┬───────────────┘
               │
               │ HTTPS
               ▼
┌──────────────────────────────┐
│       TUKIWATCH API          │
│                              │
│ 1. Validate/activate key     │
│ 2. Verify product ID         │
│ 3. Verify variant ID         │
│ 4. Check license status      │
│ 5. Create local session      │
└──────────────┬───────────────┘
               │
               │ session token
               ▼
┌──────────────────────────────┐
│      EXPO SECURE STORE       │
│                              │
│ TukiWatch session            │
└──────────────┬───────────────┘
               │
               │ Bearer token
               ▼
┌──────────────────────────────┐
│        TUKIWATCH API         │
│                              │
│ Free → IP rate limit         │
│ Supporter → session limit    │
└──────────────────────────────┘
```

And separately:

```text
Lemon Squeezy
      │
      │ webhook
      ▼
TukiWatch API
      │
      ▼
Update entitlement/cache
```

---

# 21. What I'd remove from your current plan

I would remove these pieces:

### ❌ Custom UUID supporter token

You currently have:

```python
tw_supp_<uuid>
```

Don't create it.

Lemon Squeezy creates the license key.

---

### ❌ 365-day TTL

Remove:

```text
Token TTL = 365 days
```

Your subscription license should follow the subscription lifecycle.

---

### ❌ SendGrid token delivery

Remove the custom:

```text
generate token → email token
```

Lemon Squeezy can deliver the license key to the customer. ([Lemon Squeezy Docs][4])

---

### ❌ email → token mapping

You don't need:

```text
email → supporter token
```

for normal authentication.

This creates additional PII handling.

---

### ❌ SQLite token audit table

Your current table:

```sql
token_audit
```

exists largely because you're inventing the token system.

You probably don't need it in that form.

---

### ❌ Rotate token on subscription renewal

Your current plan says:

```text
subscription.renewed → rotate token
```

I would **not** do that.

A monthly renewal doesn't need a new license key.

The license remains associated with the subscription. ([Lemon Squeezy Docs][1])

---

# 22. What I'd keep

I'd keep:

### ✅ Redis

But use it for:

* session tokens
* entitlement cache
* rate limiting
* temporary activation state

rather than being your primary license database.

### ✅ Expo SecureStore

Absolutely keep it for storing the TukiWatch session credential.

### ✅ FastAPI middleware

Keep your supporter/free distinction.

But change:

```text
X-Supporter-Token
```

to something like:

```http
Authorization: Bearer <tuki-session>
```

### ✅ Rate-limit differentiation

Keep the concept:

```text
free
supporter
```

Just change the identity mechanism.

### ✅ Webhooks

Keep them.

Use them to synchronize Lemon Squeezy events.

### ✅ Privacy-first design

Definitely keep that philosophy.

---

# 23. There is one VERY important issue you haven't accounted for

This may be more important than all the backend architecture:

## TukiWatch is a mobile app.

If you're planning to sell the $5/month subscription **inside the iOS/Android app**, you need to consider Apple's and Google's payment rules separately from Lemon Squeezy's rules.

Apple's current App Store guidelines say that if an app unlocks features/functionality or premium content inside the app, it generally must use **In-App Purchase**, and Apple specifically says apps may not use mechanisms such as license keys to unlock in-app functionality. ([Apple Developer][7])

Google Play likewise says apps distributed through Google Play generally must use Google Play Billing for digital goods, app functionality and subscriptions, subject to its regional/program exceptions. ([Google Help][8])

So you need to distinguish:

### Scenario A — TukiWatch is a standalone downloadable digital software product

Lemon Squeezy licensing makes a lot of sense.

### Scenario B — TukiWatch is on the App Store/Google Play and $5/month unlocks features inside the mobile app

Then **Lemon Squeezy licensing may not be your primary mobile-app payment solution**, because Apple/Google rules enter the picture.

That is a major architectural decision.

---

# 24. I would decide this before writing any code

You essentially have two possible business architectures.

## Option 1 — Lemon Squeezy-centered

```text
Website
   ↓
Lemon Squeezy
   ↓
Subscription
   ↓
License key
   ↓
TukiWatch
```

Good for:

* web application
* desktop application
* externally distributed software
* digital software products

---

## Option 2 — App Store / Google Play-centered

```text
iOS
 ↓
Apple In-App Purchase
 ↓
Apple entitlement

Android
 ↓
Google Play Billing
 ↓
Google entitlement

TukiWatch API
 ↓
verify platform purchase
 ↓
supporter entitlement
```

Lemon Squeezy could potentially be used for a **separate web/desktop channel**, but you'd need to design entitlement reconciliation carefully.

---

# 25. There's also a subtle problem with calling it "Supporter Tier"

This matters for Lemon Squeezy approval.

You were previously rejected because Lemon Squeezy thought your business was selling services.

So I would **not pitch the product as**:

> "Support my project for $5/month."

I'd make the actual product explicit:

> **TukiWatch Supporter — $5/month**

> A digital subscription that unlocks [specific software features] in TukiWatch.

For example:

```text
Free
- basic TukiWatch functionality
- 100 API requests/min

Supporter — $5/month
- higher API limits
- higher-quality data
- supporter-only features
- etc.
```

If those are genuinely the digital benefits, that's much more clearly a **digital software subscription** rather than a service.

Lemon Squeezy describes its products as digital downloads and subscriptions, and its licensing system is specifically designed to control access to software/digital products. ([Lemon Squeezy Docs][9])

You should still get Lemon Squeezy to approve the exact product/business model rather than assuming the license feature guarantees approval.

---

# 26. My recommended architecture

If your distribution model permits Lemon Squeezy, I'd simplify the original 5-phase plan into this:

### Phase 1 — Lemon Squeezy product

Create:

```text
TukiWatch Supporter
$5/month
Subscription
License keys enabled
```

Get this approved **before implementing everything else**.

---

### Phase 2 — License integration

Implement:

```text
POST /api/license/activate
POST /api/license/validate
POST /api/license/deactivate
```

Your API communicates with Lemon Squeezy.

Verify:

```text
license valid
+
correct store
+
correct product
+
correct variant
```

---

### Phase 3 — TukiWatch sessions

After successful activation:

```text
Lemon license
      ↓
TukiWatch session
```

Store only the TukiWatch session in SecureStore.

Don't make the Lemon license key your permanent API authentication credential.

---

### Phase 4 — Entitlement cache

Redis:

```text
session → supporter
```

with expiration.

Periodically revalidate the underlying Lemon Squeezy license.

---

### Phase 5 — Webhooks

Listen to relevant events such as:

```text
license_key_created
subscription_payment_success
subscription_updated
subscription_cancelled
subscription_expired
```

Use them to update your local entitlement/cache. ([Lemon Squeezy Docs][5])

---

### Phase 6 — Rate limiting

```text
anonymous
    ↓
IP

authenticated
    ↓
TukiWatch session
```

Not Lemon license key.

---

# 27. Your final architecture in one diagram

This is what I would aim for:

```text
                         ┌──────────────────────┐
                         │    LEMON SQUEEZY     │
                         │                      │
                         │ TukiWatch Supporter  │
                         │      $5 / month      │
                         │                      │
                         │ Subscription         │
                         │ + License Key        │
                         └──────────┬───────────┘
                                    │
                         license key│
                                    ▼
                         ┌──────────────────────┐
                         │    TUKIWATCH APP     │
                         │                      │
                         │ Enter license key    │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │    TUKIWATCH API     │
                         │                      │
                         │ License verification │
                         └──────────┬───────────┘
                                    │
                         HTTPS      │
                                    ▼
                         ┌──────────────────────┐
                         │   LEMON LICENSE API  │
                         │                      │
                         │ activate / validate  │
                         └──────────┬───────────┘
                                    │
                              valid + correct
                                product
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │    TUKIWATCH API     │
                         │                      │
                         │ Create session       │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │    REDIS             │
                         │                      │
                         │ session → supporter  │
                         │ rate limiting        │
                         └──────────────────────┘


             ┌───────────────────────────────────────┐
             │         LEMON SQUEEZY WEBHOOKS        │
             │                                       │
             │ subscription/payment/license events   │
             └──────────────────┬────────────────────┘
                                │
                                ▼
                         TukiWatch API
                                │
                                ▼
                       update entitlement
```

**This gives you the privacy/account-free experience you wanted, while letting Lemon Squeezy own the thing it is actually designed to own: the commercial license/subscription entitlement.**

And there's a nice conceptual separation:

> **Lemon Squeezy answers: "Did this customer purchase TukiWatch and is their subscription valid?"**

> **TukiWatch answers: "What can this authenticated installation do?"**

That's a much better boundary than your original architecture.

### One thing I would do before you implement anything

**Do not start coding the current plan yet.** First determine whether you intend to sell the $5/month subscription **inside the iOS/Android app or through a website**. That answer changes the payment architecture substantially because of Apple's and Google's current digital-purchase rules. ([Apple Developer][7])

If you tell me **(1) what TukiWatch actually does, (2) what the $5 supporter gets, and (3) whether you're distributing it through Apple App Store, Google Play, a website, or all three**, I can redesign your entire implementation plan—**database schema, FastAPI endpoints, Redis model, Lemon Squeezy configuration, license activation flow, webhook events, and Expo code structure**—around the correct architecture rather than the current UUID-token approach.

[1]: https://docs.lemonsqueezy.com/help/licensing/license-keys-subscriptions?utm_source=chatgpt.com "Docs: License Keys and Subscriptions • Lemon Squeezy"
[2]: https://docs.lemonsqueezy.com/help/licensing/generating-license-keys?utm_source=chatgpt.com "Docs: Generating License Keys • Lemon Squeezy"
[3]: https://docs.lemonsqueezy.com/api/license-api?utm_source=chatgpt.com "API Docs: License API • Lemon Squeezy"
[4]: https://docs.lemonsqueezy.com/guides/tutorials/license-keys?utm_source=chatgpt.com "Guides: Validating License Keys With the License API • Lemon Squeezy"
[5]: https://docs.lemonsqueezy.com/help/webhooks/event-types?utm_source=chatgpt.com "Docs: Event Types • Lemon Squeezy"
[6]: https://docs.lemonsqueezy.com/guides/developer-guide/webhooks?utm_source=chatgpt.com "Guides: Sync With Webhooks • Lemon Squeezy"
[7]: https://developer.apple.com/app-store/review/guidelines/?utm_source=chatgpt.com "App Review Guidelines - Apple Developer"
[8]: https://support.google.com/googleplay/android-developer/answer/10281818?hl=en&utm_source=chatgpt.com "Understanding Google Play’s Payments policy - Play Console Help"
[9]: https://docs.lemonsqueezy.com/help/products?utm_source=chatgpt.com "Docs: Products • Lemon Squeezy"

