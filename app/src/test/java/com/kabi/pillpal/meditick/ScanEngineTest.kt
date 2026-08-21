package com.kabi.pillpal.meditick

import com.kabi.pillpal.meditick.data.CatalogEntry
import com.kabi.pillpal.meditick.model.MedicationForm
import com.kabi.pillpal.meditick.ui.screens.ScanCandidate
import com.kabi.pillpal.meditick.ui.screens.ScanEngine
import com.kabi.pillpal.meditick.ui.screens.parseStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Scan-to-Add matching engine — the same cases as the
 * iOS `ScanEngineTests`, so the two matchers stay behaviorally identical.
 */
class ScanEngineTest {

    /** A tiny fixed catalog so tests don't depend on the bundled JSON. */
    private val catalog = listOf(
        CatalogEntry("Acetaminophen", listOf("325 mg", "500 mg", "650 mg"), MedicationForm.tablet, listOf("Tylenol", "Panadol", "Calpol")),
        CatalogEntry("Dextromethorphan", listOf("15 mg", "30 mg"), MedicationForm.liquid, listOf("NyQuil", "Robitussin DM", "Delsym")),
        CatalogEntry("Ibuprofen", listOf("200 mg", "400 mg", "600 mg"), MedicationForm.tablet, listOf("Advil", "Motrin", "Brufen")),
        CatalogEntry("Zinc", listOf("50 mg"), MedicationForm.tablet, emptyList()),
    )

    // MARK: Strength extraction

    @Test fun extractsSimpleStrength() {
        assertEquals(listOf("200 mg"), ScanEngine.strengths("IBUPROFEN 200 mg tablets"))
    }

    @Test fun extractsPercentAndDecimalStrengths() {
        val found = ScanEngine.strengths("Sore Throat 1.4% Spray, 500mg backup")
        assertTrue(found.contains("1.4 %"))
        assertTrue(found.contains("500 mg"))
    }

    @Test fun canonicalizesIUUnit() {
        assertEquals(listOf("1000 IU"), ScanEngine.strengths("Vitamin D3 1000IU softgels"))
    }

    @Test fun preservesLiquidConcentrationForPrefill() {
        assertEquals("100" to "mg/5 mL", parseStrength("100 mg/5 mL"))
    }

    // MARK: Form detection

    @Test fun detectsFormKeywords() {
        assertEquals(MedicationForm.spray, ScanEngine.formIn("Nasal SPRAY 30ml"))
        assertEquals(MedicationForm.liquid, ScanEngine.formIn("oral suspension for children"))
        assertEquals(MedicationForm.tablet, ScanEngine.formIn("20 caplets"))
        assertNull(ScanEngine.formIn("just a name"))
    }

    // MARK: Catalog matching

    @Test fun matchesGenericNameFromLabelLines() {
        val lines = listOf("IBUPROFEN", "200 mg", "Pain reliever / fever reducer", "50 tablets")
        val candidates = ScanEngine.candidates(lines, catalog)
        assertEquals("Ibuprofen", candidates.first().name)
        assertEquals("200 mg", candidates.first().strengthText)
        assertEquals(MedicationForm.tablet, candidates.first().form)
        assertEquals(ScanCandidate.Source.CATALOG, candidates.first().source)
    }

    @Test fun matchesBrandAliasAndReportsIt() {
        val lines = listOf("NyQuil", "Cold & Flu", "Nighttime Relief", "Liquid")
        val candidates = ScanEngine.candidates(lines, catalog)
        assertEquals("Dextromethorphan", candidates.first().name)
        assertTrue(candidates.first().detail?.contains("NyQuil") == true)
    }

    @Test fun toleratesSingleCharacterOcrTypo() {
        // "Ibuprofon" — one substituted character.
        val candidates = ScanEngine.candidates(listOf("IBUPROFON 400 mg film-coated tablets"), catalog)
        assertEquals("Ibuprofen", candidates.first().name)
    }

    @Test fun shortNamesRequireExactMatch() {
        // "Zin" must not fuzzy-match the 4-letter entry "Zinc".
        val noMatch = ScanEngine.candidates(listOf("Zin tablets"), catalog)
        assertFalse(noMatch.any { it.name == "Zinc" })

        val exact = ScanEngine.candidates(listOf("ZINC 50 mg tablets"), catalog)
        assertEquals("Zinc", exact.first().name)
    }

    @Test fun emptyInputYieldsNoCandidates() {
        assertTrue(ScanEngine.candidates(emptyList(), catalog).isEmpty())
        assertTrue(ScanEngine.candidates(listOf("  "), catalog).isEmpty())
    }

    @Test fun prefersStrengthTheEntryIsSoldIn() {
        // 30 ml (bottle volume) is a strength-shaped red herring; 500 mg is real.
        val lines = listOf("Acetaminophen", "30 ml", "500 mg", "oral solution")
        val candidates = ScanEngine.candidates(lines, catalog)
        assertEquals("500 mg", candidates.first().strengthText)
    }

    // MARK: Online query building

    @Test fun onlineQueryKeepsNameAndStrengthDropsNoise() {
        val lines = listOf("ACETAMINOPHEN", "Extra Strength", "500 mg", "100 caplets", "fast acting relief")
        val query = ScanEngine.onlineQuery(lines)
        assertTrue(query.lowercase().contains("acetaminophen"))
        assertTrue(query.contains("500 mg"))
        assertFalse(query.lowercase().contains("caplets"))
    }
}
