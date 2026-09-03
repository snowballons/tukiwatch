# TukiWatch Website Transformation Plan

> Goal: Rebuild the marketing/landing website in `app/web/` using real app assets from `app/assets/`, and add a **Supporter** page with Lemon Squeezy pricing.

---

## 1. Current State Audit

### What exists today

| File | Notes |
|---|---|
| `app/web/index.html` | Static HTML landing page, externally-hosted assets (CDN URLs), generic feature copy |
| `app/web/style.css` | Basic dark theme (`#1a1a1a` bg, green `#00ff7f` accent), responsive |
| `app/web/script.js` | Smooth scroll, fade-in observer, dynamic APK version fetch from `version.json` |
| `app/web/logo.svg` | Self-hosted (good) |
| `app/web/preview.png` | External URL reference — **old screenshot** |
| `app/web/privacy.html` | Existing privacy policy |
| `app/web/terms.html` | Existing terms of service |

### Assets available

| Asset | Size | Use |
|---|---|---|
| `app/assets/logo.png` | 2048×2048 | Logo/icon — swap out SVG for PNG where raster is needed |
| `app/assets/live-screen.png` | 720×1640 | Hero phone mockup |
| `app/assets/empty-live-screen.png` | 720×1640 | "No streams" state screenshot |
| `app/assets/add-stream.png` | 720×1640 | Add stream flow screenshot |
| `app/assets/intro1.png` | 720×1640 | Onboarding screen 1 |
| `app/assets/intro2.png` | 720×1640 | Onboarding screen 2 |
| `app/assets/favicon.png` | 512×512 | Favicon |

---

## 2. Target Site Structure

```
app/web/
├── index.html          # Landing page (redesigned)
├── supporter.html      # NEW — Supporter pricing page
├── privacy.html        # Existing (keep as-is)
├── terms.html          # Existing (keep as-is)
├── style.css           # Redesign with new components
├── script.js           # Updated + supporter page logic
├── logo.svg            # Keep (already self-hosted)
└── assets/             # NEW — symlink or copy of relevant app/assets
    ├── hero-phone.png
    ├── empty-screen.png
    ├── add-stream.png
    ├── intro1.png
    ├── intro2.png
    ├── live-screen.png
    └── favicon.png
```

---

## 3. Landing Page (`index.html`) — Section-by-Section

### 3.1 Header / Nav

- Logo SVG (self-hosted from `web/logo.svg`)
- Links: Features · About · Supporter · GitHub
- **CTA button**: "Download APK" → external download link
- Mobile: hamburger nav

### 3.2 Hero Section

**Purpose:** Immediately communicate what TukiWatch does and get downloads.

```
┌─────────────────────────────────────────┐
│                                         │
│   [Logo]  TukiWatch                     │
│                                         │
│   Track every live stream.              │
│   Watch ad-free. No account needed.     │
│                                         │
│   [Download APK v1.0.6]  [View on GitHub]│
│                                         │
│        [Phone mockup - live-screen.png]  │
│                                         │
└─────────────────────────────────────────┘
```

**Changes from current:**
- Replace external CDN image with local `assets/live-screen.png`
- Shorten tagline — "Track every live stream. Watch ad-free. No account needed."
- Add version badge next to download button (keep dynamic JS fetch)
- Use `logo.png` as fallback favicon (512×512)

### 3.3 Platforms Strip

**Purpose:** Show supported platforms at a glance.

```
Trusted by streamers on:
[Twitch] [YouTube] [Kick] [Facebook Live] [TikTok] ...
```

Minimal icon strip or text chips — no need for heavy assets.

### 3.4 Features Grid

**Purpose:** The core value props. 2 columns on desktop, 1 on mobile.

| Feature | Copy | Screenshot |
|---|---|---|
| Live Now tab | See only who's streaming right now. Filter by platform, search, pull-to-refresh. | `empty-live-screen.png` |
| My List | Every favorite, online/offline status, full add/remove management. | `live-screen.png` |
| 20+ platforms | Add streams from Twitch, YouTube, Kick, TikTok, Facebook, Instagram, and more. | `add-stream.png` |
| In-app player | Native playback with quality switching, PiP, and deep links to official apps. | *(no screenshot yet)* |
| Self-host API | Point the app at your own backend via QR code or deep link — no rebuild. | *(no screenshot yet)* |
| Ad-free Twitch | Configure a Twitch Turbo token on the backend for ad-free streams. | *(no screenshot yet)* |
| Privacy-first | No accounts, no cloud, no data collection. Everything lives on your device. | *(no screenshot yet)* |

### 3.5 App Screenshots Carousel

**Purpose:** Visual proof of the app.

- Horizontal scroll or static grid of the 5 phone screenshots
- Use `intro1.png`, `intro2.png`, `live-screen.png`, `empty-live-screen.png`, `add-stream.png`
- Label each: Onboarding → Live Now → My List → Add Stream → Player

### 3.6 How It Works

**Purpose:** Demystify the self-hosted model.

```
Step 1: Download the APK
Step 2: (Optional) Set up your own backend — scan QR or paste URL
Step 3: Add your favorite streamers
Step 4: Watch ad-free, get notifications
```

Short, 4-step visual with icons.

### 3.7 Download CTA Section

Repeat the download CTA near the bottom with a stronger prompt:

> **Ready to never miss a stream?**
> Download TukiWatch for Android — free, open-source, no account required.

Buttons: "Download APK" (primary) · "View on GitHub" (secondary)

### 3.8 Footer

- Logo + tagline
- Links: Features · Supporter · Privacy · Terms · Contact
- Social: GitHub, Discord (placeholder)
- Email: tukiwatch@snowballons.com
- Copyright

---

## 4. Supporter Page (`supporter.html`) — New

### 4.1 Purpose

The supporter page converts visitors into paying supporters via Lemon Squeezy. It sits alongside the landing page and is linked from the main nav.

### 4.2 Page Layout

```
┌──────────────────────────────────────────────────────┐
│  [Logo]                    [Back to Site]            │
│                                                      │
│  Be a TukiWatch Supporter                            │
│                                                      │
│  TukiWatch is free and always will be.               │
│  Supporters help keep the lights on and get          │
│  exclusive perks that make the experience better     │
│  for everyone.                                       │
│                                                      │
│  ┌─────────────────────────────────────────────┐    │
│  │              SUPPORTER                      │    │
│  │                                             │    │
│  │              $5/month                       │    │
│  │                                             │    │
│  │  • Higher rate limits (1000 req/min         │    │
│  │    vs 100 req/min)                          │    │
│  │  • Health endpoint exclusions               │    │
│  │  • Ad-free Twitch when you add              │    │
│  │    your own Turbo token                     │    │
│  │  • Your name in the app credits             │    │
│  │  • Early access to new features             │    │
│  │                                             │    │
│  │  [Become a Supporter →]                     │    │
│  │       (Lemon Squeezy checkout)              │    │
│  └─────────────────────────────────────────────┘    │
│                                                      │
│  FAQ:                                               │
│  Q: Can I cancel anytime?                           │
│  A: Yes. Cancel from your Lemon Squeezy             │
│     dashboard. Your support ends at period end.     │
│                                                      │
│  Q: Is there a one-time option?                     │
│  A: Not currently. Future plans may include         │
│     a lifetime tier.                                │
│                                                      │
│  Q: What happens to my data?                        │
│  A: Nothing changes. Your favorites, sessions,      │
│     and usage stay local. We don't track you.       │
│                                                      │
│  ─────────────────────────────────────────          │
│  Or sponsor us once:                                │
│  [GitHub Sponsors]  [Ko-fi]                         │
└──────────────────────────────────────────────────────┘
```

### 4.3 Lemon Squeezy Integration

- **"Become a Supporter"** button links directly to the Lemon Squeezy checkout:
  ```
  https://your-store.lemonsqueezy.com/buy/[product-id]
  ```
- On successful purchase, Lemon Squeezy sends a license key via email (their native flow)
- User enters the license key in the app → API validates → issues session token
- **No custom payment flow** — Lemon Squeezy owns checkout, licensing, and subscriptions

### 4.4 Pricing Tier (single tier for MVP)

| | Free | Supporter ($5/mo) |
|---|---|---|
| Rate limit | 100 req/min | 1,000 req/min |
| Health endpoint | Limited | Exempt |
| Ad-free Twitch | Requires self-host + Turbo token | Same (but higher limits help) |
| Priority support | — | Via Discord |
| Name in app | — | Future feature |

### 4.5 FAQ Section

Include these 3–4 questions:
1. Can I cancel anytime?
2. What happens if I miss a payment?
3. How do I activate my supporter status in the app?
4. Is there a refund policy?

---

## 5. CSS Changes (`style.css`)

### 5.1 New Design Tokens

```css
:root {
  --primary-bg: #0a0a0a;          /* darker than current #1a1a1a */
  --surface: #141414;
  --surface-2: #1e1e1e;
  --border: #2a2a2a;
  --text-primary: #ffffff;
  --text-secondary: #a0a0a0;
  --accent-green: #00ff7f;
  --accent-green-dim: rgba(0, 255, 127, 0.1);
  --accent-blue: #3b82f6;
}
```

### 5.2 New Components

```css
/* Phone mockup container */
.phone-mockup {
  max-width: 320px;
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 0 60px rgba(0, 255, 127, 0.15);
}

/* Platform chips */
.platform-chips {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
  margin: 30px 0;
}
.platform-chip {
  padding: 8px 16px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 20px;
  font-size: 0.9em;
  color: var(--text-secondary);
}

/* Pricing card */
.pricing-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 40px;
  max-width: 400px;
  margin: 0 auto;
  text-align: center;
}
.pricing-card.featured {
  border-color: var(--accent-green);
  box-shadow: 0 0 40px var(--accent-green-dim);
}
.price {
  font-size: 3em;
  font-weight: 700;
  color: var(--accent-green);
}
.price span {
  font-size: 0.4em;
  color: var(--text-secondary);
}

/* Feature card with screenshot */
.feature-split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 40px;
  align-items: center;
}
.feature-split.reverse .feature-text { order: 2; }
.feature-split.reverse .feature-img { order: 1; }

/* Steps */
.steps {
  display: flex;
  gap: 20px;
  justify-content: center;
  flex-wrap: wrap;
}
.step {
  text-align: center;
  max-width: 200px;
}
.step-number {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--accent-green);
  color: var(--primary-bg);
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}
```

### 5.3 Responsive Notes

- Hero: stack phone mockup below text on mobile
- Feature grid: single column on mobile, 2-col on tablet+, 3-col on desktop
- Pricing card: full-width on mobile
- All screenshots: max-width 280px, centered

---

## 6. JavaScript Changes (`script.js`)

### 6.1 Keep Existing

- Smooth scroll (unchanged)
- Fade-in observer (unchanged)
- APK version fetch (unchanged)

### 6.2 New: Platform Chips Animation

Animate platform chips fading in staggered on scroll.

### 6.3 New: Supporter Page Logic (if needed)

Minimal — the "Become a Supporter" button is a direct link to Lemon Squeezy checkout. No client-side API calls.

---

## 7. Asset Mapping

Copy/symlink assets from `app/assets/` into `app/web/assets/`:

```bash
mkdir -p app/web/assets
cp app/assets/favicon.png   app/web/assets/favicon.png
cp app/assets/live-screen.png  app/web/assets/hero-phone.png
cp app/assets/empty-live-screen.png  app/web/assets/empty-screen.png
cp app/assets/add-stream.png  app/web/assets/add-stream.png
cp app/assets/intro1.png  app/web/assets/intro1.png
cp app/assets/intro2.png  app/web/assets/intro2.png
```

Use `hero-phone.png` as the hero image (highest quality screenshot).
Use `empty-screen.png` in the features section.
Use `intro1.png` and `intro2.png` in the screenshots carousel.

---

## 8. Implementation Phases

### Phase A: Skeleton (no visual changes, structural)
1. Create `app/web/assets/` directory and copy assets
2. Create `app/web/supporter.html` shell (empty for now)
3. Add nav link to supporter in `index.html`
4. Update favicon to use local asset

### Phase B: Landing Page Redesign
5. Rewrite `style.css` with new tokens and components
6. Rewrite `index.html` hero, platforms strip, features, screenshots, how-it-works, CTA
7. Verify responsive on mobile (375px), tablet (768px), desktop (1200px+)

### Phase C: Supporter Page
8. Build `supporter.html` with pricing card, FAQ, CTA to Lemon Squeezy
9. Add to nav and footer

### Phase D: Polish
10. Test all links, images load correctly
11. Run Lighthouse audit (performance, accessibility)
12. Verify Google Search Console structured data is correct
13. Commit and push

---

## 9. Files to Create / Modify

| Action | File |
|---|---|
| Create | `app/web/assets/` (directory + copied images) |
| Create | `app/web/supporter.html` |
| Modify | `app/web/index.html` (full rewrite) |
| Modify | `app/web/style.css` (redesign) |
| Modify | `app/web/script.js` (minor additions) |
| Modify | `app/web/privacy.html` (update nav) |
| Modify | `app/web/terms.html` (update nav) |

---

## 10. Post-Launch

Once the site is live:
- Update `og:image` in `index.html` to point to a new preview screenshot
- Submit sitemap to Google Search Console
- Consider adding Open Graph meta to `supporter.html`
- Link supporter page from the app's "About" screen (future)
