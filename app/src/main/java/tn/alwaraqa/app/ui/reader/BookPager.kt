package tn.alwaraqa.app.ui.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Une page en train d'être tournée : vers l'avant ou l'arrière, prise par le coin du haut ou du bas. */
private data class Turn(val forward: Boolean, val fromTop: Boolean)

/**
 * Mode Livre : les pages se tournent au doigt, comme du papier.
 *
 * - glisser depuis la moitié droite : page suivante ; depuis la moitié gauche : page précédente ;
 * - appuyer sur le bord droit ou gauche : la page se tourne toute seule ;
 * - appuyer au centre : [onCenterTap] (afficher ou masquer les barres).
 *
 * Le pli est calculé en 2D : la ligne de pli est la médiatrice entre le coin de la page
 * et sa position actuelle. D'un côté on voit la page du dessous, de l'autre le verso replié.
 */
@Composable
fun BookPager(
    pageCount: Int,
    index: Int,
    pages: Map<Int, ImageBitmap>,
    paper: Color,
    onIndexChange: (Int) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var turn by remember { mutableStateOf<Turn?>(null) }
    var corner by remember { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }
    val currentIndex by rememberUpdatedState(index)
    val currentCount by rememberUpdatedState(pageCount)
    val currentOnIndexChange by rememberUpdatedState(onIndexChange)
    val currentOnCenterTap by rememberUpdatedState(onCenterTap)

    fun canTurn(forward: Boolean) = if (forward) currentIndex < currentCount - 1 else currentIndex > 0

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
            if (t.forward && toTurned) currentOnIndexChange(currentIndex + 1)
            if (!t.forward && !toTurned) currentOnIndexChange(currentIndex - 1)
            turn = null
            busy = false
        }
    }

    /** Tourne la page toute seule, après un appui sur un bord. */
    fun flip(forward: Boolean, width: Float, height: Float) {
        if (busy || turn != null || !canTurn(forward)) return
        busy = true
        corner = if (forward) Offset(width, height) else Offset(-width, height)
        turn = Turn(forward, fromTop = false)
        val lift = height * 0.16f
        scope.launch {
            animate(0f, 1f, animationSpec = tween(520, easing = FastOutSlowInEasing)) { v, _ ->
                val p = if (forward) v else 1f - v
                val raw = Offset(width - 2f * width * p, height - sin(PI.toFloat() * p) * lift)
                corner = clampCorner(raw, height, width)
            }
            currentOnIndexChange(if (forward) currentIndex + 1 else currentIndex - 1)
            turn = null
            busy = false
        }
    }

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    when {
                        pos.x < w * 0.25f -> flip(false, w, h)
                        pos.x > w * 0.75f -> flip(true, w, h)
                        else -> currentOnCenterTap()
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val forward = start.x > w / 2f
                        if (!busy && turn == null && canTurn(forward)) {
                            val t = Turn(forward, fromTop = start.y < h / 2f)
                            val cornerY = if (t.fromTop) 0f else h
                            // Au départ la page est à plat (avant) ou entièrement tournée (arrière)
                            corner = if (forward) Offset(w, cornerY) else Offset(-w, cornerY)
                            turn = t
                        }
                    },
                    onDrag = { change, _ ->
                        val t = turn
                        if (t != null && !busy) {
                            change.consume()
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val cornerY = if (t.fromTop) 0f else h
                            val touch = change.position
                            // Vers l'avant le coin suit le doigt ; vers l'arrière la page
                            // se déplie deux fois plus vite pour être à plat au bord droit.
                            val raw = if (t.forward) touch else Offset(2f * touch.x - w, touch.y)
                            corner = clampCorner(raw, cornerY, w)
                        }
                    },
                    onDragEnd = { settle(size.width.toFloat(), size.height.toFloat()) },
                    onDragCancel = { settle(size.width.toFloat(), size.height.toFloat()) }
                )
            }
    ) {
        val t = turn
        if (t == null) {
            drawSheet(pages[index], paper)
        } else {
            val top = if (t.forward) index else index - 1
            val under = if (t.forward) index + 1 else index
            drawCurl(
                top = pages[top],
                under = pages[under],
                paper = paper,
                pageCorner = Offset(size.width, if (t.fromTop) 0f else size.height),
                movedCorner = corner
            )
        }
    }
}

/** Une feuille à plat : le papier, puis l'image de la page centrée dessus. */
private fun DrawScope.drawSheet(image: ImageBitmap?, paper: Color) {
    drawRect(paper)
    if (image == null) return
    val scale = min(size.width / image.width, size.height / image.height)
    val w = image.width * scale
    val h = image.height * scale
    drawImage(
        image = image,
        dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        filterQuality = FilterQuality.Medium
    )
}

/** La page du dessus en train de se tourner au-dessus de la page [under]. */
private fun DrawScope.drawCurl(
    top: ImageBitmap?,
    under: ImageBitmap?,
    paper: Color,
    pageCorner: Offset,
    movedCorner: Offset
) {
    val v = pageCorner - movedCorner
    val dist = v.getDistance()
    if (dist < 2f) {
        drawSheet(top, paper)
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
        drawSheet(under, paper)
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
            drawSheet(top, paper)
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
