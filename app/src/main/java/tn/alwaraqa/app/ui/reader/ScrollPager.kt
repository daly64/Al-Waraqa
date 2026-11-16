package tn.alwaraqa.app.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import tn.alwaraqa.app.pdf.PdfDoc
import kotlin.math.min

/**
 * Mode Défilement : les pages se suivent de haut en bas, chacune à la largeur de l'écran.
 * Un appui affiche ou masque les barres.
 */
@Composable
fun ScrollPager(
    doc: PdfDoc,
    index: Int,
    widthPx: Int,
    uniformCrop: Boolean,
    paper: Color,
    filter: ColorFilter?,
    onIndexChange: (Int) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index)
    val currentOnIndexChange by rememberUpdatedState(onIndexChange)
    val currentOnTap by rememberUpdatedState(onTap)

    // La page en haut de l'écran devient la page courante
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.collect { currentOnIndexChange(it) }
    }
    // Saut demandé depuis les barres (curseur, sommaire, recherche)
    LaunchedEffect(index) {
        if (state.firstVisibleItemIndex != index) state.scrollToItem(index)
    }

    LazyColumn(
        state = state,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.pointerInput(Unit) { detectTapGestures { currentOnTap() } }
    ) {
        items(count = doc.pageCount, key = { it }) { page ->
            val image by produceState<ImageBitmap?>(initialValue = null, page, widthPx, uniformCrop) {
                if (widthPx > 0) {
                    val w = min(widthPx, 1600)
                    value = doc.render(page, w, w * 4, uniformCrop)?.asImageBitmap()
                }
            }
            val shown = image
            if (shown == null) {
                // En attendant l'image : une feuille vide aux proportions d'une page
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .background(paper)
                )
            } else {
                Image(
                    bitmap = shown,
                    contentDescription = "Page ${page + 1}",
                    contentScale = ContentScale.FillWidth,
                    colorFilter = filter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(paper)
                )
            }
        }
    }
}
