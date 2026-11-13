package ir.cabinetplan.fa.domain

import org.junit.Assert.*
import org.junit.Test

class ViewportAndExportLayoutTest {

    @Test
    fun `viewport worldToScreen and screenToWorld are exact inverses`() {
        val vp = Viewport(zoom = 1.75f, pan = Point(-45f, 80f))
        val center = Point(200f, 350f)
        val original = Point(135.5f, -62.25f)
        val roundTripped = vp.screenToWorld(vp.worldToScreen(original, center), center)
        assertEquals(original.x, roundTripped.x, 0.01f)
        assertEquals(original.y, roundTripped.y, 0.01f)
    }

    @Test
    fun `pinch zoom keeps world coordinate beneath fingers stationary`() {
        val vp = Viewport(zoom = 1.2f, pan = Point(20f, -15f))
        val center = Point(200f, 300f)
        val fingerScreen = Point(260f, 240f)
        val beforeWorld = vp.screenToWorld(fingerScreen, center)

        val next = vp.transform(fingerScreen - center, Point(), 2f)
        val afterWorld = next.screenToWorld(fingerScreen, center)

        assertEquals(beforeWorld.x, afterWorld.x, 0.01f)
        assertEquals(beforeWorld.y, afterWorld.y, 0.01f)
    }

    @Test
    fun `export layout fits bounds inside printable area preserving aspect ratio`() {
        val bounds = Bounds(60f, 60f, 480f, 400f)
        val layout = ExportLayout.fit(
            bounds = bounds,
            width = 842f,
            height = 595f,
            padding = 30f,
            header = 36f,
            footer = 22f,
            rightPanelWidth = 270f,
        )
        val tl = layout.map(Point(bounds.left, bounds.top))
        val br = layout.map(Point(bounds.right, bounds.bottom))
        assertTrue(tl.x >= 29.9f)
        assertTrue(tl.y >= 65.9f)
        assertTrue(br.x <= 842f - 270f - 29.9f)
        assertTrue(br.y <= 595f - 22f - 29.9f)
    }
}
