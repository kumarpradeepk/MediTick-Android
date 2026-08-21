# MediTick for Android

Native Android counterpart to the MediTick iOS app. The app is built with Jetpack Compose and uses the same private, offline-first model as iOS: no login, no health-data database, and one portable JSON snapshot. The optional Pro label scanner sends only the current label frame for transient recognition.

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

## AI medication scan

Free users receive 3 private on-device ML Kit OCR/catalog scans. Pro uses a downsampled label photo with the MediTick Claude proxy and is capped server-side at 30 AI attempts per UTC month. Results still require confirmation and only prefill label facts; the app never invents dosing instructions.

Set these in the gitignored `local.properties` for local builds, or provide the matching environment variables in CI:

```properties
ai.scan.endpoint=https://YOUR-WORKER/v1/medication/scan
ai.scan.clientToken=YOUR_CLIENT_TOKEN
```

The Anthropic API key must exist only in the worker secret store. Without these settings, Pro falls back to on-device recognition. Android currently has no login system, so Pro quota is tied to a one-way hash of the restored Google Play purchase identity (with an install-scoped fallback before billing refresh); see `../services/ai-scan-worker/README.md` for deployment and production auth/Play Integrity hardening.

## Reminder behavior

MediTick requests notification permission during onboarding. On Android 12+, it uses exact alarms when the user/device allows them and safely falls back to idle-aware inexact alarms otherwise. Notification actions work without opening the app, and reminders are rebuilt when medication data, time, time zone, app version, or boot state changes.

## Verification

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The scheduling tests cover fixed times, meal movement, cycles, weekday filters, state resolution and logged-dose precedence.
