package com.kabi.pillpal.meditick.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kabi.pillpal.meditick.data.AccentId
import com.kabi.pillpal.meditick.data.AppearanceMode
import com.kabi.pillpal.meditick.data.SettingsStore

@Immutable
data class MediTickColors(
    val bg: Color, val bg2: Color, val bg3: Color,
    val glass: Color, val glass2: Color, val glass3: Color,
    val line: Color, val line2: Color,
    val ink: Color, val ink2: Color, val ink3: Color,
    val mint: Color, val mint2: Color, val cyan: Color,
    val gradStart: Color, val gradEnd: Color, val onMint: Color, val glow: Color,
    val violet: Color, val amber: Color, val coral: Color,
    val dockBg: Color, val ringTrack: Color, val cardShadow: Color, val isDark: Boolean,
) {
    val gradient get() = Brush.linearGradient(listOf(gradStart, gradEnd))
    val verticalGradient get() = Brush.verticalGradient(listOf(gradStart, gradEnd))
}

private data class AccentTones(
    val mint: Long, val mint2: Long, val cyan: Long, val g1: Long, val g2: Long,
    val onMint: Long, val glow: Long,
)

private val LocalColors = staticCompositionLocalOf { palette(AccentId.AURORA, true) }
object DS { val colors: MediTickColors @Composable get() = LocalColors.current }

private val DisplayFamily = FontFamily.SansSerif

@Composable
fun MediTickTheme(settings: SettingsStore, content: @Composable () -> Unit) {
    val dark = when (settings.appearance) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    val colors = palette(settings.accent, dark)
    val scheme = if (dark) darkColorScheme(
        primary = colors.mint, onPrimary = colors.onMint, secondary = colors.cyan,
        background = colors.bg, surface = colors.bg3, onSurface = colors.ink,
        surfaceVariant = colors.glass2, onSurfaceVariant = colors.ink2,
        error = colors.coral,
    ) else lightColorScheme(
        primary = colors.mint, onPrimary = colors.onMint, secondary = colors.cyan,
        background = colors.bg, surface = colors.bg3, onSurface = colors.ink,
        surfaceVariant = colors.glass2, onSurfaceVariant = colors.ink2,
        error = colors.coral,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = androidx.compose.material3.Typography(
                displayLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 43.sp),
                headlineLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
                headlineMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
                titleLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp),
                titleMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
                bodyLarge = TextStyle(fontFamily = DisplayFamily, fontSize = 15.sp, lineHeight = 21.sp),
                bodyMedium = TextStyle(fontFamily = DisplayFamily, fontSize = 13.sp, lineHeight = 18.sp),
                bodySmall = TextStyle(fontFamily = DisplayFamily, fontSize = 12.sp, lineHeight = 16.sp),
                labelLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                labelSmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
            ),
            content = content,
        )
    }
}

private fun palette(accent: AccentId, dark: Boolean): MediTickColors {
    val tones = when (accent) {
        AccentId.AURORA -> if (dark) AccentTones(0xFF7DF0B4, 0xFF3ED598, 0xFF59D5FF, 0xFF8CF5BE, 0xFF54D8F5, 0xFF062015, 0xFF54D8B4)
            else AccentTones(0xFF0B9A63, 0xFF078A54, 0xFF0784B5, 0xFF0CAE72, 0xFF068FBE, 0xFFFFFFFF, 0xFF0CA470)
        AccentId.OCEAN -> if (dark) AccentTones(0xFF74C8FF, 0xFF429FEF, 0xFF97A3FF, 0xFF7ECDFF, 0xFF94A0FF, 0xFF051A2C, 0xFF5FB4F5)
            else AccentTones(0xFF0A6FC2, 0xFF085CA4, 0xFF4C55D4, 0xFF0E7FD6, 0xFF5560DE, 0xFFFFFFFF, 0xFF2E7CD4)
        AccentId.ORCHID -> if (dark) AccentTones(0xFFCBA9FF, 0xFFA87DF5, 0xFFFF9CD8, 0xFFCDABFF, 0xFFFF9CD8, 0xFF240A3C, 0xFFC79FF2)
            else AccentTones(0xFF7A3BE0, 0xFF672BC4, 0xFFC0288C, 0xFF8244E6, 0xFFC93A96, 0xFFFFFFFF, 0xFF9C50E0)
        AccentId.EMBER -> if (dark) AccentTones(0xFFFFC077, 0xFFFF9455, 0xFFFF8C9F, 0xFFFFC670, 0xFFFF8577, 0xFF2B1305, 0xFFFFA26E)
            else AccentTones(0xFFCE5F14, 0xFFB24E0E, 0xFFD33556, 0xFFDE6E17, 0xFFD8434F, 0xFFFFFFFF, 0xFFD96F2E)
    }
    fun c(value: Long) = Color(value)
    return MediTickColors(
        bg = c(if (dark) 0xFF0B1311 else 0xFFF1F5F0), bg2 = c(if (dark) 0xFF101B16 else 0xFFFAFCF9),
        bg3 = c(if (dark) 0xFF15231D else 0xFFFFFFFF),
        glass = if (dark) Color.White.copy(alpha = .05f) else Color.White.copy(alpha = .74f),
        glass2 = if (dark) Color.White.copy(alpha = .09f) else c(0x0F102017),
        glass3 = if (dark) Color.White.copy(alpha = .13f) else c(0x1C102017),
        line = if (dark) Color.White.copy(alpha = .09f) else c(0x1A102017),
        line2 = if (dark) Color.White.copy(alpha = .17f) else c(0x2E102017),
        ink = c(if (dark) 0xFFF1F7F2 else 0xFF121D16), ink2 = c(if (dark) 0xFFA9BCB1 else 0xFF586C60),
        ink3 = c(if (dark) 0xFF61756A else 0xFF8FA396),
        mint = c(tones.mint), mint2 = c(tones.mint2), cyan = c(tones.cyan),
        gradStart = c(tones.g1), gradEnd = c(tones.g2), onMint = c(tones.onMint), glow = c(tones.glow),
        violet = c(if (dark) 0xFFB9A6FF else 0xFF7C55E8), amber = c(if (dark) 0xFFFFC670 else 0xFFC8820E),
        coral = c(if (dark) 0xFFFF8577 else 0xFFE0574B),
        dockBg = c(if (dark) 0xED0E1713 else 0xF2FFFFFF), ringTrack = if (dark) Color.White.copy(alpha = .08f) else c(0x17102017),
        // Invisible in Midnight, a soft green-gray lift in Daylight — same
        // rule as the iOS card shadow.
        cardShadow = if (dark) Color.Transparent else c(0x59102017),
        isDark = dark,
    )
}
