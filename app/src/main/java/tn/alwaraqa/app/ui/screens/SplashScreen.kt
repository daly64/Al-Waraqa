package tn.alwaraqa.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tn.alwaraqa.app.ui.components.WaraqaLogo
import tn.alwaraqa.app.ui.theme.Brick
import tn.alwaraqa.app.ui.theme.Paper
import tn.alwaraqa.app.ui.theme.Ruqaa
import tn.alwaraqa.app.ui.theme.Sand

/** Splash simple : le logo et le nom en arabe, sur fond brique. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(450))
        delay(900)
        onFinished()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.graphicsLayer { this.alpha = alpha.value }
        ) {
            WaraqaLogo(height = 144.dp, page = Paper, fold = Sand, letter = Brick)
            Text(
                "الورقة",
                color = Paper,
                fontFamily = Ruqaa,
                fontWeight = FontWeight.Bold,
                fontSize = 64.sp
            )
        }
    }
}
