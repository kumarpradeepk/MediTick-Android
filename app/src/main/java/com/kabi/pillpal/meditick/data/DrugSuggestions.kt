package com.kabi.pillpal.meditick.data

import com.kabi.pillpal.meditick.model.MedicationForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * One tappable row in the describe-step search: a name the user recognizes
 * plus one concrete strength, ready to prefill the form.
 */
data class DrugSuggestion(
    val name: String,
    val strengthText: String?,
    val form: MedicationForm?,
    /** Extra context — the generic behind a brand alias, or the route. */
    val detail: String? = null,
    val online: Boolean = false,
)

/**
 * Search-as-you-type over medication names, the way the reference app does
 * it: the bundled catalog answers instantly and offline (brand aliases
 * included, so "Paracetamol" finds Acetaminophen), and the free NIH RxTerms
 * service broadens the list with US prescribables and their sold strengths
 * when the network allows. RxTerms is keyless and answers in one request:
 * https://clinicaltables.nlm.nih.gov/api/rxterms/v3/search
 */
object DrugSuggestions {

    /** Instant, offline: catalog entries expanded into per-strength rows. */
    fun local(catalog: MedicationCatalog, query: String, limit: Int = 6): List<DrugSuggestion> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val rows = mutableListOf<DrugSuggestion>()
        for (entry in catalog.search(q, limit = 3)) {
            // Show the name the user is actually typing — the brand alias
            // when that's what matched — with the generic as context.
            val alias = entry.aliases.firstOrNull { it.startsWith(q, ignoreCase = true) }
            val label = if (entry.name.startsWith(q, ignoreCase = true)) entry.name else alias ?: entry.name
            val detail = if (label != entry.name) entry.name else null
            val strengths = entry.strengths.ifEmpty { listOf(null) }
            for (strength in strengths.take(3)) {
                rows.add(DrugSuggestion(label, strength, entry.form, detail))
                if (rows.size == limit) return rows
            }
        }
        return rows
    }

    // The bundled name index: ~19k prescribable names filtered out of the
    // RxNorm display-terms list — offline breadth behind the curated catalog.
    @Volatile private var nameIndex: List<String>? = null

    private fun index(context: android.content.Context): List<String> =
        nameIndex ?: synchronized(this) {
            nameIndex ?: runCatching {
                val raw = context.assets.open("drug_names.json").bufferedReader().use { it.readText() }
                val array = JSONArray(raw)
                (0 until array.length()).map(array::getString)
            }.getOrDefault(emptyList()).also { nameIndex = it }
        }

    /**
     * Offline breadth tier: prefix matches from the bundled name index,
     * name-only (the catalog supplies strengths for the drugs it curates).
     */
    fun fromIndex(context: android.content.Context, query: String, exclude: Set<String>, limit: Int = 3): List<DrugSuggestion> {
        val q = query.trim()
        if (q.length < 3) return emptyList()
        val rows = mutableListOf<DrugSuggestion>()
        for (name in index(context)) {
            if (!name.startsWith(q, ignoreCase = true) || name.lowercase() in exclude) continue
            rows.add(DrugSuggestion(name = name, strengthText = null, form = null))
            if (rows.size == limit) break
        }
        return rows
    }

    /**
     * RxTerms autocomplete. Fails soft — any network or parsing problem
     * returns an empty list, and the local rows stand alone.
     */
    suspend fun online(query: String, limit: Int = 4): List<DrugSuggestion> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 3) return@withContext emptyList()
        runCatching {
            val url = URL(
                "https://clinicaltables.nlm.nih.gov/api/rxterms/v3/search?terms=" +
                    URLEncoder.encode(q, "UTF-8") + "&ef=STRENGTHS_AND_FORMS&maxList=$limit",
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (connection.responseCode != 200) return@runCatching emptyList()

            // Response shape: [count, [display names], {STRENGTHS_AND_FORMS: [[...]]}, ...]
            val root = JSONArray(body)
            val names = root.optJSONArray(1) ?: return@runCatching emptyList()
            val strengthLists = root.optJSONObject(2)?.optJSONArray("STRENGTHS_AND_FORMS")
            val rows = mutableListOf<DrugSuggestion>()
            for (index in 0 until names.length()) {
                val raw = names.optString(index)
                if (raw.isEmpty()) continue
                // "amLODIPine (Oral Pill)" → name + route.
                val route = Regex("""\(([^)]+)\)\s*$""").find(raw)?.groupValues?.get(1)
                val name = raw.replace(Regex("""\s*\([^)]+\)\s*$"""), "").trim()
                    .replaceFirstChar { it.uppercase() }
                val strengths = strengthLists?.optJSONArray(index)
                val first = strengths?.optString(0)?.trim()?.takeIf { it.isNotEmpty() }
                // "2.5 mg Tab" → strength text + a form hint.
                val strengthText = first?.let { Regex("""[0-9][0-9.,/\-]*\s*(?:mg|mcg|g|ml|iu|%)""", RegexOption.IGNORE_CASE).find(it)?.value }
                rows.add(DrugSuggestion(
                    name = name,
                    strengthText = strengthText,
                    form = formFor(route, first),
                    detail = route,
                    online = true,
                ))
                if (rows.size == limit) break
            }
            rows
        }.getOrDefault(emptyList())
    }

    /** Maps an RxTerms route / dose-form word to the app's form enum. */
    private fun formFor(route: String?, doseForm: String?): MedicationForm? {
        val text = ((route ?: "") + " " + (doseForm ?: "")).lowercase()
        return when {
            "cap" in text -> MedicationForm.capsule
            "pill" in text || "tab" in text -> MedicationForm.tablet
            "liquid" in text || "sol" in text || "syrup" in text -> MedicationForm.liquid
            "inhal" in text -> MedicationForm.inhaler
            "nasal" in text || "spray" in text -> MedicationForm.spray
            "ophthalmic" in text || "otic" in text || "drop" in text -> MedicationForm.drops
            "topical" in text || "cream" in text || "gel" in text || "ointment" in text -> MedicationForm.cream
            "inject" in text -> MedicationForm.injection
            "patch" in text || "transdermal" in text -> MedicationForm.patch
            "powder" in text -> MedicationForm.powder
            else -> null
        }
    }
}
