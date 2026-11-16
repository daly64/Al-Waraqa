package tn.alwaraqa.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tn.alwaraqa.app.ui.components.WaraqaLogoSmall
import tn.alwaraqa.app.ui.theme.Brick
import tn.alwaraqa.app.ui.theme.Cream
import tn.alwaraqa.app.ui.theme.Heading
import tn.alwaraqa.app.ui.theme.Ink
import tn.alwaraqa.app.ui.theme.InkSoft
import tn.alwaraqa.app.ui.theme.Paper
import tn.alwaraqa.app.ui.theme.Sand
import tn.alwaraqa.app.ui.theme.SandSoft
import tn.alwaraqa.app.ui.theme.Terracotta

private data class Welcome(val title: String, val text: String)

private val Steps = listOf(
    Welcome(
        "Tous vos PDF, au même endroit",
        "Al Waraqa retrouve les PDF de votre téléphone et les ouvre en un geste, même depuis une autre application."
    ),
    Welcome(
        "Tournez les pages comme un livre",
        "La page suit votre doigt et se replie comme du vrai papier."
    ),
    Welcome(
        "Reprenez là où vous étiez",
        "Chaque document se rouvre à la dernière page lue."
    )
)

/** Les écrans de bienvenue du premier lancement. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState(pageCount = { Steps.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == Steps.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(Cream)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 32.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WaraqaLogoSmall()
            Spacer(Modifier.width(10.dp))
            Text(
                "Al Waraqa",
                color = Ink,
                fontFamily = Heading,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
            if (!last) {
                TextButton(onClick = onDone) {
                    Text("Passer", color = Brick, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(280.dp)) { drawIllustration(page) }
                }
                Text(
                    Steps[page].title,
                    color = Ink,
                    fontFamily = Heading,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 34.sp,
                    lineHeight = 38.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    Steps[page].text,
                    color = InkSoft,
                    fontSize = 17.sp,
                    lineHeight = 25.sp
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Steps.indices.forEach { i ->
                val active = i == pager.currentPage
                Box(
                    Modifier
                        .size(width = if (active) 28.dp else 8.dp, height = 8.dp)
                        .background(if (active) Brick else Color(0xFFD9BF98), RoundedCornerShape(4.dp))
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (last) onDone()
                else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(if (last) "Commencer" else "Suivant", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Une feuille au coin plié, dans un repère de 300 × 300. */
private fun DrawScope.sheet(color: Color, fold: Color) {
    val page = Path().apply {
        moveTo(94f, 58f)
        lineTo(178f, 58f)
        lineTo(218f, 98f)
        lineTo(218f, 230f)
        cubicTo(218f, 237f, 213f, 242f, 206f, 242f)
        lineTo(94f, 242f)
        cubicTo(87f, 242f, 82f, 237f, 82f, 230f)
        lineTo(82f, 70f)
        cubicTo(82f, 63f, 87f, 58f, 94f, 58f)
        close()
    }
    drawPath(page, color)
    val corner = Path().apply {
        moveTo(178f, 58f)
        lineTo(218f, 98f)
        lineTo(190f, 98f)
        cubicTo(183f, 98f, 178f, 93f, 178f, 86f)
        close()
    }
    drawPath(corner, fold)
}

private fun DrawScope.line(x: Float, y: Float, width: Float, color: Color, height: Float = 6f) {
    drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(height / 2f))
}

private fun DrawScope.drawIllustration(page: Int) {
    val s = size.width / 300f
    scale(s, s, pivot = Offset.Zero) {
        drawCircle(SandSoft, radius = 140f, center = Offset(150f, 150f))
        when (page) {
            0 -> {
                // Une pile de feuilles
                rotate(-14f, Offset(150f, 160f)) {
                    drawRoundRect(Sand, Offset(82f, 62f), Size(136f, 180f), CornerRadius(12f))
                }
                rotate(10f, Offset(150f, 160f)) {
                    drawRoundRect(Terracotta, Offset(82f, 62f), Size(136f, 180f), CornerRadius(12f))
                }
                sheet(Paper, Sand)
                line(102f, 124f, 96f, Brick, 8f)
                line(102f, 148f, 96f, Sand)
                line(102f, 166f, 96f, Sand)
                line(102f, 184f, 60f, Sand)
            }

            1 -> {
                // Une page dont le coin se soulève
                drawRoundRect(Paper, Offset(82f, 58f), Size(136f, 184f), CornerRadius(12f))
                line(102f, 96f, 70f, Brick, 8f)
                line(102f, 120f, 96f, Sand)
                line(102f, 138f, 96f, Sand)
                line(102f, 156f, 96f, Sand)
                line(102f, 174f, 50f, Sand)
                val under = Path().apply {
                    moveTo(218f, 150f)
                    lineTo(218f, 242f)
                    lineTo(126f, 242f)
                    close()
                }
                drawPath(under, Terracotta)
                val curl = Path().apply {
                    moveTo(218f, 150f)
                    lineTo(126f, 242f)
                    lineTo(132f, 164f)
                    close()
                }
                drawPath(curl, Sand)
            }

            else -> {
                // Une feuille et son marque-page
                sheet(Paper, Sand)
                line(102f, 136f, 70f, Sand)
                line(102f, 154f, 96f, Sand)
                line(102f, 172f, 96f, Sand)
                line(102f, 190f, 54f, Sand)
                val ribbon = Path().apply {
                    moveTo(112f, 50f)
                    lineTo(144f, 50f)
                    lineTo(144f, 124f)
                    lineTo(128f, 110f)
                    lineTo(112f, 124f)
                    close()
                }
                drawPath(ribbon, Brick)
                line(102f, 214f, 96f, Sand, 8f)
                line(102f, 214f, 58f, Brick, 8f)
            }
        }
    }
}
