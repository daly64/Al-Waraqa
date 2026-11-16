package tn.alwaraqa.app.ui.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Une page en train d'être tournée : vers l'avant ou l'arrière, prise par le coin du haut ou du bas. */
private data class Turn(val forward: Boolean, val fromTop: Boolean)

private enum class Gesture { Undecided, Turn, Pan, Zoom, Ignored }

/** Mémoire des appuis, pour reconnaître un double appui. */
private class Taps {
    var lastAt = 0L
    var lastPos = Offset.Zero
    var pending: Job? = null
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val DOUBLE_TAP_MS = 280L

/**
 * Mode Livre : les pages se tournent au doigt, comme du papier.
 *
 * - glisser depuis la moitié droite : page suivante ; depuis la moitié gauche : page précédente ;
 * - appuyer sur le bord droit ou gauche : la page se tourne aussitôt, sans attente ;
 * - appuyer au centre : [onCenterTap] (afficher ou masquer les barres) ;
 * - double appui au centre : zoom rapide, puis retour à la page entière ;
 * - écarter ou pincer deux doigts : zoom ; une fois zoomé, un doigt déplace la page.
 *
 * Le pli est calculé en 2D : la ligne de pli est la médiatrice entre le coin de la page
 * et sa position actuelle. D'un côté on voit la page du dessous, de l'autre le verso replié.
 */
@Composable
fun BookPager(
    pageCount: Int,
    index: Int,
    pages: Map<Int, ImageBitmap>,
    sharpPage: ImageBitmap?,
    paper: Color,
    filter: ColorFilter?,
    flipRequests: Flow<Boolean>,
    onIndexChange: (Int) -> Unit,
    onTurned: () -> Unit,
    onCenterTap: () -> Unit,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var turn by remember { mutableStateOf<Turn?>(null) }
    var corner by remember { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val taps = remember { Taps() }
    val currentIndex by rememberUpdatedState(index)
    val currentCount by rememberUpdatedState(pageCount)
    val currentOnIndexChange by rememberUpdatedState(onIndexChange)
    val currentOnCenterTap by rememberUpdatedState(onCenterTap)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    val currentOnTurned by rememberUpdatedState(onTurned)

    // Zoom : écran = décalage + zoom × page
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    // Nouvelle page : on repart sans zoom
    LaunchedEffect(index) {
        zoom = 1f
        pan = Offset.Zero
        currentOnZoomChange(1f)
    }

    fun canTurn(forward: Boolean) = if (forward) currentIndex < currentCount - 1 else currentIndex > 0

    /** Applique un pincement autour de [centroid] et un déplacement, sans sortir de la page. */
    fun applyZoom(centroid: Offset, zoomChange: Float, move: Offset, width: Float, height: Float) {
        val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        val ratio = newZoom / zoom
        val moved = centroid - (centroid - pan) * ratio + move
        pan = Offset(
            moved.x.coerceIn(width - width * newZoom, 0f),
            moved.y.coerceIn(height - height * newZoom, 0f)
        )
        zoom = newZoom
        currentOnZoomChange(newZoom)
    }

    /** Garde le coin à portée de la reliure : une feuille ne s'étire pas. */
    fun clampCorner(p: Offset, cornerY: Float, width: Float): Offset {
        val spine = Offset(0f, cornerY)
        val d = p - spine
        val dist = d.getDistance()
        return if (dist > width && dist > 0f) spine + d * (width / dist) else p
    }

    /** Termine le geste : la page finit de se tourner, ou revient à plat. */
    fun settle(width: Float, height: Float) {
        val t = turn ?: return
        if (busy) return
        busy = true
        val cornerY = if (t.fromTop) 0f else height
        val flat = Offset(width, cornerY)
        val turned = Offset(-width, cornerY)
        val start = corner
        val toTurned = if (t.forward) start.x < width * 0.45f else start.x < -width * 0.25f
        val target = if (toTurned) turned else flat
        scope.launch {
            animate(0f, 1f, animationSpec = tween(320, easing = FastOutSlowInEasing)) { v, _ ->
                corner = clampCorner(start + (target - start) * v, cornerY, width)
            }
            if (t.forward && toTurned) {
                currentOnIndexChange(currentIndex + 1)
                currentOnTurned()
            }
            if (!t.forward && !toTurned) {
                currentOnIndexChange(currentIndex - 1)
                currentOnTurned()
            }
            turn = null
            busy = false
        }
    }

    /** Tourne la page toute seule (appui sur un bord, touche de volume). */
    fun flip(forward: Boolean, width: Float, height: Float) {
        if (busy || turn != null || !canTurn(forward) || width <= 0f) return
        busy = true
        corner = if (forward) Offset(width, height) else Offset(-width, height)
        turn = Turn(forward, fromTop = false)
        val lift = height * 0.16f
        scope.launch {
            animate(0f, 1f, animationSpec = tween(480, easing = FastOutSlowInEasing)) { v, _ ->
                val p = if (forward) v else 1f - v
                val raw = Offset(width - 2f * width * p, height - sin(PI.toFloat() * p) * lift)
                corner = clampCorner(raw, height, width)
            }
            currentOnIndexChange(if (forward) currentIndex + 1 else currentIndex - 1)
            currentOnTurned()
            turn = null
            busy = false
        }
    }

    /** Double appui : zoom autour du point touché, ou retour à la page entière. */
    fun toggleZoom(at: Offset, width: Float, height: Float) {
        if (busy || turn != null) return
        val fromZoom = zoom
        val fromPan = pan
        val toZoom = if (zoom > 1.01f) 1f else DOUBLE_TAP_ZOOM
        val toPan = if (toZoom == 1f) Offset.Zero else Offset(
            (at.x - at.x * toZoom).coerceIn(width - width * toZoom, 0f),
            (at.y - at.y * toZoom).coerceIn(height - height * toZoom, 0f)
        )
        scope.launch {
            animate(0f, 1f, animationSpec = tween(220, easing = FastOutSlowInEasing)) { v, _ ->
                zoom = fromZoom + (toZoom - fromZoom) * v
                pan = fromPan + (toPan - fromPan) * v
            }
            zoom = toZoom
            pan = toPan
            currentOnZoomChange(toZoom)
        }
    }

    /**
     * Un appui bref. Sur un bord (sans zoom) la page tourne tout de suite.
     * Au centre, on attend un court instant pour savoir si un second appui suit.
     */
    fun tap(pos: Offset, time: Long, width: Float, height: Float, nearPx: Float) {
        val isDouble = time - taps.lastAt < DOUBLE_TAP_MS && (pos - taps.lastPos).getDistance() < nearPx
        if (isDouble) {
            taps.pending?.cancel()
            taps.lastAt = 0L
            toggleZoom(pos, width, height)
            return
        }
        val onEdge = zoom <= 1.01f && (pos.x < width * 0.25f || pos.x > width * 0.75f)
        if (onEdge) {
            taps.lastAt = 0L
            flip(pos.x > width / 2f, width, height)
            return
        }
        taps.lastAt = time
        taps.lastPos = pos
        taps.pending?.cancel()
        taps.pending = scope.launch {
            delay(DOUBLE_TAP_MS)
            currentOnCenterTap()
        }
    }

    // Touches de volume
    LaunchedEffect(flipRequests) {
        flipRequests.collect { forward ->
            if (zoom <= 1.01f) flip(forward, viewSize.width.toFloat(), viewSize.height.toFloat())
        }
    }

    Canvas(
        modifier
            .onSizeChanged { viewSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val slop = viewConfiguration.touchSlop
                    var mode = Gesture.Undecided
                    var travelled = Offset.Zero
                    var maxPointers = 1
                    var upTime = down.uptimeMillis
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size > maxPointers) maxPointers = pressed.size
                        if (pressed.isEmpty()) {
                            upTime = event.changes.firstOrNull()?.uptimeMillis ?: upTime
                            break
                        }
                        if (pressed.size >= 2) {
                            // Deux doigts : zoom. Une page en cours de geste retombe à plat.
                            if (mode == Gesture.Turn && !busy) turn = null
                            if (!busy && turn == null) {
                                mode = Gesture.Zoom
                                applyZoom(
                                    centroid = event.calculateCentroid(useCurrent = false),
                                    zoomChange = event.calculateZoom(),
                                    move = event.calculatePan(),
                                    width = w,
                                    height = h
                                )
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else {
                            val change = pressed.first()
                            val delta = change.positionChange()
                            when (mode) {
                                Gesture.Undecided -> {
                                    travelled += delta
                                    if (travelled.getDistance() > slop) {
                                        if (zoom > 1.01f) {
                                            mode = Gesture.Pan
                                        } else {
                                            val forward = down.position.x > w / 2f
                                            if (!busy && turn == null && canTurn(forward)) {
                                                val t = Turn(forward, fromTop = down.position.y < h / 2f)
                                                val cornerY = if (t.fromTop) 0f else h
                                                // Au départ la page est à plat (avant) ou entièrement tournée (arrière)
                                                corner = if (forward) Offset(w, cornerY) else Offset(-w, cornerY)
                                                turn = t
                                                mode = Gesture.Turn
                                            } else {
                                                mode = Gesture.Ignored
                                            }
                                        }
                                    }
                                }

                                Gesture.Turn -> {
                                    val t = turn
                                    if (t != null && !busy) {
                                        change.consume()
                                        val cornerY = if (t.fromTop) 0f else h
                                        val touch = change.position
                                        // Vers l'avant le coin suit le doigt ; vers l'arrière la page
                                        // se déplie deux fois plus vite pour être à plat au bord droit.
                                        val raw = if (t.forward) touch else Offset(2f * touch.x - w, touch.y)
                                        corner = clampCorner(raw, cornerY, w)
                                    }
                                }

                                Gesture.Pan, Gesture.Zoom -> {
                                    change.consume()
                                    applyZoom(Offset.Zero, 1f, delta, w, h)
                                }

                                Gesture.Ignored -> Unit
                            }
                        }
                    }
                    if (mode == Gesture.Turn) settle(w, h)
                    if (zoom < 1.05f && zoom != 1f) {
                        zoom = 1f
                        pan = Offset.Zero
                        currentOnZoomChange(1f)
                    }
                    if (mode == Gesture.Undecided && maxPointers == 1 && upTime - down.uptimeMillis < 500) {
                        tap(down.position, upTime, w, h, slop * 6f)
                    }
                }
            }
    ) {
        val t = turn
        if (t == null) {
            drawRect(paper)
            withTransform({
                translate(pan.x, pan.y)
                scale(zoom, zoom, pivot = Offset.Zero)
            }) {
                drawSheet(sharpPage ?: pages[index], paper, filter)
            }
        } else {
            val top = if (t.forward) index else index - 1
            val under = if (t.forward) index + 1 else index
            drawCurl(
                top = pages[top],
                under = pages[under],
                paper = paper,
                filter = filter,
                pageCorner = Offset(size.width, if (t.fromTop) 0f else size.height),
                movedCorner = corner
            )
        }
    }
}

/** Une feuille à plat : le papier, puis l'image de la page centrée dessus. */
private fun DrawScope.drawSheet(image: ImageBitmap?, paper: Color, filter: ColorFilter?) {
    drawRect(paper)
    if (image == null) return
    val scale = min(size.width / image.width, size.height / image.height)
    val w = image.width * scale
    val h = image.height * scale
    drawImage(
        image = image,
        dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        colorFilter = filter,
        filterQuality = FilterQuality.Medium
    )
}

/** La page du dessus en train de se tourner au-dessus de la page [under]. */
private fun DrawScope.drawCurl(
    top: ImageBitmap?,
    under: ImageBitmap?,
    paper: Color,
    filter: ColorFilter?,
    pageCorner: Offset,
    movedCorner: Offset
) {
    val v = pageCorner - movedCorner
    val dist = v.getDistance()
    if (dist < 2f) {
        drawSheet(top, paper, filter)
        return
    }
    val n = v / dist                      // normale du pli, vers le coin d'origine
    val mid = (pageCorner + movedCorner) / 2f
    val along = Offset(-n.y, n.x)
    val far = (size.width + size.height) * 2f

    // Demi-plans de part et d'autre de la ligne de pli
    fun halfPlane(direction: Offset) = Path().apply {
        val a = mid + along * far
        val b = mid - along * far
        val c = b + direction * far
        val d = a + direction * far
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        lineTo(d.x, d.y)
        close()
    }
    val liftedSide = halfPlane(n)
    val flatSide = halfPlane(n * -1f)

    clipRect {
        // 1. La page du dessous, avec l'ombre de la feuille qui se soulève
        drawSheet(under, paper, filter)
        clipPath(liftedSide) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent),
                    start = mid,
                    end = mid + n * (dist * 0.30f + 24f)
                ),
                size = Size(size.width, size.height)
            )
        }

        // 2. Ce qui reste à plat de la page du dessus
        clipPath(flatSide) {
            drawSheet(top, paper, filter)
            // ombre portée par le verso replié
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                    start = mid - n * (dist * 0.5f + 28f),
                    end = mid - n * (dist * 0.5f)
                ),
                size = Size(size.width, size.height)
            )
        }

        // 3. Le verso : la partie soulevée, repliée par symétrie autour de la ligne de pli
        val k = 2f * (mid.x * n.x + mid.y * n.y)
        val mirror = Matrix().apply {
            values[Matrix.ScaleX] = 1f - 2f * n.x * n.x
            values[Matrix.SkewY] = -2f * n.x * n.y
            values[Matrix.SkewX] = -2f * n.x * n.y
            values[Matrix.ScaleY] = 1f - 2f * n.y * n.y
            values[Matrix.TranslateX] = k * n.x
            values[Matrix.TranslateY] = k * n.y
        }
        withTransform({ transform(mirror) }) {
            clipPath(liftedSide) {
                clipRect {
                    drawRect(paper)
                    drawRect(Color.Black.copy(alpha = 0.05f))
                    // arrondi du papier près du pli, puis léger reflet
                    drawRect(
                        brush = Brush.linearGradient(
                            0f to Color.Black.copy(alpha = 0.28f),
                            0.35f to Color.Black.copy(alpha = 0.04f),
                            0.6f to Color.White.copy(alpha = 0.18f),
                            1f to Color.Transparent,
                            start = mid,
                            end = mid + n * (dist * 0.5f)
                        ),
                        size = Size(size.width, size.height)
                    )
                }
            }
        }
    }
}
