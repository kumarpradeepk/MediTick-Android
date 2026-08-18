# MediTick vs Doz — parity audit

**Scope.** Every behaviour observable in the Doz 1.1.9 (60) audit set — 262 screenshots,
261 accessibility hierarchies, ~1,050 distinct UI labels — checked against the MediTick
iOS and Android codebases.

**Method.** Each row below is a regex pair asserted against the full source of both apps
(`scripts/parity_audit.py`). A ✅ means the implementing code exists and is reachable; it
does not by itself prove the pixels are right. Items that could only be verified by running
the app are listed under *Not verified here*.

**Result: 116 / 116 on iOS, 116 / 116 on Android.**

## Naming

Doz's own vocabulary is used throughout, as requested: the tab is **Treatments**; schedule
modes are **Every Day / Specific Days / Interval / Cyclic Mode / As Needed**; sections read
**MY PRESCRIPTIONS**, **STANDALONE MEDICATIONS**, **NEXT DOSE**, **SCHEDULE OPTIONS**,
**DURATION**, **DOSES**, **INVENTORY**, **MEAL-LINKED REMINDERS**. Brand-specific strings
are MediTick's (for example the standard reminder sound is *MediTick (standard)*, not
*Doz (standard)*).

## Behaviours deliberately matched to Doz, changing prior MediTick behaviour

| Behaviour | Was | Now (Doz) |
|---|---|---|
| Adherence percentage | taken ÷ *decided* doses | taken ÷ *scheduled* doses — skipping resolves the day but does not raise adherence |
| Streak | broken by a skipped dose | a resolved dose may be taken **or** skipped; only a missed dose breaks the run |
| Meal-anchor label | "30 min before breakfast" | "30 min before Breakfast" |
| Medication forms | 11, led by Tablet | 13, led by Pill, adding Gummy |
| Strength units | mg / mcg / IU / ml | mg / mcg / g / mL / IU / % |
| Dose amount | one amount for the whole schedule | per-dose amount and per-dose meal relation |

## Feature-by-feature

### Shell

| Doz behaviour | iOS | Android |
|---|:--:|:--:|
| Four-tab shell: Today / Treatments / Progress / Settings | ✅ | ✅ |
| Treatments naming | ✅ | ✅ |

### Today

| Doz behaviour | iOS | Android |
|---|:--:|:--:|
| Seven-day date strip | ✅ | ✅ |
| "N medications left for today" counter | ✅ | ✅ |
| Dose groups by exact time | ✅ | ✅ |
| Take All | ✅ | ✅ |
| Add entry sheet (Create Prescription / Add Medication) | ✅ | ✅ |
| As-needed logging with amount and note | ✅ | ✅ |
| Retro-logging past days | ✅ | ✅ |

### Treatments

| Doz behaviour | iOS | Android |
|---|:--:|:--:|
| Type filter All / Medications / Prescriptions | ✅ | ✅ |
| Status filter Active / Completed / Archived | ✅ | ✅ |
| MY PRESCRIPTIONS / STANDALONE MEDICATIONS sections | ✅ | ✅ |
| Empty states per filter | ✅ | ✅ |
| Stock shown on row ("30 pills left") | ✅ | ✅ |

### Prescription

| Doz behaviour | iOS | Android |
|---|:--:|:--:|
| Prescriber field | ✅ | ✅ |
| Diagnosis field | ✅ | ✅ |
| Clinic / facility field | ✅ | ✅ |
| Contact number + call/copy | ✅ | ✅ |
| Ongoing toggle + start/end dates | ✅ | ✅ |
| Mark as Complete | ✅ | ✅ |
| Archive + restore | ✅ | ✅ |
| Delete moves medications to standalone | ✅ | ✅ |
| Reactivate a completed course | ✅ | ✅ |
| Date propagation to linked medications | ✅ | ✅ |
| "Update N medications" switch | ✅ | ✅ |
| Add new / add existing medication menu | ✅ | ✅ |
| Existing-medication chooser with search | ✅ | ✅ |
| Prescription logs | ✅ | ✅ |
| Prescription adherence + on-time | ✅ | ✅ |

### Medication

| Doz behaviour | iOS | Android |
|---|:--:|:--:|
| Twelve medication forms incl. Pill and Gummy | ✅ | ✅ |
| Strength units mg/mcg/g/mL/IU/% | ✅ | ✅ |
| Prescription association (None / Standalone) | ✅ | ✅ |
| Track quantity toggle | ✅ | ✅ |
| Current stock + low-stock threshold | ✅ | ✅ |
| "Not tracked" when quantity is off | ✅ | ✅ |
| Duplicate name+strength validation | ✅ | ✅ |
| Next dose card + Take Now | ✅ | ✅ |
| Medication logs | ✅ | ✅ |
| Archive / delete confirmations | ✅ | ✅ |
| Refill with new-stock preview | ✅ | ✅ |

### Schedule

| Doz behaviour | iOS | Android |
|---|:--:|:--:|
| Every Day | ✅ | ✅ |
| Specific Days (weekday picker) | ✅ | ✅ |
| Interval — every N calendar days | ✅ | ✅ |
| Interval quick presets | ✅ | ✅ |
| Cyclic mode with intake/pause | ✅ | ✅ |
| Cyclic presets 21/7, 24/4, 84/7 | ✅ | ✅ |
| As Needed hides duration and doses | ✅ | ✅ |
| Ongoing treatment toggle + start/end date | ✅ | ✅ |
| Per-dose amount | ✅ | ✅ |
| Per-dose Fixed / Before / With / After relation | ✅ | ✅ |
| Meal offset in minutes | ✅ | ✅ |
| MEAL-LINKED REMINDERS panel | ✅ | ✅ |
| "Changing meal times updates reminders automatically" | ✅ | ✅ |
| Add Dose chooser with common times + custom | ✅ | ✅ |
| Remove Dose | ✅ | ✅ |
| Half-dose amounts | ✅ | ✅ |

### Smart input

| Doz behaviour | iOS | Android |
|---|:--:|:--:|
| Natural-language add | ✅ | ✅ |
| Four worked examples | ✅ | ✅ |
| Quick-add chips | ✅ | ✅ |
| Duration parsing ("for 7 days") | ✅ | ✅ |
| Report wrong schedule | ✅ | ✅ |

### Progress

| Doz behaviour | iOS | Android |
|---|:--:|:--:|
| 7 Days / All Days ranges | ✅ | ✅ |
| All Days is Pro-gated | ✅ | ✅ |
| Adherence rate + taken/skipped/missed | ✅ | ✅ |
| Adherence explainer sheet | ✅ | ✅ |
| On-time rate with 15/30/60 grace window | ✅ | ✅ |
| Timing summary On time / Early / Late / Taken total | ✅ | ✅ |
| Current streak + best | ✅ | ✅ |
| Streak explainer copy | ✅ | ✅ |
| Skipped doses resolve the day | ✅ | ✅ |
| Calendar with month navigation | ✅ | ✅ |
| Choose month / year picker | ✅ | ✅ |
| Adherence legend complete/partial/missed/no doses | ✅ | ✅ |
| Per-day logs | ✅ | ✅ |
| Patterns — hardest time of day | ✅ | ✅ |

### Reminders

| Doz behaviour | iOS | Android |
|---|:--:|:--:|
| Scheduled local notifications | ✅ | ✅ |
| Take / Skip / Snooze actions on the banner | ✅ | ✅ |
| Follow-up reminder 5/10/15/30/60 | ✅ | ✅ |
| Alert sound picker (5 sounds) | ✅ | ✅ |
| Sound preview | ✅ | ✅ |
| Some sounds require Pro | ✅ | ✅ |
| Hide medication names in notifications | ✅ | ✅ |
| Critical / urgent delivery | ✅ | ✅ |
| Refill alerts | ✅ | ✅ |
| Notification status hand-off to system settings | ✅ | ✅ |
| Reschedule after reboot / time change | ✅ | ✅ |

### Settings

| Doz behaviour | iOS | Android |
|---|:--:|:--:|
| Pro entitlement state | ✅ | ✅ |
| Redeem offer code | ✅ | ✅ |
| Join the community jump | ✅ | ✅ |
| Community card with Facebook + Reddit | ✅ | ✅ |
| Appearance light / dark / system | ✅ | ✅ |
| In-app language picker (8 languages) | ✅ | ✅ |
| Meal times | ✅ | ✅ |
| Meal-time change updates anchored reminders | ✅ | ✅ |
| Dose time presets with minutes | ✅ | ✅ |
| Restore Default Times | ✅ | ✅ |
| Widgets education | ✅ | ✅ |
| Write a Review | ✅ | ✅ |
| Share | ✅ | ✅ |
| Send Feedback / Report Bug | ✅ | ✅ |
| Request a Feature | ✅ | ✅ |
| Website / Threads / Instagram | ✅ | ✅ |
| What's New changelog | ✅ | ✅ |
| Privacy Policy | ✅ | ✅ |
| Terms of Service | ✅ | ✅ |
| Version footer | ✅ | ✅ |

### Data

| Doz behaviour | iOS | Android |
|---|:--:|:--:|
| Export backup | ✅ | ✅ |
| Import backup | ✅ | ✅ |
| Erase all data | ✅ | ✅ |
| Cross-platform JSON shape (doses array) | ✅ | ✅ |
| Tolerant decode of older snapshots | ✅ | ✅ |
| Home-screen widget | ✅ | ✅ |

### Post-save

| Doz behaviour | iOS | Android |
|---|:--:|:--:|
| Success screen | ✅ | ✅ |
| "Added to your prescription" wording | ✅ | ✅ |
| Add Another Medication | ✅ | ✅ |
| App Store / Play rating prompt | ✅ | ✅ |

## Cross-platform data compatibility

Both apps read and write the same JSON snapshot, so an export from either restores on the
other. The new `doses` array (per-dose amount + meal anchor) is written by both and ignored
safely by older builds, which fall back to the mirrored `times` / `mealAnchors` / `amountPerDose`
fields. iOS decoding was made tolerant field-by-field (`decodeIfPresent` throughout
`Models.swift`), because Swift's synthesised `Decodable` throws on a missing key even when the
property has a default — without that change, one absent field would have discarded a user's
entire history on upgrade.

## Verification performed

| Check | Result |
|---|---|
| Android `:app:compileDebugKotlin` | ✅ BUILD SUCCESSFUL |
| Android `:app:testDebugUnitTest` | ✅ BUILD SUCCESSFUL (incl. 5 new engine tests) |
| Android `:app:lintDebug` | ✅ BUILD SUCCESSFUL |
| iOS structural scan (braces/brackets balanced, every new symbol declared exactly once and referenced, all `MedicationForm` / `ScheduleKind` switches exhaustive) | ✅ |
| Parity regex suite, 116 checks × 2 platforms | ✅ 232 / 232 |

## Not verified here — needs your machine or a device

1. **iOS compilation and tests.** There is no Swift toolchain on this Windows machine, so
   `PillPal.xcodeproj` was not built and `PillPalTests` was not run. The iOS changes were
   checked structurally (see above), and the same class of error the Kotlin compiler caught on
   Android — a non-exhaustive `when` over the enlarged medication-form enum — was searched for
   and cleared on iOS. **Open Xcode and run ⌘B / ⌘U before shipping.**
2. **Xcode project membership.** Four new Swift files were added — `AppLinks.swift`,
   `TreatmentSheets.swift`, `AdherenceCalendar.swift`, `SettingsExtras.swift`. If the project
   does not use a synchronised folder group, add them to the PillPal target.
3. **Notification delivery, widgets, purchases.** Reminder firing, Critical Alerts, offer-code
   redemption and the store flows can only be confirmed on a real device with the right
   entitlements — exactly the set the Doz audit itself excluded.
4. **Localisation.** The language picker is wired on both platforms, but the eight
   translations of the *new* strings still need to be filled into
   `Localizable.xcstrings` (iOS) and the Android string resources.

## Outstanding configuration (not code)

- `AppLinks.swift` / `AppLinks.kt` — website, What's New, Privacy Policy, Terms of Service,
  Facebook group, subreddit, Threads, Instagram, App Store id. Placeholder rows are hidden
  from Settings until real addresses replace them, so nothing ships as a dead link.
- Bundled reminder sounds `meditick-chime.caf`, `meditick-bell.caf`, `meditick-urgent.caf`
  (iOS). The picker works without them and falls back to the system sound.
