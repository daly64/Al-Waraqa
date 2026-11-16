package tn.alwaraqa.app.pdf

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Mode Nuit intelligent : le texte et le fond sont inversés (fond brun très sombre,
 * texte crème), mais les photos et illustrations gardent leurs vraies couleurs,
 * simplement adoucies pour ne pas éblouir.
 *
 * La page est découpée en carreaux de 16 px. Un carreau rempli de tons moyens ou de
 * couleurs vives, entouré d'autres carreaux semblables, appartient à une image.
 * Le texte, lui, n'est fait que de blanc et de noir avec un peu de lissage.
 */
object NightPage {
    private const val TILE = 16

    fun apply(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return
        val tw = (w + TILE - 1) / TILE
        val th = (h + TILE - 1) / TILE
        val mid = IntArray(tw * th)
        val total = IntArray(tw * th)
        val strip = IntArray(w * TILE)

        // 1. Part de tons moyens ou de couleurs vives dans chaque carreau
        for (ty in 0 until th) {
            val y0 = ty * TILE
            val rows = min(TILE, h - y0)
            bitmap.getPixels(strip, 0, w, 0, y0, w, rows)
            for (yy in 0 until rows) {
                val base = yy * w
                for (x in 0 until w) {
                    val c = strip[base + x]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val l = (r * 54 + g * 183 + b * 19) shr 8
                    val sat = max(r, max(g, b)) - min(r, min(g, b))
                    val t = ty * tw + x / TILE
                    total[t]++
                    if (l in 40..214 || (sat > 70 && l < 225)) mid[t]++
                }
            }
        }

        // 2. Carreaux candidats, puis seuls ceux entourés d'autres candidats (une image est une zone, pas un mot)
        val candidate = BooleanArray(tw * th) { total[it] > 0 && mid[it] * 100 > total[it] * 45 }
        val image = BooleanArray(tw * th)
        for (ty in 0 until th) {
            for (tx in 0 until tw) {
                if (!candidate[ty * tw + tx]) continue
                var around = 0
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = tx + dx
                    val ny = ty + dy
                    if (nx in 0 until tw && ny in 0 until th && candidate[ny * tw + nx]) around++
                }
                if (around >= 4) image[ty * tw + tx] = true
            }
        }
        // Un carreau de marge autour des images, pour ne pas inverser leurs bords
        val keep = BooleanArray(tw * th)
        for (ty in 0 until th) {
            for (tx in 0 until tw) {
                var any = false
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = tx + dx
                    val ny = ty + dy
                    if (nx in 0 until tw && ny in 0 until th && image[ny * tw + nx]) any = true
                }
                keep[ty * tw + tx] = any
            }
        }

        // 3. Transformation : inversion douce du texte, images seulement adoucies
        for (ty in 0 until th) {
            val y0 = ty * TILE
            val rows = min(TILE, h - y0)
            bitmap.getPixels(strip, 0, w, 0, y0, w, rows)
            for (yy in 0 until rows) {
                val base = yy * w
                for (x in 0 until w) {
                    val c = strip[base + x]
                    val a = c and -0x1000000
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    strip[base + x] = if (keep[ty * tw + x / TILE]) {
                        a or ((r * 205 shr 8) shl 16) or ((g * 205 shr 8) shl 8) or (b * 205 shr 8)
                    } else {
                        val nr = (230 - r * 204 / 256).coerceIn(0, 255)
                        val ng = (220 - g * 200 / 256).coerceIn(0, 255)
                        val nb = (206 - b * 190 / 256).coerceIn(0, 255)
                        a or (nr shl 16) or (ng shl 8) or nb
                    }
                }
            }
            bitmap.setPixels(strip, 0, w, 0, y0, w, rows)
        }
    }
}
