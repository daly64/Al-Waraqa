package tn.alwaraqa.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tn.alwaraqa.app.ui.theme.Brick
import tn.alwaraqa.app.ui.theme.Ink
import tn.alwaraqa.app.ui.theme.ReaderGround

/** Petites icônes dessinées dans un repère de 24 × 24, au trait de 2. */
@Composable
fun BookmarkGlyph(tint: Color, filled: Boolean, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val path = Path().apply {
            moveTo(7f * u, 4f * u)
            lineTo(17f * u, 4f * u)
            lineTo(17f * u, 21f * u)
            lineTo(12f * u, 17f * u)
            lineTo(7f * u, 21f * u)
            close()
        }
        if (filled) drawPath(path, tint)
        drawPath(path, tint, style = Stroke(width = 2f * u, join = StrokeJoin.Round))
    }
}

@Composable
fun DocGlyph(tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val sheet = Path().apply {
            moveTo(6f * u, 3f * u)
            lineTo(14f * u, 3f * u)
            lineTo(18f * u, 7f * u)
            lineTo(18f * u, 21f * u)
            lineTo(6f * u, 21f * u)
            close()
        }
        drawPath(sheet, tint, style = Stroke(width = 2f * u, join = StrokeJoin.Round))
        val fold = Path().apply {
            moveTo(14f * u, 3f * u)
            lineTo(14f * u, 7f * u)
            lineTo(18f * u, 7f * u)
        }
        drawPath(fold, tint, style = Stroke(width = 2f * u, join = StrokeJoin.Round))
    }
}

@Composable
fun SlidersGlyph(tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val stroke = 2f * u
        fun line(x1: Float, y: Float, x2: Float) =
            drawLine(tint, Offset(x1 * u, y * u), Offset(x2 * u, y * u), strokeWidth = stroke, cap = StrokeCap.Round)
        line(4f, 7f, 14f)
        line(18f, 7f, 20f)
        line(4f, 17f, 8f)
        line(12f, 17f, 20f)
        drawCircle(tint, radius = 2f * u, center = Offset(16f * u, 7f * u), style = Stroke(stroke))
        drawCircle(tint, radius = 2f * u, center = Offset(10f * u, 17f * u), style = Stroke(stroke))
    }
}

/** Choix entre quelques options côte à côte. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ReaderGround)
            .padding(4.dp)
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Brick else Color.Transparent)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label(option),
                    color = if (active) Color.White else Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
