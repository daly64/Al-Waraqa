package tn.alwaraqa.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tn.alwaraqa.app.data.Library
import tn.alwaraqa.app.data.RecentDoc
import tn.alwaraqa.app.pdf.PdfDoc
import tn.alwaraqa.app.ui.reader.BookPager
import tn.alwaraqa.app.ui.theme.Brick
import tn.alwaraqa.app.ui.theme.Ink
import tn.alwaraqa.app.ui.theme.InkSoft
import tn.alwaraqa.app.ui.theme.Paper
import tn.alwaraqa.app.ui.theme.ReaderGround
import tn.alwaraqa.app.ui.theme.Sand
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Le lecteur en mode Livre, en plein écran : la page occupe tout l'écran,
 * un appui au centre affiche ou masque les barres.
 */
@OptIn(DelicateCoroutinesApi::class)
@Composable
fun ReaderScreen(uri: Uri, library: Library, onClose: () -> Unit) {
    val context = LocalContext.current
    val name = remember(uri) { PdfDoc.displayName(context, uri) }
    var doc by remember(uri) { mutableStateOf<PdfDoc?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    var index by remember(uri) { mutableIntStateOf(0) }
    var barsVisible by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val pages = remember(uri) { mutableStateMapOf<Int, ImageBitmap>() }
    val currentIndex by rememberUpdatedState(index)
    val renderedFor = remember(uri) { arrayOf(IntSize.Zero) }

    // Ouverture du document, reprise à la dernière page lue
    LaunchedEffect(uri) {
        val opened = PdfDoc.open(context, uri)
        if (opened == null || opened.pageCount == 0) {
            failed = true
            library.remove(uri.toString())
        } else {
            val last = library.find(uri.toString())?.lastPage ?: 0
            index = last.coerceIn(0, opened.pageCount - 1)
            doc = opened
        }
    }

    // Fermeture : on mémorise la page et on libère le fichier
    DisposableEffect(uri) {
        onDispose {
            val d = doc
            if (d != null) {
                library.save(
                    RecentDoc(uri.toString(), name, d.pageCount, currentIndex, System.currentTimeMillis())
                )
                GlobalScope.launch { d.close() }
            }
        }
    }

    // Rendu de la page courante et de ses voisines, pour que l'animation soit fluide
    val current = doc
    LaunchedEffect(current, index, viewSize) {
        if (current == null || viewSize.width == 0 || viewSize.height == 0) return@LaunchedEffect
        // Changement de taille (rotation) : les images sont à refaire
        if (renderedFor[0] != viewSize) {
            pages.clear()
            renderedFor[0] = viewSize
        }
        // Limite la taille des images pour ménager la mémoire
        val limit = min(1f, 1600f / viewSize.width)
        val w = (viewSize.width * limit).roundToInt()
        val h = (viewSize.height * limit).roundToInt()
        for (i in listOf(index, index + 1, index - 1)) {
            if (i in 0 until current.pageCount && !pages.containsKey(i)) {
                val bitmap = current.render(i, w, h)
                if (bitmap != null) pages[i] = bitmap.asImageBitmap()
            }
        }
        pages.keys.filter { it < index - 2 || it > index + 2 }.forEach { pages.remove(it) }
        library.save(RecentDoc(uri.toString(), name, current.pageCount, index, System.currentTimeMillis()))
    }

    BackHandler { onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(ReaderGround)
            .onSizeChanged { viewSize = it }
    ) {
        when {
            failed -> Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Impossible d'ouvrir ce PDF",
                    color = Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Le fichier est peut-être protégé par un mot de passe, abîmé ou déplacé.",
                    color = InkSoft,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onClose) { Text("Retour") }
            }

            current == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Brick)

            else -> {
                BookPager(
                    pageCount = current.pageCount,
                    index = index,
                    pages = pages,
                    paper = Color.White,
                    onIndexChange = { index = it.coerceIn(0, current.pageCount - 1) },
                    onCenterTap = { barsVisible = !barsVisible },
                    modifier = Modifier.fillMaxSize()
                )

                AnimatedVisibility(
                    visible = barsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Paper)
                            .windowInsetsPadding(WindowInsets.displayCutout)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Ink)
                        }
                        Text(
                            name,
                            color = Ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = barsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    PageBar(
                        index = index,
                        pageCount = current.pageCount,
                        onJump = { index = it }
                    )
                }
            }
        }
    }
}

/** Barre du bas : numéro de page et curseur pour aller directement à une page. */
@Composable
private fun PageBar(index: Int, pageCount: Int, onJump: (Int) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue.roundToInt() else index
    Row(
        Modifier
            .fillMaxWidth()
            .background(Paper)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Page ${shown + 1} / $pageCount",
            color = Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (pageCount > 1) {
            Slider(
                value = if (dragging) dragValue else index.toFloat(),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    onJump(dragValue.roundToInt().coerceIn(0, pageCount - 1))
                    dragging = false
                },
                valueRange = 0f..(pageCount - 1).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Brick,
                    activeTrackColor = Brick,
                    inactiveTrackColor = Sand
                ),
                modifier = Modifier.weight(1f)
            )
        } else {
            Box(Modifier.size(1.dp))
        }
    }
}
