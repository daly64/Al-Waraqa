package tn.alwaraqa.app.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tn.alwaraqa.app.pdf.PdfDoc
import kotlin.math.min

private const val SCROLL_MAX_ZOOM = 4f

/**
 * Mode Défilement : les pages se suivent de haut en bas, chacune à la largeur de l'écran.
 * Pincer pour zoomer (un doigt déplace alors la page dans tous les sens),
 * double appui pour zoomer ou revenir, appui simple pour les barres.
 */
@Composable
fun ScrollPager(
    doc: PdfDoc,
    index: Int,
    widthPx: Int,
    uniformCrop: Boolean,
    night: Boolean,
    highlight: String?,
    paper: Color,
    filter: ColorFilter?,
    onIndexChange: (Int) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index)
    val scope = rememberCoroutineScope()
    val currentOnIndexChange by rememberUpdatedState(onIndexChange)
    val currentOnTap by rememberUpdatedState(onTap)
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var lastTapAt by remember { mutableStateOf(0L) }
    var pendingTap by remember { mutableStateOf<Job?>(null) }

    // La page en haut de l'écran devient la page courante
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.collect { currentOnIndexChange(it) }
    }
    // Saut demandé depuis les barres (curseur, sommaire, recherche, touches de volume)
    LaunchedEffect(index) {
        if (state.firstVisibleItemIndex != index) state.scrollToItem(index)
    }

    fun applyZoom(centroid: Offset, change: Float, move: Offset, width: Float) {
        val newZoom = (zoom * change).coerceIn(1f, SCROLL_MAX_ZOOM)
        // Horizontal : le point sous les doigts reste sous les doigts
        val contentX = (centroid.x - panX) / zoom
        panX = (centroid.x - contentX * newZoom + move.x).coerceIn(width - width * newZoom, 0f)
        // Vertical : on fait défiler la liste d'autant
        state.dispatchRawDelta(centroid.y / zoom - centroid.y / newZoom - move.y / newZoom)
        zoom = newZoom
    }

    // Les pages sont redessinées plus finement quand on zoome
    val level = if (zoom > 1.3f) 2 else 1
    val renderWidth = min(widthPx * level, 2400)

    Box(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val w = size.width.toFloat()
                    val slop = viewConfiguration.touchSlop
                    var travelled = Offset.Zero
                    var moved = false
                    var multi = false
                    var upTime = down.uptimeMillis
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            upTime = event.changes.firstOrNull()?.uptimeMillis ?: upTime
                            break
                        }
                        if (pressed.size >= 2) {
                            multi = true
                            applyZoom(
                                event.calculateCentroid(useCurrent = false),
                                event.calculateZoom(),
                                event.calculatePan(),
                                w
                            )
                            // La liste ne défile pas pendant le pincement
                            event.changes.forEach { it.consume() }
                        } else {
                            val d = pressed.first().positionChange()
                            travelled += d
                            if (travelled.getDistance() > slop) moved = true
                            // Zoomé : le doigt déplace aussi la page de gauche à droite
                            if (zoom > 1.01f) panX = (panX + d.x).coerceIn(w - w * zoom, 0f)
                        }
                    }
                    if (!moved && !multi && upTime - down.uptimeMillis < 500) {
                        if (upTime - lastTapAt < 280L) {
                            pendingTap?.cancel()
                            lastTapAt = 0L
                            // Zoomé : retour à la largeur de l'écran ; sinon zoom autour du doigt
                            applyZoom(down.position, if (zoom > 1.01f) 1f / zoom else 2.2f, Offset.Zero, w)
                        } else {
                            lastTapAt = upTime
                            pendingTap?.cancel()
                            pendingTap = scope.launch {
                                delay(280L)
                                currentOnTap()
                            }
                        }
                    }
                }
            }
    ) {
        LazyColumn(
            state = state,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = panX
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            items(count = doc.pageCount, key = { it }) { page ->
                // L'ancienne image reste affichée pendant qu'on prépare la nouvelle
                var image by remember(page) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(page, renderWidth, uniformCrop, night, highlight) {
                    if (renderWidth > 0) {
                        val bitmap = doc.render(page, renderWidth, renderWidth * 4, uniformCrop, night, highlight)
                        if (bitmap != null) image = bitmap.asImageBitmap()
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
}
