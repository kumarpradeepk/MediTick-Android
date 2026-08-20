# MediTick for Android

Native Android counterpart to the MediTick iOS app. The app is built with Jetpack Compose and uses the same private, offline-first model as iOS: no account, no health-data server, and one portable JSON snapshot.

## Before release: fill in `AppLinks.kt`

Every outbound destination lives in `app/src/main/java/com/kabi/pillpal/meditick/AppLinks.kt` —
website, What's New, Privacy Policy, Terms of Service, the Facebook group, the
subreddit, Threads and Instagram. They ship as clearly-marked placeholders, and
any row still pointing at one is **hidden from Settings** rather than shown as a
dead link. Replace them and the rows appear.

## Included

- Treatments with type (All / Medications / Prescriptions) and status (Active / Completed / Archived) filters
- Prescriptions with prescriber, clinic, contact, diagnosis and treatment period, plus mark-complete / archive / restore / reactivate
- Per-dose amounts and per-dose meal relations (fixed, before, with, after) with a meal-linked reminders panel
- Reminder sound picker with preview, private notifications, and an in-app language picker
- Today timeline with Morning, Midday, Evening and Bedtime groups, and Take All per time group
- Live upcoming, due, missed, taken and skipped states
- Take, skip, snooze and undo actions in-app and from notifications
- Retro-logging, as-needed doses, adherence, on-time rate and streaks
- Fixed, meal-relative, weekday, interval, cyclic and as-needed schedule engine
- Natural-language medication parsing and bundled offline drug catalog
- Medication inventory, refill forecasting and refill reminders
- Prescription plans with linked or standalone medications
- Daylight/Midnight appearances and Aurora/Ocean/Orchid/Ember accents
- JSON export/import compatible with the iOS model
- Restart, time-zone and device-boot reminder rescheduling
- Resizable Today home-screen widget
- Google Play Billing support and the same one-medication free tier

## Build

Open this directory in Android Studio or run:

```bash
./gradlew :app:assembleDebug
```

The verified debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Google Play products

Create these products in Play Console for the Android package `com.kabi.pillpal.meditick`:

| Product ID | Type |
| --- | --- |
| `monthly` | Auto-renewing subscription |
| `yearly` | Auto-renewing subscription |
| `lifetime` | One-time product |

Attach active base plans/offers to the subscriptions. The paywall reads localized prices directly from Google Play, acknowledges completed purchases, restores active subscriptions and lifetime ownership, and persists a last-known entitlement for offline gating.

Billing product details are unavailable in a sideload-only emulator. Test purchases with a Play Console internal-testing build and a licensed tester account.

## Reminder behavior

MediTick requests notification permission during onboarding. On Android 12+, it uses exact alarms when the user/device allows them and safely falls back to idle-aware inexact alarms otherwise. Notification actions work without opening the app, and reminders are rebuilt when medication data, time, time zone, app version, or boot state changes.

## Verification

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The scheduling tests cover fixed times, meal movement, cycles, weekday filters, state resolution and logged-dose precedence.
