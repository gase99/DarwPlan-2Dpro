package ir.cabinetplan.fa.domain

import org.junit.Assert.*
import org.junit.Test

class EditorTest {
    private val a = PlanLine(id = "a", start = Point(), end = Point(100f, 0f))
    private val b = PlanLine(id = "b", start = Point(200f, 100f), end = Point(300f, 100f))
    @Test fun `drawing is previewed before one history entry is committed`() {
        val begin = EditorState(tool = Tool.LINE).begin(Point(), 5f).drag(Point(100f, 0f), 5f)
        assertTrue(begin.document.lines.isEmpty())
        assertEquals(1, begin.visibleDocument.lines.size)
        val end = begin.finish()
        assertEquals(1, end.document.lines.size)
        assertEquals(1, end.past.size)
        assertTrue(end.undo().document.lines.isEmpty())
        assertEquals(end.document, end.undo().redo().document)
    }
    @Test fun `group movement preserves relative positions and lengths`() {
        val state = EditorState(document = PlanDocument(listOf(a, b)), selected = setOf("a", "b"), tool = Tool.SELECT)
            .begin(Point(50f, 0f), 3f).drag(Point(75f, 30f), 3f).finish()
        assertEquals(Point(25f, 30f), state.document.lines[0].start)
        assertEquals(Point(225f, 130f), state.document.lines[1].start)
        assertEquals(100f, state.document.lines[0].length, 0.001f)
    }
    @Test fun `endpoint snaps to a vertex without moving the other endpoint`() {
        val state = EditorState(document = PlanDocument(listOf(a, b)), selected = setOf("a"), tool = Tool.SELECT)
            .begin(a.end, 5f).drag(Point(198f, 102f), 5f).finish()
        assertEquals(a.start, state.document.lines[0].start)
        assertEquals(b.start, state.document.lines[0].end)
    }
    @Test fun `new edit after undo clears redo`() {
        val state = EditorState().place(CabinetKind.BASE, Point()).undo().place(CabinetKind.DOOR, Point())
        assertTrue(state.future.isEmpty())
    }
    @Test fun `zero length stroke is discarded`() {
        val state = EditorState(tool = Tool.LINE).begin(Point(), 5f).finish()
        assertTrue(state.document.lines.isEmpty())
        assertTrue(state.past.isEmpty())
    }
    @Test fun `multiselect toggles line membership`() {
        val initial = EditorState(document = PlanDocument(listOf(a, b)), tool = Tool.SELECT, multiple = true)
        val state = initial.begin(Point(50f, 0f), 5f).finish().begin(Point(250f, 100f), 5f).finish()
        assertEquals(setOf("a", "b"), state.selected)
        assertEquals(setOf("b"), state.begin(Point(50f, 0f), 5f).selected)
    }
    @Test fun `history is bounded and cancel never changes document`() {
        var state = EditorState()
        repeat(70) { state = state.commit(PlanDocument(listOf(a.copy(end = Point(it + 1f, 0f))))) }
        assertEquals(60, state.past.size)
        val preview = state.copy(tool = Tool.LINE).begin(Point(100f, 300f), 5f).drag(Point(500f, 500f), 5f)
        assertEquals(state.document, preview.document)
    }
}
