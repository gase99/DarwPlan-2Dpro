package ir.cabinetplan.fa.domain

import org.junit.Assert.*
import org.junit.Test

class ExportLayoutTest {
    @Test fun `export fits negative coordinates without distorting dimensions`() {
        val bounds = Bounds(-900f, -200f, 300f, 400f)
        val layout = ExportLayout.fit(bounds, 842f, 595f, 32f, 48f)
        val a = layout.map(Point(bounds.left, bounds.top)); val b = layout.map(Point(bounds.right, bounds.bottom))
        assertTrue(a.x >= 31.99f && a.y >= 79.99f)
        assertTrue(b.x <= 810.01f && b.y <= 563.01f)
        assertEquals(2f, (b.x - a.x) / (b.y - a.y), 0.001f)
    }
    @Test fun `vertical lines produce a finite layout`() {
        val layout = ExportLayout.fit(Bounds(0f, 0f, 0f, 100f), 2048f, 2048f, 80f, 120f)
        assertTrue(layout.scale.isFinite()); assertTrue(layout.offset.isFinite)
    }
}
