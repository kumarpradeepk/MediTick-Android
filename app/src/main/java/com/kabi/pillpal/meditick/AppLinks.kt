package com.kabi.pillpal.meditick

import androidx.annotation.StringRes

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

    data class Entry(
        @StringRes val version: Int,
        val date: String,
        @StringRes val lead: Int,
        val bullets: List<Int>,
    )

    val entries = listOf(
        Entry(
            version = R.string.release_latest,
            date = "",
            lead = R.string.release_lead,
            bullets = listOf(
                R.string.release_bullet_filters,
                R.string.release_bullet_prescriptions,
                R.string.release_bullet_per_dose,
                R.string.release_bullet_shapes,
                R.string.release_bullet_take_all,
                R.string.release_bullet_progress,
                R.string.release_bullet_sounds,
                R.string.release_bullet_refills,
            ),
        ),
    )
}
