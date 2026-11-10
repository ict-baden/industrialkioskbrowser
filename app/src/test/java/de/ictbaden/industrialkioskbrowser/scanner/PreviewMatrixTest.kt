package de.ictbaden.industrialkioskbrowser.scanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PreviewMatrixTest {

    @Test
    fun `preview matrix keeps the aspect ratio and fills the view`() {
        // Portrait phone 1080x2160, landscape sensor buffer 1280x720 turned by 90°.
        val m = CameraScanActivity.previewMatrix(1080f, 2160f, 1280f, 720f, 90)
        val corners = floatArrayOf(0f, 0f, 1080f, 0f, 0f, 2160f)
        m.mapPoints(corners)
        // Unrotated buffer x axis (view width) ends up vertical, y axis horizontal.
        val xAxisLength = abs(corners[3] - corners[1]) + abs(corners[2] - corners[0])
        val yAxisLength = abs(corners[5] - corners[1]) + abs(corners[4] - corners[0])
        // 1280:720 buffer shown upright as 720:1280, scaled to cover 1080x2160 → 2160 high, 1215 wide.
        assertEquals(2160f, xAxisLength, 1f)
        assertEquals(1215f, yAxisLength, 1f)
    }
}
