package tn.alwaraqa.app.ui.reader

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import tn.alwaraqa.app.data.ReadingTheme

/** L'apparence du lecteur pour un thème : couleur du papier, filtre des pages, couleurs des barres. */
class ReadingLook(
    val paper: Color,
    val filter: ColorFilter?,
    val ground: Color,
    val bar: Color,
    val text: Color,
    val textSoft: Color,
    val accent: Color,
    val line: Color,
    /** Vrai : les pages sont recalculées en mode Nuit (texte inversé, images préservées). */
    val nightPages: Boolean = false
)

private val NightPaper = Color(0xFF1B1410)

private val SepiaPaper = Color(0xFFF4E7CE)

fun lookFor(theme: ReadingTheme): ReadingLook = when (theme) {
    ReadingTheme.Clair -> ReadingLook(
        paper = Color.White,
        filter = null,
        ground = Color(0xFFEADCC4),
        bar = Color(0xFFFFFBF3),
        text = Color(0xFF2A1D17),
        textSoft = Color(0xFF6B5547),
        accent = Color(0xFFA63A24),
        line = Color(0xFFE4C9A0)
    )

    ReadingTheme.Sepia -> ReadingLook(
        paper = SepiaPaper,
        filter = ColorFilter.tint(SepiaPaper, BlendMode.Multiply),
        ground = Color(0xFFE2D2B4),
        bar = Color(0xFFF8EEDA),
        text = Color(0xFF2A1D17),
        textSoft = Color(0xFF6B5547),
        accent = Color(0xFFA63A24),
        line = Color(0xFFDCC59C)
    )

    ReadingTheme.Nuit -> ReadingLook(
        paper = NightPaper,
        filter = null,
        ground = Color(0xFF120C09),
        bar = Color(0xFF241A15),
        text = Color(0xFFF6EEDF),
        textSoft = Color(0xFFC9B8A6),
        accent = Color(0xFFE89077),
        line = Color(0xFF3A2A22),
        nightPages = true
    )
}
