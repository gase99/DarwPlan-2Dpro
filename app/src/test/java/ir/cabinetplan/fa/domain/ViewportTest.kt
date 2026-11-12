package ir.cabinetplan.fa.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportTest {
    private fun assertPoint(expected: Point, actual: Point) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }

    @Test fun `world and screen conversions round trip`() {
        val viewport = Viewport(zoom = 2.5f, pan = Point(37f, -92f))
        val center = Point(540f, 900f)
        val world = Point(-120f, 600f)
        assertPoint(world, viewport.screenToWorld(viewport.worldToScreen(world, center), center))
    }

    @Test fun `pan follows finger without mirroring in RTL`() {
        val viewport = Viewport().transform(Point(), Point(80f, -25f), 1f)
        assertPoint(Point(80f, -25f), viewport.pan)
    }

    @Test fun `zoom keeps world point underneath centroid fixed`() {
        val viewport = Viewport(pan = Point(40f, -30f))
        val centroid = Point(125f, 260f)
        val before = viewport.screenToWorld(centroid, Point())
        val after = viewport.transform(centroid, Point(), 2f)
        assertPoint(before, after.screenToWorld(centroid, Point()))
    }

    @Test fun `combined pan and zoom tracks moving centroid`() {
        val viewport = Viewport(2f, Point(40f, 20f))
        val centroid = Point(-100f, 200f)
        val delta = Point(35f, -60f)
        val before = viewport.screenToWorld(centroid, Point())
        val after = viewport.transform(centroid, delta, 0.75f)
        assertPoint(before, after.screenToWorld(centroid + delta, Point()))
    }

    @Test fun `zoom clamps at both limits`() {
        assertEquals(Viewport.MAX_ZOOM, Viewport().transform(Point(), Point(), 100f).zoom, 0f)
        assertEquals(Viewport.MIN_ZOOM, Viewport().transform(Point(), Point(), 0.001f).zoom, 0f)
    }

    @Test fun `clamped zoom still preserves anchor`() {
        val viewport = Viewport(7f, Point(12f, -20f))
        val centroid = Point(90f, 180f)
        val next = viewport.transform(centroid, Point(), 5f)
        assertPoint(viewport.screenToWorld(centroid, Point()), next.screenToWorld(centroid, Point()))
    }

    @Test fun `millimeter dimensions map consistently to pixels`() {
        val viewport = Viewport(zoom = 1.5f)
        val start = viewport.worldToScreen(Point(10f, 20f), Point())
        val end = viewport.worldToScreen(Point(610f, 20f), Point())
        assertEquals(1800f, end.x - start.x, 0.001f)
    }

    @Test fun `invalid gestures leave viewport unchanged`() {
        val viewport = Viewport()
        for (scale in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(viewport, viewport.transform(Point(), Point(), scale))
        }
        assertEquals(viewport, viewport.transform(Point(Float.NaN, 0f), Point(), 1f))
        assertEquals(viewport, viewport.transform(Point(), Point(Float.POSITIVE_INFINITY, 0f), 1f))
    }
}
