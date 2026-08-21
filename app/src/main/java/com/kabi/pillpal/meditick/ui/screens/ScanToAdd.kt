@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.camera.core.ExperimentalGetImage::class,
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kabi.pillpal.meditick.R
import com.kabi.pillpal.meditick.BuildConfig
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

//
// "Scan to Add" — point the camera at a medicine label and MediTick
// identifies the medication, mirroring the iOS ScanToAdd feature:
//  1. On-device OCR (ML Kit) fuzzy-matched against the bundled catalog —
//     free, offline, nothing leaves the device.
//  2. Pro sends one downsampled label frame through the MediTick backend to
//     Claude vision. The backend owns the model key and monthly quota.
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

    data class Usage(val used: Int, val limit: Int, val remaining: Int, val resetsAt: String)
    data class Result(val candidates: List<ScanCandidate>, val usage: Usage?)
    class QuotaException(val usage: Usage?) : Exception()
    class ScanException : Exception()

    suspend fun analyze(image: ByteArray, hintText: String, accountId: String): Result = withContext(Dispatchers.IO) {
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
                .put("account_id", accountId)
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
        resetsAt = raw.optString("resets_at"),
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
    private const val USED_KEY = "freeScansUsed"

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
}

// MARK: - Scan screen

private enum class ScanState { RUNNING, DENIED, UNAVAILABLE }

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ScanToAddScreen(isPro: Boolean, accountID: String, onClose: () -> Unit, onMatch: (ScanCandidate) -> Unit) {
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
    var searchedOnline by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiRemaining by remember { mutableStateOf<Int?>(null) }
    var identificationStage by remember { mutableIntStateOf(0) }
    var stableHits by remember { mutableIntStateOf(0) }
    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

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
    }

    fun identifyLocally(errorMessage: String? = null) {
        scope.launch {
            val results = ScanEngine.candidates(recognizedLines, catalog.all())
            searchedOnline = false
            matches = results.take(4)
            aiError = errorMessage
            identifying = false
            presentMatches()
        }
    }

    fun identifyNow() {
        if (identifying) return
        identifying = true
        analysisEnabled = false
        identificationStage = 0
        aiError = null

        if (!isPro || !AIScanService.isAvailable) {
            identifyLocally(
                if (isPro) context.getString(R.string.scan_ai_not_configured) else null,
            )
            return
        }

        val photo = File.createTempFile("meditick-scan-", ".jpg", context.cacheDir)
        val options = ImageCapture.OutputFileOptions.Builder(photo).build()
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                scope.launch {
                    try {
                        val bytes = withContext(Dispatchers.IO) { compressedScanImage(photo) } ?: throw AIScanService.ScanException()
                        val result = AIScanService.analyze(bytes, recognizedLines.joinToString("\n"), accountID)
                        matches = result.candidates
                        aiRemaining = result.usage?.remaining
                        searchedOnline = false
                        if (matches.isEmpty()) aiError = context.getString(R.string.scan_ai_no_match)
                        identifying = false
                        presentMatches()
                    } catch (quota: AIScanService.QuotaException) {
                        aiRemaining = quota.usage?.remaining ?: 0
                        matches = emptyList()
                        aiError = context.getString(R.string.scan_ai_limit)
                        identifying = false
                        presentMatches()
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

    LaunchedEffect(identifying) {
        if (!identifying) return@LaunchedEffect
        while (identifying) {
            delay(850)
            identificationStage = (identificationStage + 1).coerceAtMost(2)
        }
    }

    // Auto-present as soon as the reading is stable and confident — the
    // "it just recognized it" moment from the reference flow.
    LaunchedEffect(recognizedLines) {
        if (isPro || showMatches || identifying) return@LaunchedEffect
        val local = ScanEngine.candidates(recognizedLines, catalog.all())
        val best = local.firstOrNull()
        if (best == null || best.confidence < 0.8) { stableHits = 0; return@LaunchedEffect }
        stableHits += 1
        if (stableHits < 2) return@LaunchedEffect
        matches = local
        searchedOnline = false
        presentMatches()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                    recognizedLines = recognizedLines,
                    identifying = identifying,
                    isAI = isPro && AIScanService.isAvailable,
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

        if (identifying) AIProcessingOverlay(identificationStage, isPro && AIScanService.isAvailable)

        // Chrome: close + torch.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundIconButton(Icons.Default.Close, stringResource(R.string.action_close), onClose)
            if (state == ScanState.RUNNING) {
                RoundIconButton(
                    if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    null,
                    onClick = { torchOn = !torchOn; camera?.cameraControl?.enableTorch(torchOn) },
                )
            }
        }
    }

    if (showMatches) PossibleMatchesSheet(
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

@Composable
private fun ScanOverlay(
    recognizedLines: List<String>, identifying: Boolean, isAI: Boolean,
    cameraReady: Boolean, onIdentify: () -> Unit,
) {
    val c = DS.colors
    val transition = rememberInfiniteTransition(label = "scanLine")
    val linePhase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "line")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frameWidth = maxWidth - 72.dp
        val frameHeight = frameWidth * 1.25f
        // Dim everything except the label frame.
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color.Black.copy(.45f))
            val w = frameWidth.toPx(); val h = frameHeight.toPx()
            drawRoundRect(
                Color.Transparent,
                topLeft = Offset((size.width - w) / 2, (size.height - h) / 2),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()),
                blendMode = BlendMode.Clear,
            )
        }
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.scan_position),
                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(.45f))
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.size(frameWidth, frameHeight)
                    .clip(RoundedCornerShape(28.dp))
                    .border(3.dp, Color.White.copy(.9f), RoundedCornerShape(28.dp)),
            ) {
                // Animated scanning line.
                Box(
                    Modifier.align(Alignment.TopCenter)
                        .offset(y = 32.dp + (frameHeight - 64.dp) * linePhase)
                        .width(frameWidth - 44.dp).height(3.dp)
                        .clip(RoundedCornerShape(2.dp)).background(c.gradient),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(if (recognizedLines.isEmpty()) R.string.scan_looking else R.string.scan_reading),
                color = Color.White.copy(.85f), fontSize = 13.sp,
            )
            if (isAI) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = c.mint, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.scan_ai_checks), color = Color.White.copy(.76f), fontSize = 11.5.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                stringResource(if (identifying) R.string.scan_identifying else if (isAI) R.string.scan_identify_ai else R.string.scan_identify),
                onIdentify,
                Modifier.padding(horizontal = 80.dp).fillMaxWidth(),
                enabled = !identifying && cameraReady && (isAI || recognizedLines.isNotEmpty()),
                leading = if (isAI) Icons.Default.AutoAwesome else Icons.Default.Search,
            )
        }
    }
}

@Composable
private fun AIProcessingOverlay(stage: Int, isAI: Boolean) {
    val c = DS.colors
    val transition = rememberInfiniteTransition(label = "aiProcessing")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(2600)), label = "spin")
    val labels = listOf(
        stringResource(R.string.scan_stage_capture),
        stringResource(R.string.scan_stage_read),
        stringResource(R.string.scan_stage_check),
    )
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(.58f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xEF14211F),
            shape = RoundedCornerShape(30.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(.14f)),
            modifier = Modifier.padding(horizontal = 34.dp).fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(horizontal = 28.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(118.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { .72f }, color = c.mint, trackColor = Color.White.copy(.10f),
                        strokeWidth = 5.dp, modifier = Modifier.size(92.dp).graphicsLayer(rotationZ = spin),
                    )
                    Icon(
                        if (isAI) Icons.Default.AutoAwesome else Icons.Default.DocumentScanner,
                        null, tint = c.mint, modifier = Modifier.size(34.dp),
                    )
                }
                Text(
                    stringResource(if (isAI) R.string.scan_ai_identifying else R.string.scan_local_identifying),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp,
                )
                Spacer(Modifier.height(7.dp))
                Text(labels[stage.coerceIn(0, 2)], color = Color.White.copy(.72f), fontSize = 13.5.sp)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    repeat(3) { index ->
                        Box(
                            Modifier.width(if (index == stage) 28.dp else 8.dp).height(8.dp)
                                .clip(RoundedCornerShape(50)).background(if (index <= stage) c.mint else Color.White.copy(.18f)),
                        )
                    }
                }
            }
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

// MARK: - Possible Matches sheet

@Composable
private fun PossibleMatchesSheet(
    matches: List<ScanCandidate>, searchedOnline: Boolean,
    isAIResult: Boolean, aiRemaining: Int?, errorMessage: String?,
    onPick: (ScanCandidate) -> Unit, onTryAgain: () -> Unit, onDismiss: () -> Unit,
) {
    val c = DS.colors
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = c.bg2,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = { SheetDragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 26.dp)) {
            Icon(
                if (matches.isEmpty()) Icons.Default.CenterFocusWeak else Icons.Default.Verified,
                null, tint = if (matches.isEmpty()) c.ink3 else c.mint,
                modifier = Modifier.size(30.dp).align(Alignment.CenterHorizontally).appearFluidly(0),
            )
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(if (matches.isEmpty()) R.string.scan_no_confident else R.string.scan_confirm_title),
                style = MaterialTheme.typography.headlineMedium, color = c.ink,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().appearFluidly(0),
            )
            Text(
                stringResource(if (isAIResult) R.string.scan_ai_verify else R.string.scan_verify),
                color = c.ink2, fontSize = 12.5.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            if (matches.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
                Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    matches.forEachIndexed { index, candidate ->
                        GlassCard(
                            Modifier.fillMaxWidth().appearFluidly(1 + index), radius = 20.dp,
                            onClick = { onPick(candidate) }, contentPadding = PaddingValues(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconTile(formIcon(candidate.form ?: MedicationForm.other), c.mint)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.name, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(
                                        listOfNotNull(
                                            candidate.strengthText, candidate.form?.title(context),
                                            stringResource(when (candidate.source) {
                                                ScanCandidate.Source.CATALOG -> R.string.scan_source_catalog
                                                ScanCandidate.Source.RXNORM -> R.string.scan_source_rxnorm
                                                ScanCandidate.Source.AI -> R.string.scan_source_ai
                                            }),
                                        ).joinToString(" · "),
                                        color = c.ink2, fontSize = 13.sp,
                                    )
                                    candidate.detail?.let {
                                        Text(stringResource(R.string.scan_matched_alias, it), color = c.ink3, fontSize = 11.5.sp)
                                    }
                                    candidate.category?.let {
                                        Text(it.uppercase(), color = c.cyan, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                                    }
                                    candidate.note?.let {
                                        Text(it, color = c.ink2, fontSize = 12.sp)
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = c.ink3)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            errorMessage?.let {
                Text(it, color = c.coral, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            } ?: run {
                if (isAIResult && aiRemaining != null) {
                    Text(
                        stringResource(R.string.scan_ai_remaining, aiRemaining),
                        color = c.ink3, fontSize = 11.5.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            GhostButton(stringResource(R.string.scan_try_again), onTryAgain, Modifier.fillMaxWidth())
        }
    }
}
