package ir.cabinetplan.fa.domain

import org.junit.Assert.*
import org.junit.Test

class SnapEngineAndBomTest {

    private val sampleWalls = listOf(
        WallLine("w1", Point(100f, 100f), Point(300f, 100f), 15f, "دیوار شمالی"),
        WallLine("w2", Point(300f, 100f), Point(300f, 260f), 15f, "دیوار شرقی"),
    )

    @Test
    fun `vertex magnetic snap within threshold snaps to exact wall corner`() {
        val res = SnapEngine.computeSmartSnap(
            rawPoint = Point(296f, 104f),
            lines = sampleWalls,
            cabinets = emptyList(),
            snapRadiusCm = 8f,
        )
        assertEquals(Point(300f, 100f), res.snappedPoint)
        assertEquals(SnapType.VERTEX, res.indicator?.type)
    }

    @Test
    fun `midpoint magnetic snap snaps to center of wall segment`() {
        val res = SnapEngine.computeSmartSnap(
            rawPoint = Point(202f, 98f),
            lines = sampleWalls,
            cabinets = emptyList(),
            snapRadiusCm = 6f,
        )
        assertEquals(Point(200f, 100f), res.snappedPoint)
        assertEquals(SnapType.MIDPOINT, res.indicator?.type)
    }

    @Test
    fun `90 degree angle lock snaps near-vertical line orthogonally`() {
        val res = SnapEngine.computeSmartSnap(
            rawPoint = Point(103f, 220f),
            lines = emptyList(),
            cabinets = emptyList(),
            anchorPoint = Point(100f, 100f),
            snapEnabled = true,
            angleSnapEnabled = true,
            gridStepCm = 5f,
            snapRadiusCm = 6f,
        )
        assertEquals(100f, res.snappedPoint.x, 0.01f)
        assertEquals(220f, res.snappedPoint.y, 0.01f)
        assertEquals(SnapType.ANGLE, res.indicator?.type)
    }

    @Test
    fun `alignment guide emitted when aligned with existing vertex X coordinate`() {
        val res = SnapEngine.computeSmartSnap(
            rawPoint = Point(301f, 340f),
            lines = sampleWalls,
            cabinets = emptyList(),
            snapRadiusCm = 8f,
        )
        assertEquals(300f, res.snappedPoint.x, 0.01f)
        assertTrue(res.guides.any { it.vertical && it.position == 300f })
    }

    @Test
    fun `Persian digits and centimeter parser handle Persian Arabic and Latin inputs`() {
        assertEquals(185f, parseCentimeters("۱۸۵")!!, 0.01f)
        assertEquals(62.5f, parseCentimeters("۶۲٫۵")!!, 0.01f)
        assertEquals(90f, parseCentimeters("٩٠")!!, 0.01f)
        assertNull(parseCentimeters("-15"))
        assertEquals("۱۸۵ سم", formatCm(185f))
        assertEquals("۲٫۴۰ متر طول", formatMeters(240f))
    }

    @Test
    fun `BOM calculator computes running meters accurately including rotated cabinets`() {
        val sink = PERSIAN_CABINET_CATALOG.first { it.symbolType == CabinetSymbolType.SINK_DOUBLE }
            .createUnit(Point(0f, 0f)) // 100 cm
        val rotatedBase = PERSIAN_CABINET_CATALOG.first { it.symbolType == CabinetSymbolType.BASE_DOUBLE }
            .createUnit(Point(100f, 0f))
            .rotated90() // 80 cm span along rotated axis (depth becomes 80)
        val wallCab = PERSIAN_CABINET_CATALOG.first { it.symbolType == CabinetSymbolType.WALL_DISHRACK }
            .createUnit(Point(0f, 0f)) // 80 cm

        val bom = BomCalculator.calculate(listOf(sink, rotatedBase, wallCab))
        assertEquals(180f, bom.baseRunningCm, 0.01f)
        assertEquals(80f, bom.wallRunningCm, 0.01f)
        assertEquals(3, bom.totalUnits)
    }
}
