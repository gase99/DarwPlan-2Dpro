package ir.cabinetplan.fa.domain

import ir.cabinetplan.fa.ui.canvas.CanvasIntent
import ir.cabinetplan.fa.ui.canvas.CanvasViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasViewModelTest {
    @Test fun `transform intent updates observable state`() {
        val model = CanvasViewModel()
        model.onIntent(CanvasIntent.Transform(Point(), Point(10f, 25f), 2f))
        assertEquals(Viewport(2f, Point(10f, 25f)), model.state.value.viewport)
    }

    @Test fun `reset restores viewport without closing help`() {
        val model = CanvasViewModel()
        model.onIntent(CanvasIntent.ShowHelp)
        model.onIntent(CanvasIntent.Zoom(3f))
        model.onIntent(CanvasIntent.ResetView)
        assertEquals(Viewport(), model.state.value.viewport)
        assertTrue(model.state.value.helpVisible)
    }

    @Test fun `help can open and dismiss`() {
        val model = CanvasViewModel()
        assertFalse(model.state.value.helpVisible)
        model.onIntent(CanvasIntent.ShowHelp)
        assertTrue(model.state.value.helpVisible)
        model.onIntent(CanvasIntent.DismissHelp)
        assertFalse(model.state.value.helpVisible)
    }
}
