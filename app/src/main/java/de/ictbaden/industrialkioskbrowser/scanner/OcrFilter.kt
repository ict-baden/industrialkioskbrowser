package de.ictbaden.industrialkioskbrowser.scanner

import de.ictbaden.industrialkioskbrowser.config.OcrCharset
import de.ictbaden.industrialkioskbrowser.config.OcrSettings
import kotlin.math.hypot

/**
 * Which recognised lines count for `scanner.ocr` (issue #49), as pure functions so they can be
 * tested without a camera: clean-up by [OcrCharset], length, pattern and confirmation over frames.
 */
object OcrFilter {

    private val SPACES = Regex("\\s+")
    private val NOT_DIGITS = Regex("[^0-9,.\\-]")
    private val NOT_ALNUM = Regex("[^\\p{L}\\p{N}]")

    /** The line after the clean-up of [charset]; may be empty. */
    fun clean(line: String, charset: OcrCharset): String = when (charset) {
        OcrCharset.ANY -> line.trim().replace(SPACES, " ")
        OcrCharset.DIGITS -> line.replace(NOT_DIGITS, "")
        OcrCharset.ALNUM -> line.replace(NOT_ALNUM, "")
        OcrCharset.UPPER -> line.replace(NOT_ALNUM, "").uppercase()
    }

    /** Whether a cleaned line passes length and – when one is set – the pattern (whole line). */
    fun accepts(cleaned: String, settings: OcrSettings): Boolean {
        if (cleaned.isEmpty()) return false
        if (settings.minLength > 0 && cleaned.length < settings.minLength) return false
        if (settings.maxLength > 0 && cleaned.length > settings.maxLength) return false
        val pattern = settings.pattern ?: return true
        return runCatching { Regex(pattern).matches(cleaned) }.getOrDefault(false)
    }

    /** The cleaned, accepted lines of one frame, in their order. */
    fun candidates(lines: List<String>, settings: OcrSettings): List<String> =
        lines.map { clean(it, settings.charset) }.filter { accepts(it, settings) }

    /**
     * A line's bounding box (ML Kit, coordinates of the upright image of [imageW]×[imageH]) in the
     * preview view (FILL_CENTER), see [AimGeometry.toView].
     */
    fun toViewBox(left: Int, top: Int, right: Int, bottom: Int, imageW: Int, imageH: Int, viewW: Float, viewH: Float): AimGeometry.Box {
        val a = AimGeometry.toView(AimGeometry.Point(left.toFloat(), top.toFloat()), imageW, imageH, viewW, viewH)
        val b = AimGeometry.toView(AimGeometry.Point(right.toFloat(), bottom.toFloat()), imageW, imageH, viewW, viewH)
        return AimGeometry.Box(a.x, a.y, b.x, b.y)
    }

    /** The text of the line whose centre is nearest to the frame centre, or `null` for none. */
    fun closest(lines: List<Pair<String, AimGeometry.Box>>, frame: AimGeometry.Box): String? =
        lines.minByOrNull { (_, b) -> hypot(b.centerX - frame.centerX, b.centerY - frame.centerY) }?.first

    /** The line under a tap at ([x], [y]), with [slop] pixels of tolerance around each box. */
    fun lineAt(lines: List<Pair<String, AimGeometry.Box>>, x: Float, y: Float, slop: Float): String? {
        val p = AimGeometry.Point(x, y)
        return lines.firstOrNull { (_, b) -> AimGeometry.Box(b.left - slop, b.top - slop, b.right + slop, b.bottom + slop).contains(p) }?.first
    }

    /**
     * Pattern mode: a text counts once it was the first match in [needed] consecutive frames. OCR
     * misreads single characters more often than a barcode decoder, so the default is 2.
     */
    class Confirmer(private val needed: Int) {
        private var last: String? = null
        private var count = 0

        /** Offer the first match of a frame (or `null` for none); returns the text once it is confirmed. */
        fun offer(text: String?): String? {
            if (text == null) {
                last = null
                count = 0
                return null
            }
            count = if (text == last) count + 1 else 1
            last = text
            if (count < needed) return null
            last = null
            count = 0
            return text
        }
    }
}
