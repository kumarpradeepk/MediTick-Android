# MediTick Android — Play Console release checklist

Everything on this list has to happen inside your Google account (Play Console
sign-in, payment, app creation) — none of it is something I can or should do
for you. This is the exact sequence for *this* app, not a generic template.

## What's already done on the code side

- **Release signing key generated**: `keystore/meditick-release.jks`
  (alias `meditick`, RSA 2048, valid until 2054). Password is in
  `keystore.properties` at the repo root. **Neither file is committed** —
  both are gitignored. Back them up now, somewhere outside git (password
  manager + a second offline copy). If this key is lost after your first
  Play Store upload, you cannot ship updates to the same listing without
  going through Google's account-recovery process.
- `app/build.gradle.kts` reads the keystore automatically when
  `keystore.properties` is present, and falls back to unsigned so debug
  builds and CI without the secret still work.
- R8 shrinking/optimization is on for release (`android.r8.gradual.support=true`
  in `gradle.properties`, required by this project's AGP 9.2.1).
- A signed bundle already builds successfully:
  `app/build/outputs/bundle/release/app-release.aab` (4.2 MB). Fingerprint
  of the signing cert, for reference:
  - SHA-1: `71:B4:B3:0E:A2:5A:97:FC:4F:F5:93:12:C3:47:A0:40:7E:16:A5:72`
  - SHA-256: `2D:C0:A0:0B:62:D4:F5:5F:5B:FC:39:AD:43:21:4C:49:B4:7E:18:69:C0:42:E7:71:97:E6:9E:71:6E:76:5B:93`

To rebuild the bundle yourself later:
```bash
./gradlew :app:bundleRelease
```
Output lands at `app/build/outputs/bundle/release/app-release.aab`.

## 1. Play Console account and app shell

1. Sign in to [play.google.com/console](https://play.google.com/console) with
   the Google account you want to publish under. One-time $25 registration
   fee if you don't already have a developer account.
2. **Create app** → name "MediTick", default language, app type "App",
   Free/Paid = **Free** (billing is via in-app subscriptions, not a paid
   listing), accept the declarations.
3. Package name will be asked on first upload — it's fixed as
   `com.kabi.pillpal.meditick` (from `applicationId` in `app/build.gradle.kts`).
   You cannot change this later, so confirm it matches before uploading.

## 2. App content / policy forms (Play Console → "Policy" tab)

Answer these based on what the app actually does — don't default to the
generic "yes to everything" answers:

- **Privacy policy URL** — required before you can publish. `AppLinks.kt`
  currently points at a placeholder (`https://meditick.app/privacy`); publish
  a real page first and update `PRIVACY_POLICY` in
  `app/src/main/java/com/kabi/pillpal/meditick/AppLinks.kt`, or the in-app
  Settings row for it stays hidden and reviewers may flag the mismatch.
- **App access** — all functionality is available without login (no account
  system exists in this app).
- **Ads** — declare "No ads" (none are integrated).
- **Content rating questionnaire** — category "Health & Fitness" / "Medical",
  no violence/user-generated content/gambling. Should land at PEGI 3 / Everyone.
- **Target audience** — this is a medication reminder for adults; do **not**
  select an audience that includes children, since that triggers Families
  Policy requirements this app isn't built for.
- **Data safety form** — reflect the actual code, from `MedStore`/local
  storage:
  - Data collected: **none** sent off-device. All medication data,
    schedules and logs stay in local SharedPreferences/JSON on the device.
  - Data shared with third parties: **none**.
  - Google Play Billing is used for purchases — standard billing data
    handled by Google, not custom analytics.
  - No account creation, no PII collected.
- **Permissions declared** in the manifest that Play may ask you to justify:
  - `POST_NOTIFICATIONS` — dose reminders.
  - `SCHEDULE_EXACT_ALARM` — exact-time dose reminders. Play requires a
    declaration under **Policy → Permissions → Alarms & reminders** stating
    why (medication timing accuracy).
  - `RECEIVE_BOOT_COMPLETED` — reschedules reminders after device reboot.
  - `VIBRATE` — reminder haptics.

## 3. Store listing (Play Console → "Store presence" → "Main store listing")

- **App name**: MediTick
- **Short description** (80 chars) and **full description** (4000 chars) —
  draw from `README.md`'s feature list (Treatments, per-dose schedules,
  meal-anchored reminders, adherence tracking, etc.) rather than writing
  from scratch.
- **App icon** (512×512 PNG) — export at high-res from
  `app/src/main/res/drawable-nodpi/meditick_icon.png` if that source is
  large enough; regenerate at 512×512 if not.
- **Feature graphic** (1024×500 PNG/JPG) — not yet created. Needed before
  you can publish.
- **Phone screenshots** (min 2, 16:9 or 9:16) — not yet created. Capture
  from a running build once you can install it (see §5), or ask me to help
  design them once the app is running in an emulator/device you can screen-
  shot from.
- **Category**: Medical or Health & Fitness.
- **Contact details**: email `tinkersmithstudio@gmail.com` is already used
  in-app for feedback (see `FeedbackMail`-equivalent flows); reuse it or
  set a support email you'll actually monitor.

## 4. In-app products (Play Console → "Monetize" → "Products" → "In-app products" / "Subscriptions")

`BillingManager.kt` already queries these exact product IDs — create them
with **matching IDs** or purchases will silently fail to resolve:

| Product ID | Type | Notes |
|---|---|---|
| `monthly` | Subscription | Base plan, auto-renewing |
| `yearly` | Subscription | Base plan, auto-renewing |
| `lifetime` | One-time (managed in-app product) | Non-consumable |

Set prices, activate each, and attach at least one base plan/offer to each
subscription (Play won't let a subscription go live without one).

## 5. Testing before production

1. **Internal testing track** (Play Console → "Testing" → "Internal testing")
   — create a release, upload `app-release.aab`, add your own email as a
   tester, roll out. This is the fastest way to get a real signed build onto
   a device without waiting for review.
2. Install via the opt-in link Play sends your tester email, exercise the
   app for real — this is also your chance to capture the screenshots from
   §3.
3. Only after internal testing looks right, promote to **Closed testing**
   (optional) or straight to **Production** and submit for review.

## 6. What I cannot do for you

- Sign in to your Google/Play Console account.
- Accept Play's Developer Distribution Agreement or pay the registration fee.
- Fill in and submit the policy/content forms (they're legal declarations
  tied to your account).
- Upload the AAB to Play Console (that's a browser action inside your
  authenticated session).

If you'd like, once you've done §1–2 and have the app shell created, tell me
and I can help draft the exact store-listing copy, generate the feature
graphic, or walk through any Gradle/signing issue that comes up.
