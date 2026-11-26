@file:OptIn(ExperimentalTextApi::class)

package com.monmontage.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.monmontage.app.R
import com.monmontage.app.model.ClipType

/** Palette de la maquette : thème sombre d'éditeur vidéo, accent bleu vif. */
object AppColors {
    val Background = Color(0xFF0E1116)
    val Surface = Color(0xFF181C24)
    val SurfaceHigh = Color(0xFF1F2A40)
    val Track = Color(0xFF262C37)
    val Field = Color(0xFF12161D)
    val Outline = Color(0xFF3A4250)
    val OnSurface = Color(0xFFF2F4F8)
    val Muted = Color(0xFFA3ACBA)
    val Accent = Color(0xFF6F9BFF)
    val OnAccent = Color(0xFF0B1426)
    val Link = Color(0xFF8FB4FF)
    val Kept = Color(0xFF3C5C99)
    val Success = Color(0xFF7FD1B9)
    val Music = Color(0xFFF2C48D)
    val MusicBg = Color(0xFF3A2C1A)
    val TitleBg = Color(0xFF22324F)

    /** Couleur de fond / texte de l'étiquette de chaque type de média. */
    fun tagColors(type: ClipType): Pair<Color, Color> = when (type) {
        ClipType.PHOTO -> Color(0xFF3A2C1A) to Color(0xFFF2C48D)
        ClipType.ILLUSTRATION -> Color(0xFF8FB4FF) to Color(0xFF0B1426)
        ClipType.INTERVIEW -> Color(0xFFFF9F6E) to Color(0xFF2A1206)
    }

    /** Couleur du bloc de chaque type dans les frises (schéma musique). */
    fun blockColor(type: ClipType?): Color = when (type) {
        null -> TitleBg
        ClipType.PHOTO -> MusicBg
        ClipType.ILLUSTRATION -> Color(0xFF2A3550)
        ClipType.INTERVIEW -> Color(0xFF5A3422)
    }
}

private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Inter = FontFamily(
    variable(R.font.inter_variable, 400),
    variable(R.font.inter_variable, 500),
    variable(R.font.inter_variable, 600),
    variable(R.font.inter_variable, 700),
)

val SpaceGrotesk = FontFamily(
    variable(R.font.space_grotesk_variable, 500),
    variable(R.font.space_grotesk_variable, 600),
    variable(R.font.space_grotesk_variable, 700),
)

private val AppTypography = Typography().let { t ->
    fun TextStyle.inter() = copy(fontFamily = Inter)
    fun TextStyle.grotesk(w: Int) = copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight(w))
    Typography(
        displayLarge = t.displayLarge.grotesk(700),
        displayMedium = t.displayMedium.grotesk(700),
        displaySmall = t.displaySmall.grotesk(700),
        headlineLarge = t.headlineLarge.grotesk(700),
        headlineMedium = t.headlineMedium.grotesk(700),
        headlineSmall = t.headlineSmall.grotesk(600),
        titleLarge = t.titleLarge.grotesk(600).copy(fontSize = 20.sp),
        titleMedium = t.titleMedium.grotesk(600).copy(fontSize = 17.sp),
        titleSmall = t.titleSmall.inter().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.inter(),
        bodyMedium = t.bodyMedium.inter(),
        bodySmall = t.bodySmall.inter().copy(lineHeight = 18.sp),
        labelLarge = t.labelLarge.inter().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.inter(),
        labelSmall = t.labelSmall.inter(),
    )
}

private val AppScheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.OnAccent,
    primaryContainer = Color(0xFF2A3550),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = AppColors.Kept,
    onSecondary = AppColors.OnSurface,
    secondaryContainer = AppColors.SurfaceHigh,
    onSecondaryContainer = AppColors.OnSurface,
    tertiary = Color(0xFFFF9F6E),
    onTertiary = Color(0xFF2A1206),
    background = AppColors.Background,
    onBackground = AppColors.OnSurface,
    surface = AppColors.Background,
    onSurface = AppColors.OnSurface,
    surfaceVariant = AppColors.Track,
    onSurfaceVariant = AppColors.Muted,
    surfaceContainerLowest = AppColors.Field,
    surfaceContainerLow = AppColors.Surface,
    surfaceContainer = AppColors.Surface,
    surfaceContainerHigh = AppColors.Surface,
    surfaceContainerHighest = AppColors.SurfaceHigh,
    outline = AppColors.Outline,
    outlineVariant = Color(0xFF2C3340),
    error = Color(0xFFFF8A80),
)

/** Thème unique (sombre), comme sur la maquette, quel que soit le réglage du téléphone. */
@Composable
fun MonMontageTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppScheme, typography = AppTypography, content = content)
}
