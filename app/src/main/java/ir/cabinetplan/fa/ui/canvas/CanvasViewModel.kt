package ir.cabinetplan.fa.ui.canvas

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.cabinetplan.fa.R
import ir.cabinetplan.fa.data.*
import ir.cabinetplan.fa.export.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.cabinetplan.fa.domain.*
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CanvasState(
    val editor: EditorState = EditorState(),
    val helpVisible: Boolean = false,
    val libraryVisible: Boolean = false,
    val projectId: String? = null,
    val projectName: String = "",
    val savedDocument: PlanDocument = PlanDocument(),
    val projects: List<ProjectEntity> = emptyList(),
    val saveDialog: Boolean = false,
    val browser: Boolean = false,
    val pendingDiscard: PendingOpen? = null,
    val pendingDelete: String? = null,
    val busy: Boolean = false,
    val message: Int? = null,
) {
    val viewport: Viewport get() = editor.viewport
    val dirty: Boolean get() = editor.document != savedDocument
}

sealed interface PendingOpen {
    data object New : PendingOpen
    data class Load(val id: String) : PendingOpen
}

sealed interface CanvasIntent {
    data object RequestSave : CanvasIntent
    data object DismissSave : CanvasIntent
    data class Save(val name: String, val copy: Boolean = false) : CanvasIntent
    data object Browse : CanvasIntent
    data object DismissBrowser : CanvasIntent
    data object NewProject : CanvasIntent
    data class Load(val id: String) : CanvasIntent
    data object ConfirmDiscard : CanvasIntent
    data object CancelDiscard : CanvasIntent
    data class RequestDeleteProject(val id: String) : CanvasIntent
    data object ConfirmDeleteProject : CanvasIntent
    data object CancelDeleteProject : CanvasIntent
    data class Export(val uri: Uri, val format: ExportFormat, val defaultName: String) : CanvasIntent
    data object ClearMessage : CanvasIntent
    data class Transform(val centroid: Point, val pan: Point, val zoom: Float) : CanvasIntent
    data class Zoom(val factor: Float) : CanvasIntent
    data object ResetView : CanvasIntent
    data object ShowHelp : CanvasIntent
    data object DismissHelp : CanvasIntent
    data class SetTool(val tool: Tool) : CanvasIntent
    data class Begin(val point: Point, val tolerance: Float) : CanvasIntent
    data class Drag(val point: Point, val tolerance: Float) : CanvasIntent
    data object Finish : CanvasIntent
    data object Cancel : CanvasIntent
    data class Place(val kind: CabinetKind, val point: Point) : CanvasIntent
    data class ChooseCabinet(val kind: CabinetKind) : CanvasIntent
    data object ToggleLibrary : CanvasIntent
    data object ToggleMultiple : CanvasIntent
    data object ToggleGrid : CanvasIntent
    data object ToggleDimensions : CanvasIntent
    data object Undo : CanvasIntent
    data object Redo : CanvasIntent
    data object Delete : CanvasIntent
    data class Fit(val width: Float, val height: Float) : CanvasIntent
}

@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val store: ProjectStore,
    private val exporter: PlanExport,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CanvasState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            store.projects.catch { error ->
                if (error is CancellationException) throw error
                mutableState.update { it.copy(message = R.string.load_failed) }
            }.collect { projects -> mutableState.update { it.copy(projects = projects) } }
        }
    }

    private fun task(errorMessage: Int, block: suspend () -> Unit) {
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutableState.update { it.copy(message = errorMessage) } }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    private fun open(action: PendingOpen) {
        if (action == PendingOpen.New) {
            mutableState.update { old -> CanvasState(projects = old.projects, message = R.string.new_created) }
        } else if (action is PendingOpen.Load) {
            task(R.string.load_failed) {
                val entity = requireNotNull(store.load(action.id))
                val document = DocumentCodec.decode(entity.documentJson)
                mutableState.update { it.copy(editor = EditorState(document = document), projectId = entity.id,
                    projectName = entity.name, savedDocument = document, browser = false,
                    pendingDiscard = null, message = R.string.loaded) }
            }
        }
    }

    fun onIntent(intent: CanvasIntent) {
        if (state.value.busy && intent != CanvasIntent.ClearMessage) return
        when (intent) {
            is CanvasIntent.Save -> {
                val name = intent.name.trim()
                if (name.isBlank() || name.length > 100) return
                val snapshot = state.value
                val id = if (intent.copy) UUID.randomUUID().toString() else snapshot.projectId ?: UUID.randomUUID().toString()
                task(R.string.save_failed) {
                    store.save(ProjectEntity(id, name, DocumentCodec.encode(snapshot.editor.document), System.currentTimeMillis()))
                    mutableState.update { it.copy(projectId = id, projectName = name, savedDocument = snapshot.editor.document,
                        saveDialog = false, message = R.string.saved) }
                }
                return
            }
            is CanvasIntent.Load, CanvasIntent.NewProject -> {
                val action = if (intent is CanvasIntent.Load) PendingOpen.Load(intent.id) else PendingOpen.New
                if (state.value.dirty) mutableState.update { it.copy(pendingDiscard = action, browser = false) } else open(action)
                return
            }
            CanvasIntent.ConfirmDiscard -> {
                val action = state.value.pendingDiscard ?: return
                mutableState.update { it.copy(pendingDiscard = null) }
                open(action)
                return
            }
            CanvasIntent.ConfirmDeleteProject -> {
                val id = state.value.pendingDelete ?: return
                task(R.string.delete_failed) {
                    store.delete(id)
                    mutableState.update { it.copy(pendingDelete = null, message = R.string.project_deleted,
                        projectId = if (it.projectId == id) null else it.projectId,
                        savedDocument = if (it.projectId == id) PlanDocument() else it.savedDocument) }
                }
                return
            }
            is CanvasIntent.Export -> {
                val snapshot = state.value
                if (snapshot.editor.document.lines.isEmpty()) return
                task(R.string.export_failed) {
                    exporter.write(intent.uri, snapshot.editor.document, snapshot.projectName.ifBlank { intent.defaultName }, intent.format)
                    mutableState.update { it.copy(message = R.string.exported) }
                }
                return
            }
            else -> Unit
        }
        mutableState.update { state ->
            val editor = state.editor
            when (intent) {
                CanvasIntent.RequestSave -> state.copy(saveDialog = true, editor = editor.copy(gesture = null, snap = null))
                CanvasIntent.DismissSave -> state.copy(saveDialog = false)
                CanvasIntent.Browse -> state.copy(browser = true, editor = editor.copy(gesture = null, snap = null))
                CanvasIntent.DismissBrowser -> state.copy(browser = false)
                CanvasIntent.CancelDiscard -> state.copy(pendingDiscard = null)
                is CanvasIntent.RequestDeleteProject -> state.copy(pendingDelete = intent.id)
                CanvasIntent.CancelDeleteProject -> state.copy(pendingDelete = null)
                CanvasIntent.ClearMessage -> state.copy(message = null)
                CanvasIntent.ShowHelp -> state.copy(helpVisible = true)
                CanvasIntent.DismissHelp -> state.copy(helpVisible = false)
                CanvasIntent.ToggleLibrary -> state.copy(libraryVisible = !state.libraryVisible)
                else -> state.copy(editor = when (intent) {
                    is CanvasIntent.Transform -> editor.copy(viewport = editor.viewport.transform(intent.centroid, intent.pan, intent.zoom))
                    is CanvasIntent.Zoom -> editor.copy(viewport = editor.viewport.transform(Point(), Point(), intent.factor))
                    CanvasIntent.ResetView -> editor.copy(viewport = Viewport())
                    is CanvasIntent.SetTool -> editor.copy(tool = intent.tool, gesture = null, snap = null)
                    is CanvasIntent.Begin -> editor.begin(intent.point, intent.tolerance)
                    is CanvasIntent.Drag -> editor.drag(intent.point, intent.tolerance)
                    CanvasIntent.Finish -> editor.finish()
                    CanvasIntent.Cancel -> editor.copy(gesture = null, snap = null)
                    is CanvasIntent.Place -> editor.place(intent.kind, intent.point)
                    is CanvasIntent.ChooseCabinet -> editor.copy(cabinet = intent.kind, tool = Tool.PLACE, gesture = null)
                    CanvasIntent.ToggleMultiple -> editor.copy(multiple = !editor.multiple, tool = Tool.SELECT, gesture = null)
                    CanvasIntent.ToggleGrid -> editor.copy(gridSnap = !editor.gridSnap)
                    CanvasIntent.ToggleDimensions -> editor.copy(dimensions = !editor.dimensions)
                    CanvasIntent.Undo -> editor.undo()
                    CanvasIntent.Redo -> editor.redo()
                    CanvasIntent.Delete -> editor.commit(PlanDocument(editor.document.lines.filter { it.id !in editor.selected }))
                    is CanvasIntent.Fit -> editor.copy(viewport = fitViewport(editor.document, intent.width, intent.height))
                    else -> editor
                })
            }
        }
    }
}
