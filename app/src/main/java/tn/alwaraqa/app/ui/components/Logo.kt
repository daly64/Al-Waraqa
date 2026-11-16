package tn.alwaraqa.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Le logo : une feuille au coin plié portant un W.
 * Dessiné dans un repère de 252 × 348 (même tracé que l'icône).
 */
@Composable
fun WaraqaLogo(
    height: Dp,
    page: Color,
    fold: Color,
    letter: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.size(width = height * (252f / 348f), height = height)) {
        val s = size.height / 348f
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            translate(left = -130f, top = -84f) {
                val sheet = Path().apply {
                    moveTo(158f, 92f)
                    lineTo(290f, 92f)
                    lineTo(374f, 176f)
                    lineTo(374f, 404f)
                    cubicTo(374f, 415f, 365f, 424f, 354f, 424f)
                    lineTo(158f, 424f)
                    cubicTo(147f, 424f, 138f, 415f, 138f, 404f)
                    lineTo(138f, 112f)
                    cubicTo(138f, 101f, 147f, 92f, 158f, 92f)
                    close()
                }
                drawPath(sheet, page)
                val corner = Path().apply {
                    moveTo(290f, 92f)
                    lineTo(374f, 176f)
                    lineTo(310f, 176f)
                    cubicTo(299f, 176f, 290f, 167f, 290f, 156f)
                    close()
                }
                drawPath(corner, fold)
                val w = Path().apply {
                    moveTo(184f, 262f)
                    lineTo(214f, 356f)
                    lineTo(256f, 286f)
                    lineTo(298f, 356f)
                    lineTo(328f, 262f)
                }
                drawPath(
                    w,
                    letter,
                    style = Stroke(width = 28f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

@Composable
fun WaraqaLogoSmall(modifier: Modifier = Modifier) {
    WaraqaLogo(
        height = 30.dp,
        page = Color(0xFFA63A24),
        fold = Color(0xFF7A2817),
        letter = Color(0xFFFFFBF3),
        modifier = modifier
    )
}
