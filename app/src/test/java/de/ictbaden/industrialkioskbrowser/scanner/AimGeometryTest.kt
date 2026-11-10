package de.ictbaden.industrialkioskbrowser.scanner

import de.ictbaden.industrialkioskbrowser.config.AimMode
import de.ictbaden.industrialkioskbrowser.scanner.AimGeometry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AimGeometryTest {

    // Upright image 720×1280 (a portrait phone), shown in a 1080×1920 view: scale 1.5, no offset.
    private val imageW = 720
    private val imageH = 1280
    private val viewW = 1080f
    private val viewH = 1920f
    private val frame = AimGeometry.frame(viewW, viewH, density = 2.75f)

    /** A code of [w]×[h] image pixels centred at ([cx], [cy]) in image coordinates. */
    private fun code(cx: Float, cy: Float, w: Float = 100f, h: Float = 40f) = listOf(
        Point(cx - w / 2, cy - h / 2),
        Point(cx + w / 2, cy - h / 2),
        Point(cx + w / 2, cy + h / 2),
        Point(cx - w / 2, cy + h / 2),
    )

    private fun pick(mode: AimMode, vararg codes: List<Point>) =
        AimGeometry.pick(codes.toList(), imageW, imageH, viewW, viewH, frame, mode)

    @Test
    fun `maps image to view with centre crop`() {
        assertEquals(Point(540f, 960f), AimGeometry.toView(Point(360f, 640f), imageW, imageH, viewW, viewH))
        // Wider view than the image: scaled to the width, cropped top and bottom.
        val p = AimGeometry.toView(Point(0f, 0f), 720, 1280, 1080f, 1080f)
        assertEquals(0f, p.x, 0.01f)
        assertEquals((1080f - 1280f * 1.5f) / 2, p.y, 0.01f)
    }

    @Test
    fun `frame mode takes only codes inside the frame`() {
        val inside = code(360f, 640f)
        val outside = code(360f, 100f)
        assertEquals(1, pick(AimMode.FRAME, outside, inside))
        assertNull(pick(AimMode.FRAME, outside))
    }

    @Test
    fun `center mode takes the code under the crosshair`() {
        val left = code(250f, 640f, w = 120f)
        val middle = code(360f, 640f, w = 80f)
        assertEquals(1, pick(AimMode.CENTER, left, middle))
        // No code covers the centre, but one is close: it counts.
        val near = code(360f, 670f, w = 80f, h = 20f)
        assertEquals(0, pick(AimMode.CENTER, near))
        // Far away codes do not.
        assertNull(pick(AimMode.CENTER, left))
    }

    @Test
    fun `anywhere mode takes the code closest to the centre`() {
        val far = code(100f, 100f)
        val closer = code(400f, 900f)
        assertEquals(1, pick(AimMode.ANYWHERE, far, closer))
        assertEquals(0, pick(AimMode.ANYWHERE, far))
        assertNull(pick(AimMode.ANYWHERE))
    }

    @Test
    fun `point in rotated quadrilateral`() {
        val diamond = listOf(Point(0f, -10f), Point(10f, 0f), Point(0f, 10f), Point(-10f, 0f))
        assertTrue(AimGeometry.inside(Point(0f, 0f), diamond))
        assertFalse(AimGeometry.inside(Point(8f, 8f), diamond))
    }
}
