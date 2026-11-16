package tn.alwaraqa.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import tn.alwaraqa.app.R

// Couleurs de l'identité Al Waraqa
val Brick = Color(0xFFA63A24)
val Terracotta = Color(0xFF7A2817)
val Sand = Color(0xFFE4C9A0)
val SandSoft = Color(0xFFEFE0C6)
val Paper = Color(0xFFFFFBF3)
val Cream = Color(0xFFF6EEDF)
val Ink = Color(0xFF2A1D17)
val InkSoft = Color(0xFF6B5547)
val ReaderGround = Color(0xFFEADCC4)

/** Bricolage Grotesque (police variable, licence OFL), embarquée dans l'app. */
val Heading = FontFamily(
    Font(R.font.bricolage_grotesque, weight = FontWeight.ExtraBold),
    Font(R.font.bricolage_grotesque, weight = FontWeight.Bold)
)

/** Aref Ruqaa (licence OFL) pour le nom en arabe. */
val Ruqaa = FontFamily(Font(R.font.aref_ruqaa_bold, FontWeight.Bold))

private val Colors = lightColorScheme(
    primary = Brick,
    onPrimary = Color.White,
    secondary = Terracotta,
    onSecondary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = SandSoft,
    onSurfaceVariant = InkSoft,
    outline = Sand
)

@Composable
fun AlWaraqaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
