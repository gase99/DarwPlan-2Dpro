package ir.cabinetplan.fa.domain

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import ir.cabinetplan.fa.data.*
import ir.cabinetplan.fa.export.*
import ir.cabinetplan.fa.ui.canvas.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CanvasViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private class FakeStore : ProjectStore {
        override val projects = MutableStateFlow<List<ProjectEntity>>(emptyList())
        var fail = false
        override suspend fun save(project: ProjectEntity) {
            check(!fail)
            projects.value = projects.value.filter { it.id != project.id } + project
        }
        override suspend fun load(id: String) = projects.value.find { it.id == id }
        override suspend fun delete(id: String) { projects.value = projects.value.filter { it.id != id } }
    }
    private val store = FakeStore()
    private val exporter = object : PlanExport {
        override suspend fun write(uri: Uri, document: PlanDocument, title: String, format: ExportFormat) = Unit
    }
    private fun model() = CanvasViewModel(store, exporter).also { models.put("model", it) }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test fun `transform intent updates observable state`() {
        val model = model()
        model.onIntent(CanvasIntent.Transform(Point(), Point(10f, 25f), 2f))
        assertEquals(Viewport(2f, Point(10f, 25f)), model.state.value.viewport)
    }
    @Test fun `reset restores viewport without closing help`() {
        val model = model()
        model.onIntent(CanvasIntent.ShowHelp); model.onIntent(CanvasIntent.Zoom(3f)); model.onIntent(CanvasIntent.ResetView)
        assertEquals(Viewport(), model.state.value.viewport)
        assertTrue(model.state.value.helpVisible)
    }
    @Test fun `help can open and dismiss`() {
        val model = model()
        model.onIntent(CanvasIntent.ShowHelp); assertTrue(model.state.value.helpVisible)
        model.onIntent(CanvasIntent.DismissHelp); assertFalse(model.state.value.helpVisible)
    }
    @Test fun `save and load preserve exact geometry and clear history`() = runTest(dispatcher) {
        val model = model()
        model.onIntent(CanvasIntent.Place(CabinetKind.BASE, Point()))
        val document = model.state.value.editor.document
        model.onIntent(CanvasIntent.Save("آشپزخانه")); advanceUntilIdle()
        assertFalse(model.state.value.dirty)
        val id = model.state.value.projectId!!
        model.onIntent(CanvasIntent.NewProject)
        assertTrue(model.state.value.editor.document.lines.isEmpty())
        model.onIntent(CanvasIntent.Load(id)); advanceUntilIdle()
        assertEquals(document, model.state.value.editor.document)
        assertTrue(model.state.value.editor.past.isEmpty())
    }
    @Test fun `unsaved new document requires confirmation`() {
        val model = model(); model.onIntent(CanvasIntent.Place(CabinetKind.DOOR, Point()))
        model.onIntent(CanvasIntent.NewProject)
        assertNotNull(model.state.value.pendingDiscard)
        assertTrue(model.state.value.editor.document.lines.isNotEmpty())
        model.onIntent(CanvasIntent.CancelDiscard)
        assertNull(model.state.value.pendingDiscard)
    }
    @Test fun `failed save keeps document dirty and does not assign project id`() = runTest(dispatcher) {
        val model = model(); model.onIntent(CanvasIntent.Place(CabinetKind.WALL, Point()))
        store.fail = true; model.onIntent(CanvasIntent.Save("آزمون")); advanceUntilIdle()
        assertTrue(model.state.value.dirty); assertNull(model.state.value.projectId)
        assertFalse(model.state.value.busy); assertNotNull(model.state.value.message)
    }
    @Test fun `corrupt saved project never replaces canvas`() = runTest(dispatcher) {
        val model = model(); model.onIntent(CanvasIntent.Place(CabinetKind.WALL, Point()))
        val before = model.state.value.editor.document
        store.save(ProjectEntity("bad", "خراب", "{}", 0))
        model.onIntent(CanvasIntent.Load("bad")); model.onIntent(CanvasIntent.ConfirmDiscard); advanceUntilIdle()
        assertEquals(before, model.state.value.editor.document)
        assertNotNull(model.state.value.message)
    }
    @Test fun `save copy uses independent project id`() = runTest(dispatcher) {
        val model = model(); model.onIntent(CanvasIntent.Save("یک")); advanceUntilIdle()
        val id = model.state.value.projectId
        model.onIntent(CanvasIntent.Save("دو", copy = true)); advanceUntilIdle()
        assertNotEquals(id, model.state.value.projectId)
        assertEquals(2, store.projects.value.size)
    }
}
