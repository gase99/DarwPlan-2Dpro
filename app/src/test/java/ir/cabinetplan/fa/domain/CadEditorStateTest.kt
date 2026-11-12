package ir.cabinetplan.fa.domain

import org.junit.Assert.*
import org.junit.Test

class CadEditorStateTest {

    @Test
    fun `drawing a wall records exactly one undo history entry on finish`() {
        var state = CadEditorState(activeTool = ToolMode.DRAW_WALL)
        state = state.beginGesture(Point(50f, 50f), snapRadiusCm = 8f)
        state = state.dragGesture(Point(150f, 52f), snapRadiusCm = 8f)
        assertTrue(state.past.isEmpty())
        state = state.finishGesture()

        assertEquals(1, state.document.lines.size)
        assertEquals(100f, state.document.lines.single().lengthCm, 0.01f)
        assertEquals(1, state.past.size)

        val undone = state.undo()
        assertTrue(undone.document.lines.isEmpty())
        assertEquals(1, undone.future.size)
        assertEquals(1, undone.redo().document.lines.size)
    }

    @Test
    fun `selecting and dragging wall endpoint snaps and updates length`() {
        val wall = WallLine("w1", Point(0f, 0f), Point(120f, 0f))
        var state = CadEditorState(
            document = ProjectDocument(lines = listOf(wall)),
            selectedLineIds = setOf("w1"),
            activeTool = ToolMode.SELECT,
        )
        state = state.beginGesture(Point(118f, 2f), snapRadiusCm = 8f, handleRadiusCm = 10f)
        state = state.dragGesture(Point(200f, 1f), snapRadiusCm = 8f)
        state = state.finishGesture()

        assertEquals(200f, state.document.lines.single().lengthCm, 0.01f)
        assertEquals(1, state.past.size)
    }

    @Test
    fun `cabinet rotation duplicate align nudge and delete work cleanly`() {
        val baseItem = PERSIAN_CABINET_CATALOG.first { it.symbolType == CabinetSymbolType.BASE_DOUBLE }
        var state = CadEditorState().placeCatalogItem(baseItem, Point(100f, 100f))
        assertEquals(1, state.document.cabinets.size)
        assertEquals(80f, state.document.cabinets.single().width, 0.01f)
        assertEquals(60f, state.document.cabinets.single().depth, 0.01f)

        state = state.rotateSelectedCabinets90()
        assertEquals(60f, state.document.cabinets.single().width, 0.01f)
        assertEquals(80f, state.document.cabinets.single().depth, 0.01f)
        assertEquals(90, state.document.cabinets.single().rotation)

        state = state.duplicateSelected()
        assertEquals(2, state.document.cabinets.size)

        // Select both and align horizontally after nudging one
        state = state.nudgeSelected(0f, 30f)
        val allIds = state.document.cabinets.map { it.id }.toSet()
        state = state.copy(selectedCabinetIds = allIds).alignSelectedGroup(horizontal = true)
        val ys = state.document.cabinets.map { it.y }.distinct()
        assertEquals(1, ys.size)

        state = state.deleteSelected()
        assertTrue(state.document.cabinets.isEmpty())
    }

    @Test
    fun `front elevation view selects cabinet by front projection bounds`() {
        val preset = PRESET_PROJECTS.first().document
        val targetCab = preset.cabinets.first { it.id == "cab_5" }
        val elevY = CadEditorState.ELEVATION_FLOOR_Y - targetCab.elevationCm - targetCab.height / 2f
        val state = CadEditorState(
            document = preset,
            viewMode = ViewMode.ELEVATION_FRONT,
        ).beginGesture(Point(targetCab.x + targetCab.width / 2f, elevY), snapRadiusCm = 8f)

        assertEquals(setOf(targetCab.id), state.selectedCabinetIds)
    }
}
