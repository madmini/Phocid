package org.sunsetware.phocid.data

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.ibm.icu.text.Collator
import com.ibm.icu.text.RuleBasedCollator
import java.util.Locale
import kotlin.math.PI
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.NonNls
import org.sunsetware.phocid.R
import org.sunsetware.phocid.ui.theme.GRAY
import org.sunsetware.phocid.ui.theme.Oklch
import org.sunsetware.phocid.ui.theme.hashColor
import org.sunsetware.phocid.ui.theme.toOklch
import org.sunsetware.phocid.ui.views.TabInfo
import org.sunsetware.phocid.ui.views.TabType

@Volatile var preferencesSystemLocale = Locale.getDefault()

/**
 * Changes to this class should not change types of existing members, and new members must have a
 * default value, or else the user will have their preferences wiped after an app update.
 */
@Immutable
@Serializable
data class Preferences(
    // Interface
    val tabSettings: Map<TabType, TabInfo> = TabType.entries.associateWith { TabInfo(it) },
    val tabOrderAndVisibility: List<Pair<TabType, Boolean>> = TabType.entries.map { it to true },
    val tabStyle: TabStylePreference = TabStylePreference.TEXT_ONLY,
    val scrollableTabs: Boolean = true,
    val sortingLocaleLanguageTag: String? = null,
    val lyricsDisplay: LyricsDisplayPreference = LyricsDisplayPreference.DEFAULT,
    val densityMultiplier: Float = 1f,
    // Playback
    val playOnOutputDeviceConnection: Boolean = false,
    // Theme
    val darkTheme: DarkThemePreference = DarkThemePreference.SYSTEM,
    val themeColorSource: ThemeColorSource = ThemeColorSource.MATERIAL_YOU,
    val customThemeColor: CustomThemeColor = CustomThemeColor(50, 0),
    val pureBackgroundColor: Boolean = false,
    val coloredCards: Boolean = true,
    val coloredPlayer: Boolean = true,
    val artworkColorPreference: ArtworkColorPreference = ArtworkColorPreference.MUTED_FIRST,
    val shapePreference: ShapePreference = ShapePreference.SQUARE,
    // Indexing
    val advancedMetadataExtraction: Boolean = false,
    val disableArtworkColorExtraction: Boolean = false,
    @NonNls
    val artistMetadataSeparators: List<String> = listOf("&", ";", ",", "+", "/", " feat.", " ft."),
    val artistMetadataSeparatorExceptions: List<String> = emptyList(),
    val genreMetadataSeparators: List<String> = listOf("&", ";", ",", "+", "/"),
    @NonNls
    val genreMetadataSeparatorExceptions: List<String> =
        listOf("R&B", "Rhythm & Blues", "D&B", "Drum & Bass"),
    val blacklist: List<String> = emptyList(),
    val whitelist: List<String> = emptyList(),
    // Data
    val charsetName: String? = null,
    val playlistIoSettings: PlaylistIoSettings = PlaylistIoSettings(),
) {
    fun upgrade(): Preferences {
        val newTabSettings =
            tabSettings.filterKeys { TabType.entries.contains(it) } +
                TabType.entries.toSet().minus(tabSettings.keys).associateWith { TabInfo(it) }
        val newTabOrderAndVisibility =
            tabOrderAndVisibility
                .filter { TabType.entries.contains(it.first) }
                .distinctBy { it.first } +
                TabType.entries.toSet().minus(tabOrderAndVisibility.map { it.first }.toSet()).map {
                    it to false
                }
        return copy(tabSettings = newTabSettings, tabOrderAndVisibility = newTabOrderAndVisibility)
    }

    @Transient
    val blacklistRegexes =
        blacklist.mapNotNull {
            try {
                Regex(it, RegexOption.IGNORE_CASE)
            } catch (_: Exception) {
                null
            }
        }

    @Transient
    val whitelistRegexes =
        whitelist.mapNotNull {
            try {
                Regex(it, RegexOption.IGNORE_CASE)
            } catch (_: Exception) {
                null
            }
        }

    @Transient
    val tabs =
        tabOrderAndVisibility
            .filter { it.second }
            .map { requireNotNull(tabSettings[it.first]) }
            .takeIf { it.isNotEmpty() } ?: listOf(tabSettings[TabType.TRACKS]!!)

    @Transient val sortingLocale = sortingLocaleLanguageTag?.let { Locale.forLanguageTag(it) }

    @Transient
    val sortCollator =
        (if (sortingLocale != null) Collator.getInstance(sortingLocale)
            else Collator.getInstance(preferencesSystemLocale))
            .apply {
                this.strength = Collator.PRIMARY
                (this as RuleBasedCollator).numericCollation = true
            }
            .freeze() as RuleBasedCollator

    @Transient
    val searchCollator =
        (if (sortingLocale != null) Collator.getInstance(sortingLocale)
            else Collator.getInstance(preferencesSystemLocale))
            .apply { this.strength = Collator.PRIMARY }
            .freeze() as RuleBasedCollator
}

@Serializable
enum class TabStylePreference(val stringId: Int) {
    TEXT_AND_ICON(R.string.preferences_tab_style_text_and_icon),
    TEXT_ONLY(R.string.preferences_tab_style_text_only),
    ICON_ONLY(R.string.preferences_tab_style_icon_only),
}

@Serializable
enum class DarkThemePreference(val stringId: Int, val boolean: Boolean?) {
    SYSTEM(R.string.preferences_dark_theme_system, null),
    DARK(R.string.preferences_dark_theme_dark, true),
    LIGHT(R.string.preferences_dark_theme_light, false),
}

@Serializable
enum class ThemeColorSource(val stringId: Int) {
    DEFAULT(R.string.preferences_theme_color_source_default),
    MATERIAL_YOU(R.string.preferences_theme_color_source_material_you),
    CUSTOM(R.string.preferences_theme_color_source_custom),
}

@Serializable
data class CustomThemeColor(val chromaPercentage: Int, val hueDegrees: Int) {
    fun toColor(lightness: Float): Color {
        return Oklch(lightness, chromaPercentage / 100f * 0.4f, hueDegrees / 180f * PI.toFloat())
            .toColor()
    }
}

@Serializable
enum class ArtworkColorPreference(val stringId: Int) {
    VIBRANT_FIRST(R.string.preferences_artwork_color_vibrant_first),
    MUTED_FIRST(R.string.preferences_artwork_color_muted_first),
    MUTED_ONLY(R.string.preferences_artwork_color_muted_only),
}

@Serializable
enum class ShapePreference(val stringId: Int, val artworkShape: Shape, val cardShape: Shape) {
    SQUARE(R.string.preferences_shape_square, RoundedCornerShape(0.dp), RoundedCornerShape(0.dp)),
    ROUNDED_SQUARE(
        R.string.preferences_shape_rounded_square,
        RoundedCornerShape(8.dp),
        RoundedCornerShape(12.dp),
    ),
    CIRCLE(R.string.preferences_shape_circle, CircleShape, RoundedCornerShape(12.dp)),
}

@Stable
fun Track.getArtworkColor(preference: ArtworkColorPreference): Color {
    return if (this === InvalidTrack) GRAY
    else
        when (preference) {
            ArtworkColorPreference.VIBRANT_FIRST ->
                vibrantColor ?: mutedColor ?: if (hasArtwork) GRAY else fileName.hashColor()
            ArtworkColorPreference.MUTED_FIRST ->
                mutedColor ?: vibrantColor ?: if (hasArtwork) GRAY else fileName.hashColor()
            ArtworkColorPreference.MUTED_ONLY ->
                mutedColor
                    ?: vibrantColor?.toOklch()?.copy(c = 0.05f)?.toColor()
                    ?: if (hasArtwork) GRAY else fileName.hashColor()
        }
}

@Serializable
enum class LyricsDisplayPreference(val stringId: Int) {
    DISABLED(R.string.preferences_lyrics_display_disabled),
    DEFAULT(R.string.preferences_lyrics_display_default),
    TWO_LINES(R.string.preferences_lyrics_display_two_lines),
}
