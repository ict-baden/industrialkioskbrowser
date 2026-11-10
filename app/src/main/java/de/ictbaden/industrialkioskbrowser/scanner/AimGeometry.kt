package de.ictbaden.industrialkioskbrowser.scanner

import de.ictbaden.industrialkioskbrowser.config.AimMode
import kotlin.math.hypot
import kotlin.math.max

/**
 * Which of the codes in a camera image counts for `scanner.camera.aim`. Pure geometry, so it can
 * be tested without a camera.
 *
 * zxing-cpp reports the corners in the coordinates of the upright (rotated) image. The preview
 * (PreviewView, FILL_CENTER) shows that image scaled by `max(viewW/imgW, viewH/imgH)` and centred.
 */
object AimGeometry {

    data class Point(val x: Float, val y: Float)

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = (left + right) / 2
        val centerY get() = (top + bottom) / 2
        fun contains(p: Point) = p.x in left..right && p.y in top..bottom
    }

    /** The aiming frame for a view of [viewW]×[viewH] pixels; wide enough for 1D codes, high enough for QR. */
    fun frame(viewW: Float, viewH: Float, density: Float): Box {
        val w = minOf(viewW * 0.8f, 520 * density)
        val h = minOf(w * 0.75f, viewH * 0.45f)
        return Box((viewW - w) / 2, (viewH - h) / 2, (viewW + w) / 2, (viewH + h) / 2)
    }

    /** Maps a point of the upright image into the preview view. */
    fun toView(p: Point, imageW: Int, imageH: Int, viewW: Float, viewH: Float): Point {
        val s = max(viewW / imageW, viewH / imageH)
        return Point(p.x * s + (viewW - imageW * s) / 2, p.y * s + (viewH - imageH * s) / 2)
    }

    /**
     * Index of the code to take out of [codes] (each the four corners in image coordinates), or
     * `null` when none qualifies.
     */
    fun pick(
        codes: List<List<Point>>,
        imageW: Int,
        imageH: Int,
        viewW: Float,
        viewH: Float,
        frame: Box,
        mode: AimMode,
    ): Int? {
        if (codes.isEmpty()) return null
        val mapped = codes.map { corners -> corners.map { toView(it, imageW, imageH, viewW, viewH) } }
        val target = Point(frame.centerX, frame.centerY)
        fun distance(corners: List<Point>) = center(corners).let { hypot(it.x - target.x, it.y - target.y) }
        val indices = mapped.indices
        return when (mode) {
            AimMode.ANYWHERE -> indices.minByOrNull { distance(mapped[it]) }
            AimMode.FRAME -> indices.filter { frame.contains(center(mapped[it])) }.minByOrNull { distance(mapped[it]) }
            AimMode.CENTER -> indices.firstOrNull { inside(target, mapped[it]) }
                ?: indices.filter { distance(mapped[it]) <= frame.width * 0.15f }.minByOrNull { distance(mapped[it]) }
        }
    }

    fun center(corners: List<Point>) = Point(corners.map { it.x }.average().toFloat(), corners.map { it.y }.average().toFloat())

    /** Point in polygon (ray casting); works for the four corners zxing reports in either winding. */
    fun inside(p: Point, polygon: List<Point>): Boolean {
        var result = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) result = !result
            j = i
        }
        return result
    }
}
