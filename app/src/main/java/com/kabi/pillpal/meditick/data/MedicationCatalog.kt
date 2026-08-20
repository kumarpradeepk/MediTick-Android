package com.kabi.pillpal.meditick.data

import android.content.Context
import com.kabi.pillpal.meditick.model.MedicationForm
import org.json.JSONArray

data class CatalogEntry(val name: String, val strengths: List<String>, val form: MedicationForm, val aliases: List<String>)

class MedicationCatalog private constructor(context: Context) {
    private val entries: List<CatalogEntry> = runCatching {
        val raw = context.assets.open("medications.json").bufferedReader().use { it.readText() }
        val array = JSONArray(raw)
        (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            CatalogEntry(
                name = json.getString("name"),
                strengths = json.getJSONArray("strengths").let { a -> (0 until a.length()).map(a::getString) },
                form = runCatching { MedicationForm.valueOf(json.optString("form")) }.getOrDefault(MedicationForm.tablet),
                aliases = json.getJSONArray("aliases").let { a -> (0 until a.length()).map(a::getString) },
            )
        }
    }.getOrDefault(emptyList())

    /** Every entry — the Scan-to-Add matcher scores the whole catalog. */
    fun all(): List<CatalogEntry> = entries

    fun search(query: String, limit: Int = 6): List<CatalogEntry> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return entries.mapNotNull { entry ->
            val name = entry.name.lowercase()
            val alias = entry.aliases.minOfOrNull { a -> when {
                a.lowercase().startsWith(q) -> 1
                q in a.lowercase() -> 3
                else -> 99
            } } ?: 99
            val score = when { name.startsWith(q) -> 0; q in name -> 2; else -> alias }
            if (score == 99) null else score to entry
        }.sortedBy { it.first }.take(limit).map { it.second }
    }

    fun resolve(word: String): CatalogEntry? = search(word, 1).firstOrNull()?.takeIf { entry ->
        entry.name.startsWith(word, true) || entry.aliases.any { it.startsWith(word, true) }
    }

    companion object {
        @Volatile private var instance: MedicationCatalog? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: MedicationCatalog(context.applicationContext).also { instance = it }
        }
    }
}
