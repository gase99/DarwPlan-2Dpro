package ir.cabinetplan.fa.domain

import org.junit.Assert.*
import org.junit.Test

class GeometryTest {
    private val line = PlanLine(id = "a", start = Point(100f, 100f), end = Point(200f, 100f))
    @Test fun `nearest vertex wins over grid and alignment`() {
        val snap = SnapEngine.snap(Point(103f, 104f), listOf(line), 8f)
        assertEquals(Point(100f, 100f), snap.point)
        assertTrue(snap.vertex)
    }
    @Test fun `vertices outside tolerance do not attract cursor`() {
        val snap = SnapEngine.snap(Point(130f, 150f), listOf(line), 5f, grid = false)
        assertEquals(Point(130f, 150f), snap.point)
        assertFalse(snap.vertex)
    }
    @Test fun `alignment and grid work with negative coordinates`() {
        val snap = SnapEngine.snap(Point(-23f, -39f), emptyList(), 6f)
        assertEquals(Point(-20f, -40f), snap.point)
        assertEquals(100f, SnapEngine.snap(Point(102f, 500f), listOf(line), 5f).guideX!!, 0f)
    }
    @Test fun `dimension uses euclidean millimeters`() {
        assertEquals(500f, PlanLine(start = Point(), end = Point(300f, 400f)).length, 0.001f)
    }
    @Test fun `segment hit testing clamps projection`() {
        assertEquals(10f, distanceToSegment(Point(150f, 110f), line), 0.001f)
        assertEquals(20f, distanceToSegment(Point(80f, 100f), line), 0.001f)
    }
    @Test fun `all library objects have valid dimensions and one group`() {
        CabinetKind.entries.forEach {
            val doc = PlanDocument(it.create(Point()))
            assertTrue(doc.valid())
            assertEquals(it.width, doc.bounds!!.width, 0.001f)
            assertEquals(it.depth, doc.bounds!!.height, 0.001f)
            assertEquals(1, doc.lines.map { line -> line.group }.distinct().size)
        }
    }
    @Test fun `invalid geometry rejected`() {
        assertFalse(PlanDocument(listOf(line, line)).valid())
        assertFalse(PlanDocument(listOf(line.copy(start = Point(Float.NaN, 0f)))).valid())
        assertFalse(PlanDocument(listOf(line.copy(end = line.start))).valid())
    }
    @Test fun `fit centers document and stays inside scale limits`() {
        val doc = PlanDocument(CabinetKind.BASE.create(Point(100f, 200f)))
        val viewport = fitViewport(doc, 1080f, 1400f)
        assertEquals(Point(540f, 700f), viewport.worldToScreen(doc.bounds!!.center, Point(540f, 700f)))
        assertTrue(viewport.zoom in Viewport.MIN_ZOOM..Viewport.MAX_ZOOM)
    }
}
