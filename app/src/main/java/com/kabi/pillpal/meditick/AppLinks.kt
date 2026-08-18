package com.kabi.pillpal.meditick

/**
 * Every outbound destination the app can open, in one place. Replace the
 * placeholder hosts below with the real MediTick addresses before shipping —
 * nothing else in the app hard-codes a URL.
 *
 * Rows whose URL is still a placeholder are hidden from Settings rather than
 * shown as dead links, so an unfinished address can never ship as a broken tap.
 */
object AppLinks {

    // TODO: point at the production MediTick domain.
    const val WEBSITE = "https://meditick.app"

    // TODO: publish the changelog and policy pages, then update these paths.
    const val WHATS_NEW = "https://meditick.app/whats-new"
    const val PRIVACY_POLICY = "https://meditick.app/privacy"
    const val TERMS_OF_SERVICE = "https://meditick.app/terms"

    // TODO: create the group/subreddit and update these.
    const val FACEBOOK_GROUP = "https://facebook.com/groups/meditick"
    const val REDDIT = "https://reddit.com/r/meditick"

    // TODO: claim these handles and update.
    const val THREADS = "https://threads.net/@meditick"
    const val INSTAGRAM = "https://instagram.com/meditick"

    const val SUPPORT_EMAIL = "tinkersmithstudio@gmail.com"

    /** Play Store listing, used by Share and Write a Review. */
    const val PLAY_STORE = "https://play.google.com/store/apps/details?id=com.kabi.pillpal.meditick"

    const val TAGLINE = "MediTick - Pill Timer & Reminder"

    private val PLACEHOLDER_MARKERS = listOf("meditick.app", "/groups/meditick", "/r/meditick", "@meditick")

    /** Whether a destination is real enough to show the user. */
    fun isConfigured(url: String): Boolean = PLACEHOLDER_MARKERS.none { url.contains(it) }
}

/**
 * The in-app changelog. Newest first; each entry is what actually shipped, so
 * "What's new" never needs a network call to say something true.
 */
object ReleaseNotes {

    data class Entry(val version: String, val date: String, val lead: String, val bullets: List<String>)

    val entries = listOf(
        Entry(
            version = "Latest",
            date = "",
            lead = "This update brings MediTick to full feature parity across iPhone and Android:",
            bullets = listOf(
                "Treatments now filters by type and by Active, Completed or Archived.",
                "Prescriptions keep prescriber, clinic, contact, diagnosis and a treatment period, and can be marked complete, archived or restored.",
                "Each dose can have its own amount and its own meal relation, so one medication can be 08:00 fixed and 30 minutes before dinner.",
                "New schedule shapes: every N days and intake/pause cycles, alongside specific days and as-needed.",
                "Take All resolves a whole time group in one tap.",
                "Progress adds an all-days calendar with month navigation, plus adherence, on-time and streak explainers.",
                "Reminder sounds, private notifications that keep drug names off the lock screen, and an in-app language picker.",
                "Refills preview the resulting stock before you commit, and logged times can be corrected after the fact.",
            ),
        ),
    )
}
