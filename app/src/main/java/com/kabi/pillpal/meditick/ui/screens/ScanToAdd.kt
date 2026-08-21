@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.camera.core.ExperimentalGetImage::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.kabi.pillpal.meditick.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kabi.pillpal.meditick.R
import com.kabi.pillpal.meditick.BuildConfig
import com.kabi.pillpal.meditick.billing.AIScanPurchaseIdentity
import com.kabi.pillpal.meditick.data.CatalogEntry
import com.kabi.pillpal.meditick.data.MedicationCatalog
import com.kabi.pillpal.meditick.model.MedicationForm
import com.kabi.pillpal.meditick.ui.components.*
import com.kabi.pillpal.meditick.ui.theme.DS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

// Temporary, scanner-only Pro bypass for local testing. BuildConfig.DEBUG
// makes this false in every release build even if the block is left in place.
internal object ScanDebugOptions {
    val forceProAccess: Boolean = BuildConfig.DEBUG // TEMP: replace with false before committing.
}

/** Keeps image-based AI dormant while on-device OCR remains available. */
internal object ScanFeatures {
    @Volatile
    var aiScanEnabled: Boolean = false
        private set
}

//
// "Scan to Add" — point the camera at a medicine label and MediTick
// identifies the medication, mirroring the iOS ScanToAdd feature:
//  1. On-device OCR (ML Kit) fuzzy-matched against the bundled catalog —
//     free, offline, nothing leaves the device.
//  2. Pro sends one downsampled label frame through the MediTick backend to
//     Claude vision. The backend owns the model key and subscription quota.
//

// MARK: - Candidate model

data class ScanCandidate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Secondary line, e.g. the brand alias that matched ("NyQuil"). */
    val detail: String? = null,
    /** Raw strength text as read from the label, e.g. "500 mg" or "1.4 %". */
    val strengthText: String? = null,
    val form: MedicationForm? = null,
    /** 0…1 — how sure the matcher is. */
    val confidence: Double,
    val source: Source,
    val category: String? = null,
    val note: String? = null,
) {
    enum class Source { CATALOG, RXNORM, AI }
}

// MARK: - Matching engine (pure, unit-testable — the twin of iOS ScanEngine)

object ScanEngine {

    /** Words that appear on packaging but never help identify the medication. */
    private val noiseWords = setOf(
        "tablets", "tablet", "capsules", "capsule", "caplets", "caplet",
        "softgels", "softgel", "liquid", "syrup", "oral", "solution",
        "suspension", "spray", "drops", "cream", "ointment", "gel",
        "relief", "severe", "extra", "strength", "maximum", "max", "fast",
        "acting", "rapid", "release", "extended", "daily", "nighttime",
        "daytime", "adult", "children", "childrens", "original", "value",
        "pack", "count", "net", "contents", "warning", "warnings", "keep",
        "out", "reach", "directions", "dosage", "use", "uses",
        "active", "ingredient", "ingredients", "each", "contains", "per",
        "the", "and", "for", "with", "new", "free",
    )

    /** Keyword → medication form, checked against the whole recognized text. */
    private val formKeywords = listOf(
        "tablet" to MedicationForm.tablet, "caplet" to MedicationForm.tablet,
        "capsule" to MedicationForm.capsule, "softgel" to MedicationForm.capsule,
        "syrup" to MedicationForm.liquid, "liquid" to MedicationForm.liquid,
        "solution" to MedicationForm.liquid, "suspension" to MedicationForm.liquid,
        "elixir" to MedicationForm.liquid,
        "spray" to MedicationForm.spray,
        "drops" to MedicationForm.drops, "drop" to MedicationForm.drops,
        "cream" to MedicationForm.cream, "ointment" to MedicationForm.cream,
        "gel" to MedicationForm.cream, "lotion" to MedicationForm.cream,
        "inhaler" to MedicationForm.inhaler, "inhalation" to MedicationForm.inhaler,
        "injection" to MedicationForm.injection, "injectable" to MedicationForm.injection,
        "patch" to MedicationForm.patch,
        "powder" to MedicationForm.powder, "sachet" to MedicationForm.powder,
    )

    // Lookahead instead of \b so "%" terminates a match and "g" can't begin
    // one inside "gel"/"gels".
    private val strengthRegex = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*(mg|mcg|g|ml|iu|%)(?![a-z%])""", RegexOption.IGNORE_CASE)

    /** Extracts every strength-looking value ("500 mg", "1.4%", "1000IU"). */
    fun strengths(text: String): List<String> {
        val results = mutableListOf<String>()
        for (match in strengthRegex.findAll(text)) {
            val value = match.groupValues[1].replace(',', '.')
            var unit = match.groupValues[2].lowercase()
            if (unit == "iu") unit = "IU"
            val strength = "$value $unit"
            if (strength !in results) results.add(strength)
        }
        return results
    }

    /** Detects the medication form named on the label, if any. */
    fun formIn(text: String): MedicationForm? {
        val lowered = text.lowercase()
        return formKeywords.firstOrNull { lowered.contains(it.first) }?.second
    }

    /** Ranks catalog entries against the recognized label text. */
    fun candidates(lines: List<String>, entries: List<CatalogEntry>, limit: Int = 4): List<ScanCandidate> {
        val joined = lines.joinToString(" ")
        if (joined.isBlank()) return emptyList()

        val textTokens = tokens(joined)
        if (textTokens.isEmpty()) return emptyList()
        val labelStrengths = strengths(joined)
        val labelForm = formIn(joined)

        data class Scored(val entry: CatalogEntry, val score: Double, val matchedAlias: String?)

        val scored = mutableListOf<Scored>()
        for (entry in entries) {
            var best = matchScore(entry.name, textTokens)
            var alias: String? = null
            for (a in entry.aliases) {
                val s = matchScore(a, textTokens)
                if (s > best) { best = s; alias = a }
            }
            if (best >= 0.55) scored.add(Scored(entry, best, alias))
        }
        scored.sortByDescending { it.score }

        return scored.take(limit).map { hit ->
            // Prefer a strength that this entry is actually sold in.
            val strength = labelStrengths.firstOrNull { raw ->
                hit.entry.strengths.any { normalizedStrength(it) == normalizedStrength(raw) }
            } ?: labelStrengths.firstOrNull()
            ScanCandidate(
                name = hit.entry.name,
                detail = hit.matchedAlias,
                strengthText = strength,
                form = labelForm ?: hit.entry.form,
                confidence = hit.score,
                source = ScanCandidate.Source.CATALOG,
            )
        }
    }

    /**
     * A short, name-like query string for online fallback lookups: the most
     * promising alphabetic tokens plus the first strength found.
     */
    fun onlineQuery(lines: List<String>): String {
        val joined = lines.joinToString(" ")
        val meaningful = tokens(joined)
            .filter { it.length >= 4 && it !in noiseWords && it.toDoubleOrNull() == null }
            .take(5)
        var query = meaningful.joinToString(" ")
        strengths(joined).firstOrNull()?.let { query += " $it" }
        return query.take(120)
    }

    // MARK: Internals

    private fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("""[^\p{L}\p{N}.%]+""")).filter { it.length >= 2 }

    /**
     * Fraction (0…1) of the candidate name's significant tokens found in the
     * label text, weighted by token length so "acetaminophen" counts for
     * more than "pm". Tokens tolerate one OCR typo.
     */
    private fun matchScore(candidate: String, textTokens: List<String>): Double {
        val candidateTokens = tokens(candidate).filter { it !in noiseWords }
        if (candidateTokens.isEmpty()) return 0.0

        var matchedWeight = 0.0
        var totalWeight = 0.0
        for (token in candidateTokens) {
            val weight = token.length.toDouble()
            totalWeight += weight
            if (textTokens.any { tokensMatch(it, token) }) matchedWeight += weight
        }
        // Very short names ("Zinc") need a full match to count at all.
        val score = matchedWeight / totalWeight
        if (candidateTokens.size == 1 && candidateTokens[0].length <= 4 && score < 1.0) return 0.0
        return score
    }

    private fun tokensMatch(a: String, b: String): Boolean {
        if (a == b) return true
        // Prefix match covers truncated OCR reads of long names.
        if (b.length >= 6 && a.length >= 5 && (b.startsWith(a) || a.startsWith(b))) return true
        // One edit of slack for OCR misreads on longer tokens.
        if (b.length >= 5 && abs(a.length - b.length) <= 1) return editDistanceAtMostOne(a, b)
        return false
    }

    private fun editDistanceAtMostOne(a: String, b: String): Boolean {
        if (abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            edits++
            if (edits > 1) return false
            when {
                a.length == b.length -> { i++; j++ }
                a.length > b.length -> i++
                else -> j++
            }
        }
        edits += (a.length - i) + (b.length - j)
        return edits <= 1
    }

    private fun normalizedStrength(raw: String): String =
        raw.lowercase().replace(" ", "").replace(",", ".")
}

// MARK: - RxNorm fallback (free NLM web service, text only)

object RxNormService {

    /**
     * Looks up the recognized text against RxNorm's approximate-match
     * service. Free, no API key, US drug names. Fails soft: any network or
     * parsing problem returns an empty list.
     */
    suspend fun approximateMatches(query: String, maxEntries: Int = 4): List<ScanCandidate> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val url = URL(
                "https://rxnav.nlm.nih.gov/REST/approximateTerm.json?term=" +
                    URLEncoder.encode(query, "UTF-8") + "&maxEntries=${maxEntries * 3}&option=1",
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (connection.responseCode != 200) return@runCatching emptyList()

            val raw = JSONObject(body).optJSONObject("approximateGroup")?.optJSONArray("candidate")
                ?: return@runCatching emptyList()
            val seen = mutableSetOf<String>()
            val results = mutableListOf<ScanCandidate>()
            for (index in 0 until raw.length()) {
                val candidate = raw.optJSONObject(index) ?: continue
                val name = candidate.optString("name")
                if (name.isEmpty() || !seen.add(name.lowercase())) continue
                val score = candidate.optString("score").toDoubleOrNull() ?: 0.0
                results.add(ScanCandidate(
                    name = name,
                    strengthText = ScanEngine.strengths(name).firstOrNull(),
                    form = ScanEngine.formIn(name),
                    confidence = minOf(score / 100, 1.0) * 0.75,
                    source = ScanCandidate.Source.RXNORM,
                ))
                if (results.size == maxEntries) break
            }
            results
        }.getOrDefault(emptyList())
    }
}

// MARK: - Server-backed Claude vision recognition (Pro)

object AIScanService {
    val isAvailable: Boolean
        get() = BuildConfig.AI_SCAN_ENDPOINT.isNotBlank() && BuildConfig.AI_SCAN_CLIENT_TOKEN.isNotBlank()

    data class Usage(val used: Int, val limit: Int, val remaining: Int, val resetsAt: String?)
    data class Result(val candidates: List<ScanCandidate>, val usage: Usage?)
    class QuotaException(val usage: Usage?) : Exception()
    class ScanException : Exception()

    suspend fun analyze(
        image: ByteArray,
        hintText: String,
        purchaseIdentity: AIScanPurchaseIdentity,
    ): Result = withContext(Dispatchers.IO) {
        if (!isAvailable) throw ScanException()
        val connection = URL(BuildConfig.AI_SCAN_ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 12_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.AI_SCAN_CLIENT_TOKEN}")
            val body = JSONObject()
                .put("image_base64", Base64.encodeToString(image, Base64.NO_WRAP))
                .put("media_type", "image/jpeg")
                .put("hint_text", hintText.take(1500))
                .put("identity", purchaseIdentity.putInto(JSONObject()))
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val root = runCatching { JSONObject(responseText) }.getOrNull()
            val usage = root?.optJSONObject("usage")?.let(::parseUsage)
            if (status == 429) throw QuotaException(usage)
            if (status != 200 || root == null) throw ScanException()

            val raw = root.optJSONArray("candidates") ?: JSONArray()
            val candidates = buildList {
                for (index in 0 until minOf(raw.length(), 3)) {
                    val item = raw.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    if (name.isEmpty()) continue
                    val generic = item.optString("generic").trim().ifEmpty { null }
                    val form = MedicationForm.entries.firstOrNull { it.name == item.optString("form").lowercase() }
                    add(ScanCandidate(
                        name = name,
                        detail = generic?.takeUnless { it.equals(name, ignoreCase = true) },
                        strengthText = item.optString("strength").trim().ifEmpty { null },
                        form = form ?: MedicationForm.other,
                        confidence = item.optDouble("confidence", .9).coerceIn(0.0, 1.0),
                        source = ScanCandidate.Source.AI,
                        category = item.optString("category").trim().ifEmpty { null },
                        note = item.optString("note").trim().ifEmpty { null },
                    ))
                }
            }
            Result(candidates, usage)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseUsage(raw: JSONObject) = Usage(
        used = raw.optInt("used"),
        limit = raw.optInt("limit", 30),
        remaining = raw.optInt("remaining"),
        resetsAt = raw.optString("resets_at").takeIf { it.isNotEmpty() && it != "null" },
    )
}

private fun compressedScanImage(file: File): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 1400) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 72, output)
        bitmap.recycle()
        output.toByteArray()
    }
}

// MARK: - Free on-device scan metering (Pro AI quota is enforced server-side)

object ScanQuota {
    const val FREE_SCANS = 3
    /** The lifetime allowance attached to a server-verified Pro purchase. */
    const val AI_SCANS = 100
    private const val USED_KEY = "freeScansUsed"
    private const val AI_REMAINING_KEY = "aiScansRemaining"
    private const val AI_QUOTA_VERSION_KEY = "aiQuotaVersion"

    private fun prefs(context: Context) = context.getSharedPreferences("meditick.scan", Context.MODE_PRIVATE)

    fun remainingFree(context: Context): Int = maxOf(FREE_SCANS - prefs(context).getInt(USED_KEY, 0), 0)

    fun canStart(context: Context, isPro: Boolean): Boolean = isPro || remainingFree(context) > 0

    /**
     * A scan is "spent" only when the user accepts a match — retries and
     * abandoned scans stay free, so trying the feature never feels risky.
     */
    fun consume(context: Context, isPro: Boolean) {
        if (isPro) return
        val store = prefs(context)
        store.edit().putInt(USED_KEY, store.getInt(USED_KEY, 0) + 1).apply()
    }

    /**
     * The Pro allowance is metered server-side, so the last figure the backend
     * reported is cached here — the entry cards can name a number before the
     * first scan of a session instead of showing a blank.
     */
    fun aiRemaining(context: Context): Int {
        val store = prefs(context)
        return if (store.getInt(AI_QUOTA_VERSION_KEY, 0) >= 2) {
            store.getInt(AI_REMAINING_KEY, AI_SCANS)
        } else AI_SCANS
    }

    fun rememberAIRemaining(context: Context, remaining: Int) {
        prefs(context).edit()
            .putInt(AI_REMAINING_KEY, remaining.coerceIn(0, AI_SCANS))
            .putInt(AI_QUOTA_VERSION_KEY, 2)
            .apply()
    }
}

// MARK: - Scan screen

private enum class ScanState { RUNNING, DENIED, UNAVAILABLE }

/** Where the camera is in the Instant Scan story. */
private enum class ScanPhase { SEARCHING, LOCKED, ANALYZING, FOUND }

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ScanToAddScreen(
    isPro: Boolean,
    purchaseIdentity: AIScanPurchaseIdentity?,
    onClose: () -> Unit,
    onMatch: (ScanCandidate) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val catalog = remember { MedicationCatalog.get(context) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    var state by remember {
        mutableStateOf(
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                ScanState.RUNNING else ScanState.DENIED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state = if (granted) ScanState.RUNNING else ScanState.DENIED
    }
    var askedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (state != ScanState.RUNNING && !askedOnce) {
            askedOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Recognized lines accumulate across frames (with vote counts) so a
    // steady label converges on a stable reading.
    val lineVotes = remember { mutableMapOf<String, Int>() }
    var recognizedLines by remember { mutableStateOf(listOf<String>()) }
    var analysisEnabled by remember { mutableStateOf(true) }
    val analysisEnabledNow by rememberUpdatedState(analysisEnabled)

    var matches by remember { mutableStateOf(listOf<ScanCandidate>()) }
    var showMatches by remember { mutableStateOf(false) }
    var identifying by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var searchedOnline by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiRemaining by remember { mutableIntStateOf(ScanQuota.aiRemaining(context)) }
    var checkStage by remember { mutableIntStateOf(0) }
    var stableHits by remember { mutableIntStateOf(0) }
    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val isAI = ScanFeatures.aiScanEnabled && isPro &&
        AIScanService.isAvailable && purchaseIdentity != null

    val phase = when {
        showMatches -> ScanPhase.FOUND
        finished -> ScanPhase.FOUND
        identifying -> ScanPhase.ANALYZING
        recognizedLines.isNotEmpty() -> ScanPhase.LOCKED
        else -> ScanPhase.SEARCHING
    }

    fun presentMatches() {
        haptics.tap()
        analysisEnabled = false
        showMatches = true
    }

    fun resumeScanning() {
        stableHits = 0
        lineVotes.clear()
        recognizedLines = emptyList()
        analysisEnabled = true
        aiError = null
        finished = false
        checkStage = 0
    }

    /**
     * The landing the design asks for: the ring closes, the check draws, and
     * only then do the results glide up. A failed scan skips the celebration.
     */
    fun land(succeeded: Boolean) {
        identifying = false
        if (!succeeded) { presentMatches(); return }
        finished = true
        haptics.success()
        scope.launch {
            delay(1500)
            presentMatches()
        }
    }

    fun identifyLocally(errorMessage: String? = null) {
        scope.launch {
            val local = ScanEngine.candidates(recognizedLines, catalog.all())
            if (local.isEmpty()) {
                val query = ScanEngine.onlineQuery(recognizedLines)
                matches = RxNormService.approximateMatches(query)
                searchedOnline = query.isNotEmpty()
            } else {
                matches = local.take(4)
                searchedOnline = false
            }
            aiError = errorMessage
            land(errorMessage == null && matches.isNotEmpty())
        }
    }

    fun identifyNow() {
        if (identifying || finished) return
        identifying = true
        analysisEnabled = false
        checkStage = 0
        aiError = null

        if (!isAI || purchaseIdentity == null) {
            identifyLocally()
            return
        }

        val photo = File.createTempFile("meditick-scan-", ".jpg", context.cacheDir)
        val options = ImageCapture.OutputFileOptions.Builder(photo).build()
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                scope.launch {
                    try {
                        val bytes = withContext(Dispatchers.IO) { compressedScanImage(photo) } ?: throw AIScanService.ScanException()
                        val result = AIScanService.analyze(bytes, recognizedLines.joinToString("\n"), purchaseIdentity)
                        matches = result.candidates
                        result.usage?.remaining?.let { aiRemaining = it; ScanQuota.rememberAIRemaining(context, it) }
                        searchedOnline = false
                        if (matches.isEmpty()) aiError = context.getString(R.string.scan_ai_no_match)
                        land(matches.isNotEmpty())
                    } catch (quota: AIScanService.QuotaException) {
                        aiRemaining = quota.usage?.remaining ?: 0
                        ScanQuota.rememberAIRemaining(context, aiRemaining)
                        matches = emptyList()
                        aiError = context.getString(R.string.scan_ai_limit)
                        land(false)
                    } catch (_: Exception) {
                        identifyLocally(context.getString(R.string.scan_ai_fallback))
                    } finally {
                        photo.delete()
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                photo.delete()
                identifyLocally(context.getString(R.string.scan_ai_fallback))
            }
        })
    }

    // The three "what MediTick is doing" rows tick over while the model works.
    LaunchedEffect(identifying) {
        if (!identifying) return@LaunchedEffect
        while (identifying && checkStage < 3) {
            delay(1200)
            checkStage += 1
        }
    }

    // Auto-present as soon as the reading is stable and confident — the
    // "it just recognized it" moment from the reference flow.
    LaunchedEffect(recognizedLines) {
        if (isAI || showMatches || identifying || finished) return@LaunchedEffect
        val local = ScanEngine.candidates(recognizedLines, catalog.all())
        val best = local.firstOrNull()
        if (best == null || best.confidence < 0.8) { stableHits = 0; return@LaunchedEffect }
        stableHits += 1
        if (stableHits < 2) return@LaunchedEffect
        matches = local
        searchedOnline = false
        land(true)
    }

    Box(Modifier.fillMaxSize().background(ScanInk.backdrop)) {
        when (state) {
            ScanState.RUNNING -> {
                // Camera preview + throttled ML Kit OCR.
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        var lastAnalysis = 0L
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                            // Throttle OCR — a few analyses per second is
                            // plenty for a label.
                            val now = SystemClock.elapsedRealtime()
                            val media = proxy.image
                            if (!analysisEnabledNow || media == null || now - lastAnalysis < 400) { proxy.close(); return@setAnalyzer }
                            lastAnalysis = now
                            val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                            recognizer.process(input)
                                .addOnSuccessListener { result ->
                                    val lines = result.textBlocks.flatMap { it.lines }
                                        .map { it.text.trim() }.filter { it.length >= 2 }
                                    for (line in lines) lineVotes[line] = (lineVotes[line] ?: 0) + 1
                                    // Keep the table bounded on busy scenes.
                                    if (lineVotes.size > 60) {
                                        val keep = lineVotes.entries.sortedByDescending { it.value }.take(40)
                                        lineVotes.clear(); keep.forEach { lineVotes[it.key] = it.value }
                                    }
                                    recognizedLines = lineVotes.entries.sortedByDescending { it.value }.map { it.key }
                                }
                                .addOnCompleteListener { proxy.close() }
                        }
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            runCatching {
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                                provider.unbindAll()
                                camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, imageCapture)
                            }.onFailure { state = ScanState.UNAVAILABLE }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                )
                ScanOverlay(
                    phase = phase,
                    isAI = isAI,
                    cameraReady = camera != null,
                    onIdentify = { haptics.tap(); identifyNow() },
                )
            }
            ScanState.DENIED -> PermissionScreen(
                title = stringResource(R.string.scan_camera_needed_title),
                message = stringResource(R.string.scan_camera_needed_body),
                showsSettingsButton = true,
            )
            ScanState.UNAVAILABLE -> PermissionScreen(
                title = stringResource(R.string.scan_camera_unavailable_title),
                message = stringResource(R.string.scan_camera_unavailable_body),
                showsSettingsButton = false,
            )
        }

        if (identifying || finished) AnalyzingOverlay(
            stage = checkStage,
            finished = finished,
            isAI = isAI,
            best = matches.firstOrNull(),
        )

        // Chrome: close · what to do · torch.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanChromeButton(Icons.Default.Close, stringResource(R.string.action_close), onClose)
            if (!identifying && !finished) Text(
                stringResource(R.string.scan_position),
                color = Color.White.copy(.92f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            ) else Spacer(Modifier.weight(1f))
            if (state == ScanState.RUNNING && !identifying && !finished) ScanChromeButton(
                if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff, null,
            ) { torchOn = !torchOn; camera?.cameraControl?.enableTorch(torchOn) }
            else Spacer(Modifier.size(42.dp))
        }
    }

    if (showMatches) ResultsSheet(
        matches = matches, searchedOnline = searchedOnline,
        isAIResult = matches.firstOrNull()?.source == ScanCandidate.Source.AI,
        aiRemaining = aiRemaining, errorMessage = aiError,
        onPick = { candidate ->
            haptics.success()
            showMatches = false
            onMatch(candidate)
        },
        onTryAgain = { showMatches = false; resumeScanning() },
        onDismiss = { showMatches = false; resumeScanning() },
    )
}

// MARK: - Camera overlay

/** Fixed inks for the camera surface — it is dark in both appearance modes. */
private object ScanInk {
    val backdrop = Color(0xFF0A100C)
    val idle = Color.White.copy(alpha = .65f)
    val locked = Color(0xFF35D695)
    val hintIdle = Color.White.copy(alpha = .75f)
    val hintLocked = Color(0xFF5EE6A8)
    val paper = Color(0xFFF1F7F2)
}

@Composable
private fun ScanChromeButton(icon: ImageVector, description: String?, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(21.dp))
            .background(Color.White.copy(.10f))
            .border(1.dp, Color.White.copy(.16f), RoundedCornerShape(21.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(17.dp)) }
}

@Composable
private fun ScanOverlay(phase: ScanPhase, isAI: Boolean, cameraReady: Boolean, onIdentify: () -> Unit) {
    val locked = phase != ScanPhase.SEARCHING
    val transition = rememberInfiniteTransition(label = "reticle")
    // Idle corners breathe; a locked frame holds still and sweeps instead.
    val breathe by transition.animateFloat(
        1f, 1.04f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "breathe",
    )
    val sweep by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "sweep",
    )
    val cornerColor by animateColorAsState(
        if (locked) ScanInk.locked else ScanInk.idle, tween(400), label = "corner",
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frameWidth = (maxWidth - 96.dp).coerceAtMost(252.dp)
        val frameHeight = frameWidth * 1.355f
        Box(
            Modifier.align(Alignment.Center).offset(y = (-42).dp)
                .size(frameWidth + 14.dp, frameHeight + 14.dp)
                .graphicsLayer(scaleX = if (locked) 1f else breathe, scaleY = if (locked) 1f else breathe),
            contentAlignment = Alignment.Center,
        ) {
            // The lock ring sits just outside the brackets, so it lives on the
            // outer box and the reticle is inset within it.
            if (locked) Box(
                Modifier.fillMaxSize()
                    .border(2.dp, ScanInk.locked.copy(alpha = .55f), RoundedCornerShape(20.dp)),
            )
            Box(Modifier.size(frameWidth, frameHeight)) {
                Canvas(Modifier.fillMaxSize()) {
                    val arm = 32.dp.toPx()
                    val stroke = 3.5.dp.toPx()
                    val radius = 13.dp.toPx()
                    drawCornerBrackets(cornerColor, arm, stroke, radius)
                }
                // The beam that reads the framed label.
                if (locked) Box(
                    Modifier.align(Alignment.TopCenter)
                        .offset(y = (frameHeight - 40.dp) * sweep)
                        .fillMaxWidth().height(40.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, ScanInk.locked.copy(alpha = .40f), Color.Transparent),
                            ),
                            RoundedCornerShape(10.dp),
                        ),
                )
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = 26.dp, vertical = 30.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(if (locked) R.string.scan_locked else R.string.scan_looking),
                color = if (locked) ScanInk.hintLocked else ScanInk.hintIdle,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
            if (isAI) Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = ScanInk.hintLocked, modifier = Modifier.size(11.dp))
                Text(stringResource(R.string.scan_ai_checks), color = Color.White.copy(.5f), fontSize = 11.5.sp)
            }
            IdentifyButton(
                text = stringResource(if (isAI) R.string.scan_identify_ai else R.string.scan_identify),
                enabled = locked && cameraReady,
                onClick = onIdentify,
            )
        }
    }
}

/** The four L-shaped brackets that frame the label. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCornerBrackets(
    color: Color, arm: Float, stroke: Float, radius: Float,
) {
    val half = stroke / 2f
    val w = size.width
    val h = size.height
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(x + dx * arm, y + dy * half)
            lineTo(x + dx * (radius + half), y + dy * half)
            quadraticTo(x + dx * half, y + dy * half, x + dx * half, y + dy * (radius + half))
            lineTo(x + dx * half, y + dy * arm)
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    corner(0f, 0f, 1f, 1f)
    corner(w, 0f, -1f, 1f)
    corner(0f, h, 1f, -1f)
    corner(w, h, -1f, -1f)
}

@Composable
private fun IdentifyButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = DS.colors
    val interaction = remember { MutableInteractionSource() }
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(400), label = "identifyAlpha")
    Box(
        Modifier.fillMaxWidth().alpha(alpha)
            .pressScale(interaction, 0.97f)
            .then(if (enabled) Modifier.auroraBorder(28.dp, 2.5.dp, durationMillis = 1800) else Modifier)
            .clip(RoundedCornerShape(28.dp))
            .background(c.gradient)
            .clickable(interaction, ripple(color = Color.White), enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.AutoAwesome, null, tint = c.onMint, modifier = Modifier.size(17.dp))
            Text(text, color = c.onMint, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// MARK: - Analyzing overlay

@Composable
private fun AnalyzingOverlay(stage: Int, finished: Boolean, isAI: Boolean, best: ScanCandidate?) {
    val context = LocalContext.current
    val c = DS.colors
    val transition = rememberInfiniteTransition(label = "analyzing")
    val orbit by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "orbit",
    )
    val shimmer by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "shimmer",
    )
    // The ring fills over the time a scan usually takes, then snaps closed on
    // the real answer rather than pretending to finish early.
    val ring by animateFloatAsState(
        if (finished) 1f else 0.92f,
        tween(if (finished) 420 else 4800, easing = FastOutSlowInEasing), label = "ring",
    )
    val specimenScale by animateFloatAsState(if (finished) 0.92f else 1f, tween(600), label = "specimen")

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF12291E), Color(0xFF0A1710), Color(0xFF060D09))),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Box(Modifier.size(224.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2f + 22.dp.toPx()
                    drawArc(
                        color = Color.White.copy(alpha = .09f), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(stroke),
                    )
                    drawArc(
                        brush = Brush.linearGradient(listOf(Color(0xFF35D695), Color(0xFF4CC5E8))),
                        startAngle = -90f, sweepAngle = 360f * ring, useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                if (!finished) Box(Modifier.fillMaxSize().graphicsLayer(rotationZ = orbit)) {
                    Icon(
                        Icons.Default.AutoAwesome, null, tint = ScanInk.hintLocked,
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = 14.dp)
                            .size(16.dp).graphicsLayer(rotationZ = -orbit),
                    )
                }
                SpecimenCard(finished = finished, scale = specimenScale, best = best)
                if (finished) FoundSeal()
            }

            Spacer(Modifier.height(26.dp))
            Column(
                Modifier.heightIn(min = 64.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (finished) {
                    val what = best?.form?.title(context)?.lowercase()
                        ?: best?.name
                        ?: stringResource(R.string.scan_none_title)
                    Text(
                        stringResource(R.string.scan_found, what),
                        color = ScanInk.paper, fontSize = 20.sp, fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(
                            R.string.scan_found_sub,
                            ((best?.confidence ?: 0.0) * 100).roundToInt().coerceIn(0, 100),
                        ),
                        color = ScanInk.hintLocked, fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        stringResource(if (isAI) R.string.scan_ai_identifying else R.string.scan_local_identifying),
                        fontSize = 19.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                        style = LocalTextStyle.current.copy(brush = shimmerBrush(shimmer)),
                    )
                    Text(
                        stringResource(
                            listOf(
                                R.string.scan_phase_1, R.string.scan_phase_2,
                                R.string.scan_phase_3, R.string.scan_phase_4,
                            )[stage.coerceIn(0, 3)],
                        ),
                        color = ScanInk.paper.copy(alpha = .55f), fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.widthIn(max = 270.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                listOf(R.string.scan_check_read, R.string.scan_check_form, R.string.scan_check_details)
                    .forEachIndexed { index, label ->
                        CheckRow(
                            label = stringResource(label),
                            done = finished || stage > index,
                            active = !finished && stage == index,
                        )
                    }
            }
        }
    }
}

/** The title's left-to-right sheen while the model is thinking. */
private fun shimmerBrush(phase: Float): Brush {
    val span = 900f
    val start = -span + phase * span * 2
    return Brush.linearGradient(
        listOf(ScanInk.paper, Color(0xFF5EE6A8), ScanInk.paper),
        start = Offset(start, 0f),
        end = Offset(start + span, 0f),
    )
}

/** The frosted card at the centre of the ring, cycling through guesses. */
@Composable
private fun SpecimenCard(finished: Boolean, scale: Float, best: ScanCandidate?) {
    val guesses = remember { listOf(MedicationForm.tablet, MedicationForm.capsule, MedicationForm.powder, MedicationForm.liquid) }
    var guess by remember { mutableIntStateOf(0) }
    LaunchedEffect(finished) {
        while (!finished) {
            delay(1200)
            guess = (guess + 1) % guesses.size
        }
    }
    val transition = rememberInfiniteTransition(label = "specimen")
    val beam by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "specimenBeam")
    Box(
        Modifier.size(96.dp, 128.dp).graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = .06f))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            if (finished) (best?.form ?: MedicationForm.other) else guesses[guess],
            animationSpec = tween(500), label = "guess",
        ) { form ->
            Icon(formIcon(form), null, tint = Color(0xFF8FE8C0), modifier = Modifier.size(52.dp))
        }
        if (!finished) Box(
            Modifier.align(Alignment.TopCenter).offset(y = 102.dp * beam)
                .fillMaxWidth().height(26.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, ScanInk.locked.copy(alpha = .45f), Color.Transparent),
                    ),
                ),
        )
    }
}

/** The check that draws itself once the ring closes. */
@Composable
private fun FoundSeal() {
    val c = DS.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(
        if (shown) 1f else 0.6f,
        spring(dampingRatio = 0.5f, stiffness = 420f), label = "seal",
    )
    val ping by animateFloatAsState(if (shown) 2.1f else 0.6f, tween(1100), label = "ping")
    val pingAlpha by animateFloatAsState(if (shown) 0f else 0.8f, tween(1100), label = "pingAlpha")
    Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(60.dp).graphicsLayer(scaleX = ping, scaleY = ping, alpha = pingAlpha)
                .border(2.dp, ScanInk.locked, RoundedCornerShape(50)),
        )
        Box(
            Modifier.size(60.dp).graphicsLayer(scaleX = pop, scaleY = pop)
                .clip(RoundedCornerShape(50)).background(c.gradient),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Check, null, tint = c.onMint, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun CheckRow(label: String, done: Boolean, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "checkRow")
    val pulse by transition.animateFloat(0.75f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")
    val alpha by animateFloatAsState(if (done || active) 1f else 0.45f, tween(500), label = "rowAlpha")
    Row(
        Modifier.fillMaxWidth().alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            done -> Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(8.dp)).background(ScanInk.locked.copy(alpha = .16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Check, null, tint = ScanInk.hintLocked, modifier = Modifier.size(11.dp)) }
            active -> Box(
                Modifier.size(20.dp).alpha(pulse)
                    .border(2.dp, ScanInk.locked, RoundedCornerShape(8.dp)),
            )
            else -> Box(
                Modifier.size(20.dp).border(2.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(8.dp)),
            )
        }
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = when {
                done -> Color(0xFFB9CEC0)
                active -> ScanInk.paper
                else -> ScanInk.paper.copy(alpha = .4f)
            },
        )
    }
}

// MARK: - Results sheet

@Composable
private fun ResultsSheet(
    matches: List<ScanCandidate>, searchedOnline: Boolean,
    isAIResult: Boolean, aiRemaining: Int, errorMessage: String?,
    onPick: (ScanCandidate) -> Unit, onTryAgain: () -> Unit, onDismiss: () -> Unit,
) {
    val c = DS.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = c.bg2,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), dragHandle = { SheetDragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 6.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(16.dp))
                        .background(c.mint.copy(alpha = .12f)).appearFluidly(0),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (matches.isEmpty()) Icons.Default.CenterFocusWeak else Icons.Default.Check,
                        null, tint = if (matches.isEmpty()) c.ink3 else c.mint, modifier = Modifier.size(19.dp),
                    )
                }
                Text(
                    stringResource(if (matches.isEmpty()) R.string.scan_no_confident else R.string.scan_confirm_title),
                    color = c.ink, fontSize = 21.sp, fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center, modifier = Modifier.appearFluidly(1),
                )
                Text(
                    stringResource(if (isAIResult) R.string.scan_ai_verify else R.string.scan_verify),
                    color = c.ink3, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
            }

            if (matches.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.HelpOutline, null, tint = c.ink3, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.scan_none_title), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(if (searchedOnline) R.string.scan_none_online else R.string.scan_none_local),
                        color = c.ink2, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)
                        .padding(horizontal = 18.dp).padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BestMatchCard(matches.first()) { onPick(matches.first()) }
                    matches.drop(1).forEachIndexed { index, candidate ->
                        RunnerUpRow(candidate, Modifier.appearFluidly(2 + index)) { onPick(candidate) }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                errorMessage?.let {
                    Text(it, color = c.coral, fontSize = 12.5.sp, textAlign = TextAlign.Center)
                } ?: run {
                    if (isAIResult) Text(
                        stringResource(R.string.scan_ai_remaining, aiRemaining),
                        color = c.ink3, fontSize = 11.5.sp, textAlign = TextAlign.Center,
                    )
                }
                GhostButton(stringResource(R.string.scan_try_again), onTryAgain, Modifier.fillMaxWidth())
            }
        }
    }
}

/** The hero result: aurora ring, confidence bar, verification chips, CTA. */
@Composable
private fun BestMatchCard(candidate: ScanCandidate, onPick: () -> Unit) {
    val c = DS.colors
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val percent = (candidate.confidence * 100).roundToInt().coerceIn(0, 100)
    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(candidate.id) { delay(300); grown = true }
    val bar by animateFloatAsState(if (grown) percent / 100f else 0f, tween(900), label = "confidence")

    Box(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(
            Modifier.fillMaxWidth().pressScale(interaction, 0.985f)
                .auroraBorder(24.dp, 2.5.dp)
                .clip(RoundedCornerShape(24.dp)).background(c.bg3)
                .clickable(interaction, ripple()) { haptics.tap(); onPick() }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                IconTile(formIcon(candidate.form ?: MedicationForm.other), c.cyan, 50.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(candidate.name, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(
                        listOfNotNull(
                            candidate.strengthText, candidate.form?.title(context),
                            stringResource(
                                when (candidate.source) {
                                    ScanCandidate.Source.CATALOG -> R.string.scan_source_catalog
                                    ScanCandidate.Source.RXNORM -> R.string.scan_source_rxnorm
                                    ScanCandidate.Source.AI -> R.string.scan_source_ai
                                },
                            ),
                        ).joinToString(" · "),
                        color = c.ink3, fontSize = 12.5.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$percent%", color = c.mint, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(
                        stringResource(R.string.scan_match_label),
                        color = c.ink3, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(c.glass2)) {
                Box(Modifier.fillMaxWidth(bar).fillMaxHeight().clip(RoundedCornerShape(50)).background(c.gradient))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                candidate.category?.let { ResultChip(it.uppercase(), c.cyan, c.cyan.copy(alpha = .1f), bold = true) }
                candidate.detail?.let {
                    ResultChip(stringResource(R.string.scan_matched_alias, it), c.ink2, c.glass2)
                }
                candidate.form?.let { ResultChip(it.title(context), c.ink2, c.glass2) }
            }
            candidate.note?.let {
                Text(it, color = c.ink2, fontSize = 12.5.sp, lineHeight = 18.sp)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.gradient).padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.scan_use_match),
                    color = c.onMint, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, null, tint = c.onMint, modifier = Modifier.size(14.dp))
            }
        }
        // Rides the top edge of the card, like the design's floating tag.
        Row(
            Modifier.align(Alignment.TopStart).offset(x = 16.dp)
                .clip(RoundedCornerShape(50)).background(c.gradient)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = c.onMint, modifier = Modifier.size(9.dp))
            Text(
                stringResource(R.string.scan_best_match),
                color = c.onMint, fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp,
            )
        }
    }
}

@Composable
private fun ResultChip(text: String, ink: Color, background: Color, bold: Boolean = false) {
    Text(
        text, color = ink, fontSize = 10.sp,
        fontWeight = if (bold) FontWeight.Black else FontWeight.Bold,
        letterSpacing = if (bold) 0.5.sp else 0.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun RunnerUpRow(candidate: ScanCandidate, modifier: Modifier = Modifier, onPick: () -> Unit) {
    val c = DS.colors
    val percent = (candidate.confidence * 100).roundToInt().coerceIn(0, 100)
    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(candidate.id) { delay(450); grown = true }
    val bar by animateFloatAsState(if (grown) percent / 100f else 0f, tween(900), label = "runnerUp")
    GlassCard(modifier.fillMaxWidth(), radius = 20.dp, onClick = onPick, contentPadding = PaddingValues(horizontal = 15.dp, vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            IconTile(formIcon(candidate.form ?: MedicationForm.other), c.ink3, 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(candidate.name, color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                candidate.note?.let { Text(it, color = c.ink3, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(c.glass2)) {
                    Box(Modifier.fillMaxWidth(bar).fillMaxHeight().clip(RoundedCornerShape(50)).background(c.ink3.copy(alpha = .55f)))
                }
            }
            Text("$percent%", color = c.ink3, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PermissionScreen(title: String, message: String, showsSettingsButton: Boolean) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.PhotoCamera, null, tint = Color.White.copy(.7f), modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.White.copy(.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
        if (showsSettingsButton) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(stringResource(R.string.scan_open_settings), {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                }
            }, Modifier.padding(horizontal = 20.dp).fillMaxWidth())
        }
    }
}
