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
import org.junit.After
import org.junit.Assert.*
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
        override suspend fun delete(id: String) {
            projects.value = projects.value.filter { it.id != id }
        }
    }

    private val store = FakeStore()
    private val exporter = object : PlanExport {
        override suspend fun write(uri: Uri, document: ProjectDocument, title: String, format: ExportFormat) = Unit
    }

    private fun model() = CanvasViewModel(store, exporter, dispatcher).also { models.put("model", it) }

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test
    fun `initial state loads preset kitchen cleanly without dirty flag`() {
        val model = model()
        assertFalse(model.state.value.dirty)
        assertEquals(PRESET_PROJECTS.first().document, model.state.value.editor.document)
    }

    @Test
    fun `save and load preserve exact geometry metadata and clear undo stack`() = runTest(dispatcher) {
        val model = model()
        val island = PERSIAN_CABINET_CATALOG.first { it.symbolType == CabinetSymbolType.ISLAND_UNIT }
        model.onIntent(CanvasIntent.AddCatalogUnit(island))
        model.onIntent(CanvasIntent.UpdateProjectMeta("آشپزخانه زعفرانیه", "مهندس کریمی", "صفحه کوارتز"))
        val expectedDoc = model.state.value.editor.document

        model.onIntent(CanvasIntent.SaveProject())
        advanceUntilIdle()
        assertFalse(model.state.value.dirty)
        val savedId = requireNotNull(model.state.value.projectId)

        model.onIntent(CanvasIntent.RequestNewBlank("خام", "", withStarterWalls = false))
        assertTrue(model.state.value.editor.document.isEmpty)

        model.onIntent(CanvasIntent.RequestLoadSaved(savedId))
        advanceUntilIdle()
        assertEquals(expectedDoc, model.state.value.editor.document)
        assertEquals("آشپزخانه زعفرانیه", model.state.value.projectNameFa)
        assertTrue(model.state.value.editor.past.isEmpty())
    }

    @Test
    fun `unsaved changes prompt confirmation before opening blank project`() {
        val model = model()
        model.onIntent(CanvasIntent.DeleteSelected) // no-op if nothing selected
        model.onIntent(CanvasIntent.AddCatalogUnit(PERSIAN_CABINET_CATALOG.first()))
        assertTrue(model.state.value.dirty)

        model.onIntent(CanvasIntent.RequestNewBlank())
        assertNotNull(model.state.value.pendingOpen)

        model.onIntent(CanvasIntent.CancelDiscardPending)
        assertNull(model.state.value.pendingOpen)
        assertFalse(model.state.value.editor.document.isEmpty)
    }

    @Test
    fun `failed save keeps dirty flag and surfaces error message`() = runTest(dispatcher) {
        val model = model()
        model.onIntent(CanvasIntent.AddCatalogUnit(PERSIAN_CABINET_CATALOG.first()))
        store.fail = true
        model.onIntent(CanvasIntent.SaveProject())
        advanceUntilIdle()

        assertTrue(model.state.value.dirty)
        assertNull(model.state.value.projectId)
        assertFalse(model.state.value.busy)
        assertNotNull(model.state.value.messageRes)
    }

    @Test
    fun `corrupt saved json never replaces active document`() = runTest(dispatcher) {
        val model = model()
        val before = model.state.value.editor.document
        store.save(ProjectEntity("corrupt", "خراب", "{not-valid}", 10L))

        model.onIntent(CanvasIntent.RequestLoadSaved("corrupt"))
        advanceUntilIdle()

        assertEquals(before, model.state.value.editor.document)
        assertNotNull(model.state.value.messageRes)
    }

    @Test
    fun `save as copy assigns distinct project id`() = runTest(dispatcher) {
        val model = model()
        model.onIntent(CanvasIntent.SaveProject(customName = "نسخه اول"))
        advanceUntilIdle()
        val firstId = requireNotNull(model.state.value.projectId)

        model.onIntent(CanvasIntent.SaveProject(asCopy = true, customName = "نسخه دوم"))
        advanceUntilIdle()
        val secondId = requireNotNull(model.state.value.projectId)

        assertNotEquals(firstId, secondId)
        assertEquals(2, store.projects.value.size)
    }
}
