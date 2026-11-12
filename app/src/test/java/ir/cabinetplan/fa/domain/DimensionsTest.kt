package ir.cabinetplan.fa.domain

import org.junit.Assert.*
import org.junit.Test

class DimensionsTest {
    @Test fun `Persian Arabic and Latin dimensions parse consistently`() {
        assertEquals(1250.5f, parseMillimeters("۱٬۲۵۰٫۵")!!, 0f)
        assertEquals(600f, parseMillimeters("٦٠٠")!!, 0f)
        assertEquals(12.5f, parseMillimeters("12.5")!!, 0f)
        for (text in listOf("NaN", "Infinity", "-1", "0", "100001", "")) assertNull(parseMillimeters(text))
    }
    @Test fun `exact line length preserves direction and supports undo`() {
        val line = PlanLine(id = "a", start = Point(10f, 20f), end = Point(310f, 420f))
        val initial = EditorState(document = PlanDocument(listOf(line)), selected = setOf("a"))
        val state = initial.resizeLength(1000f)
        assertEquals(1000f, state.document.lines.single().length, .001f)
        assertEquals(Point(10f, 20f), state.document.lines.single().start)
        assertEquals(Point(610f, 820f), state.document.lines.single().end)
        assertEquals(initial.document, state.undo().document)
    }
    @Test fun `cabinet resizing preserves center and group identity`() {
        val document = PlanDocument(CabinetKind.BASE.create(Point(-40f, 80f)))
        val state = EditorState(document = document, selected = document.lines.map { it.id }.toSet()).resizeBounds(800f, 550f)
        assertEquals(800f, state.document.bounds!!.width, .001f)
        assertEquals(550f, state.document.bounds!!.height, .001f)
        assertEquals(document.bounds!!.center, state.document.bounds!!.center)
        assertEquals(document.lines.first().group, state.document.lines.first().group)
    }
}
