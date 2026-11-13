package ir.cabinetplan.fa.ui.canvas

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.cabinetplan.fa.R
import ir.cabinetplan.fa.data.DocumentCodec
import ir.cabinetplan.fa.data.ProjectEntity
import ir.cabinetplan.fa.data.ProjectStore
import ir.cabinetplan.fa.di.DocumentDispatcher
import ir.cabinetplan.fa.domain.*
import ir.cabinetplan.fa.export.ExportFormat
import ir.cabinetplan.fa.export.PlanExport
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SideTab {
    LIBRARY,
    INSPECTOR,
    BOM,
}

enum class ActiveModal {
    MOBILE_SHEET,
    PROJECTS,
    EXPORT,
    HELP,
}

sealed interface PendingOpenAction {
    data class Saved(val id: String) : PendingOpenAction
    data class Preset(val template: PresetProjectTemplate) : PendingOpenAction
    data class Blank(val nameFa: String, val clientNameFa: String, val withStarterWalls: Boolean) : PendingOpenAction
}

data class CanvasUiState(
    val editor: CadEditorState = defaultInitialEditor(),
    val projectId: String? = null,
    val projectNameFa: String = PRESET_PROJECTS.first().nameFa,
    val savedDocument: ProjectDocument = PRESET_PROJECTS.first().document,
    val savedProjects: List<ProjectEntity> = emptyList(),
    val activeSideTab: SideTab = SideTab.LIBRARY,
    val isDesktopPanelOpen: Boolean = true,
    val activeModal: ActiveModal? = null,
    val pendingOpen: PendingOpenAction? = null,
    val pendingDeleteProjectId: String? = null,
    val busy: Boolean = false,
    val messageRes: Int? = null,
) {
    val dirty: Boolean
        get() = editor.document != savedDocument
}

private fun defaultInitialEditor(): CadEditorState {
    val initial = PRESET_PROJECTS.first().document
    val initialBounds = initial.bounds
    val viewport = if (initialBounds != null) {
        Viewport.fitBounds(initialBounds, 412f, 680f, 44f)
    } else {
        Viewport()
    }
    return CadEditorState(
        document = initial,
        viewport = viewport,
    )
}

sealed interface CanvasIntent {
    // Tool & View Modes
    data class SetTool(val tool: ToolMode) : CanvasIntent
    data class SetViewMode(val mode: ViewMode) : CanvasIntent
    data object ToggleMultiSelect : CanvasIntent
    data object ToggleSnap : CanvasIntent
    data object ToggleAngleLock : CanvasIntent
    data object ToggleDimensions : CanvasIntent
    data object ToggleWallLayer : CanvasIntent
    data object ToggleBaseLayer : CanvasIntent

    // Canvas Gestures & Viewport
    data class BeginGesture(val world: Point, val snapRadiusCm: Float, val handleRadiusCm: Float) : CanvasIntent
    data class DragGesture(val world: Point, val snapRadiusCm: Float) : CanvasIntent
    data object FinishGesture : CanvasIntent
    data object CancelGesture : CanvasIntent
    data class TransformViewport(val centroidFromCenter: Point, val pan: Point, val zoom: Float) : CanvasIntent
    data class ZoomBy(val factor: Float) : CanvasIntent
    data object ResetView : CanvasIntent
    data class FitToScreen(val widthPx: Float, val heightPx: Float) : CanvasIntent

    // Catalog & Element Operations
    data class AddCatalogUnit(val item: CatalogItem, val canvasWidthPx: Float = 0f, val canvasHeightPx: Float = 0f) : CanvasIntent
    data class StartPlaceCatalogUnit(val item: CatalogItem) : CanvasIntent
    data class SelectSingleCabinet(val id: String) : CanvasIntent
    data object ClearSelection : CanvasIntent
    data class UpdateLine(val line: WallLine) : CanvasIntent
    data class UpdateCabinet(val cabinet: CabinetObject) : CanvasIntent
    data object RotateSelected90 : CanvasIntent
    data object DuplicateSelected : CanvasIntent
    data object DeleteSelected : CanvasIntent
    data class NudgeSelected(val dx: Float, val dy: Float) : CanvasIntent
    data class AlignSelected(val horizontal: Boolean) : CanvasIntent
    data object Undo : CanvasIntent
    data object Redo : CanvasIntent

    // UI Panels & Modals
    data class SelectSideTab(val tab: SideTab, val openMobileSheet: Boolean = false) : CanvasIntent
    data object ToggleDesktopPanel : CanvasIntent
    data class OpenModal(val modal: ActiveModal?) : CanvasIntent

    // Project Metadata, Persistence & Export
    data class UpdateProjectMeta(
        val nameFa: String,
        val clientNameFa: String,
        val notesFa: String = "",
    ) : CanvasIntent
    data class SaveProject(val asCopy: Boolean = false, val customName: String? = null) : CanvasIntent
    data class RequestLoadSaved(val id: String) : CanvasIntent
    data class RequestLoadPreset(val preset: PresetProjectTemplate) : CanvasIntent
    data class RequestNewBlank(
        val nameFa: String = "",
        val clientNameFa: String = "",
        val withStarterWalls: Boolean = false,
    ) : CanvasIntent
    data object ConfirmDiscardPending : CanvasIntent
    data object CancelDiscardPending : CanvasIntent
    data class RequestDeleteSaved(val id: String) : CanvasIntent
    data object ConfirmDeleteSaved : CanvasIntent
    data object CancelDeleteSaved : CanvasIntent
    data class ExportDocument(val uri: Uri, val format: ExportFormat, val defaultTitle: String) : CanvasIntent
    data object ClearMessage : CanvasIntent
}

@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val store: ProjectStore,
    private val exporter: PlanExport,
    @DocumentDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(CanvasUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.projects
                .catch { _state.update { s -> s.copy(messageRes = R.string.load_failed) } }
                .collect { list -> _state.update { s -> s.copy(savedProjects = list) } }
        }
    }

    fun onIntent(intent: CanvasIntent) {
        when (intent) {
            is CanvasIntent.SetTool -> _state.update {
                it.copy(editor = it.editor.clearTransient().copy(activeTool = intent.tool))
            }
            is CanvasIntent.SetViewMode -> _state.update {
                it.copy(editor = it.editor.clearTransient().copy(viewMode = intent.mode))
            }
            CanvasIntent.ToggleMultiSelect -> _state.update {
                it.copy(editor = it.editor.copy(multiSelectMode = !it.editor.multiSelectMode))
            }
            CanvasIntent.ToggleSnap -> _state.update {
                it.copy(editor = it.editor.copy(snapEnabled = !it.editor.snapEnabled))
            }
            CanvasIntent.ToggleAngleLock -> _state.update {
                it.copy(editor = it.editor.copy(angleSnapEnabled = !it.editor.angleSnapEnabled))
            }
            CanvasIntent.ToggleDimensions -> _state.update {
                it.copy(editor = it.editor.copy(showDimensions = !it.editor.showDimensions))
            }
            CanvasIntent.ToggleWallLayer -> _state.update {
                it.copy(editor = it.editor.copy(showWallCabinets = !it.editor.showWallCabinets))
            }
            CanvasIntent.ToggleBaseLayer -> _state.update {
                it.copy(editor = it.editor.copy(showBaseCabinets = !it.editor.showBaseCabinets))
            }

            is CanvasIntent.BeginGesture -> _state.update {
                it.copy(editor = it.editor.beginGesture(intent.world, intent.snapRadiusCm, intent.handleRadiusCm))
            }
            is CanvasIntent.DragGesture -> _state.update {
                it.copy(editor = it.editor.dragGesture(intent.world, intent.snapRadiusCm))
            }
            CanvasIntent.FinishGesture -> _state.update { s ->
                val prevWallCount = s.editor.document.lines.size
                val prevCabCount = s.editor.document.cabinets.size
                val nextEditor = s.editor.finishGesture()
                val msg = when {
                    nextEditor.document.lines.size > prevWallCount -> R.string.wall_added
                    nextEditor.document.cabinets.size > prevCabCount -> R.string.unit_added
                    else -> s.messageRes
                }
                s.copy(editor = nextEditor, messageRes = msg)
            }
            CanvasIntent.CancelGesture -> _state.update {
                it.copy(editor = it.editor.clearTransient())
            }
            is CanvasIntent.TransformViewport -> _state.update {
                it.copy(
                    editor = it.editor.copy(
                        viewport = it.editor.viewport.transform(intent.centroidFromCenter, intent.pan, intent.zoom)
                    )
                )
            }
            is CanvasIntent.ZoomBy -> _state.update {
                it.copy(
                    editor = it.editor.copy(
                        viewport = it.editor.viewport.transform(Point(), Point(), intent.factor)
                    )
                )
            }
            CanvasIntent.ResetView -> _state.update {
                it.copy(editor = it.editor.copy(viewport = Viewport()))
            }
            is CanvasIntent.FitToScreen -> _state.update { s ->
                val bounds = s.editor.document.bounds ?: return@update s
                s.copy(
                    editor = s.editor.copy(
                        viewport = Viewport.fitBounds(bounds, intent.widthPx, intent.heightPx, 48f)
                    )
                )
            }

            is CanvasIntent.AddCatalogUnit -> _state.update { s ->
                val customPos = if (intent.canvasWidthPx > 0f && intent.canvasHeightPx > 0f) {
                    val centerScreen = Point(intent.canvasWidthPx / 2f, intent.canvasHeightPx / 2f)
                    val worldCenter = s.editor.viewport.screenToWorld(centerScreen, centerScreen)
                    val jitter = (s.editor.document.cabinets.size % 5) * 10f
                    Point(
                        worldCenter.x - intent.item.defaultWidth / 2f + jitter,
                        worldCenter.y - intent.item.defaultDepth / 2f + jitter,
                    )
                } else null
                s.copy(
                    editor = s.editor.placeCatalogItem(intent.item, customPos),
                    activeModal = if (s.activeModal == ActiveModal.MOBILE_SHEET) null else s.activeModal,
                    messageRes = R.string.unit_added,
                )
            }
            is CanvasIntent.StartPlaceCatalogUnit -> _state.update { s ->
                s.copy(
                    editor = s.editor.clearTransient().copy(
                        activeTool = ToolMode.PLACE_CABINET,
                        pendingCatalogItem = intent.item,
                    ),
                    activeModal = null,
                )
            }
            is CanvasIntent.SelectSingleCabinet -> _state.update { s ->
                s.copy(
                    editor = s.editor.copy(
                        selectedLineIds = emptySet(),
                        selectedCabinetIds = setOf(intent.id),
                    ),
                    activeSideTab = SideTab.INSPECTOR,
                )
            }
            CanvasIntent.ClearSelection -> _state.update { s ->
                s.copy(editor = s.editor.copy(selectedLineIds = emptySet(), selectedCabinetIds = emptySet()))
            }
            is CanvasIntent.UpdateLine -> _state.update {
                it.copy(editor = it.editor.updateLine(intent.line))
            }
            is CanvasIntent.UpdateCabinet -> _state.update {
                it.copy(editor = it.editor.updateCabinet(intent.cabinet))
            }
            CanvasIntent.RotateSelected90 -> _state.update {
                it.copy(editor = it.editor.rotateSelectedCabinets90())
            }
            CanvasIntent.DuplicateSelected -> _state.update {
                it.copy(editor = it.editor.duplicateSelected())
            }
            CanvasIntent.DeleteSelected -> _state.update {
                it.copy(editor = it.editor.deleteSelected())
            }
            is CanvasIntent.NudgeSelected -> _state.update {
                it.copy(editor = it.editor.nudgeSelected(intent.dx, intent.dy))
            }
            is CanvasIntent.AlignSelected -> _state.update {
                it.copy(editor = it.editor.alignSelectedGroup(intent.horizontal))
            }
            CanvasIntent.Undo -> _state.update { it.copy(editor = it.editor.undo()) }
            CanvasIntent.Redo -> _state.update { it.copy(editor = it.editor.redo()) }

            is CanvasIntent.SelectSideTab -> _state.update { s ->
                s.copy(
                    activeSideTab = intent.tab,
                    isDesktopPanelOpen = true,
                    activeModal = if (intent.openMobileSheet) ActiveModal.MOBILE_SHEET else s.activeModal,
                )
            }
            CanvasIntent.ToggleDesktopPanel -> _state.update {
                it.copy(isDesktopPanelOpen = !it.isDesktopPanelOpen)
            }
            is CanvasIntent.OpenModal -> _state.update {
                it.copy(activeModal = intent.modal)
            }

            is CanvasIntent.UpdateProjectMeta -> _state.update { s ->
                val updatedDoc = s.editor.document.copy(
                    clientNameFa = intent.clientNameFa.take(500),
                    notesFa = intent.notesFa.take(4000),
                )
                s.copy(
                    projectNameFa = intent.nameFa.take(120),
                    editor = s.editor.copy(document = updatedDoc),
                )
            }
            is CanvasIntent.SaveProject -> saveProject(intent.asCopy, intent.customName)

            is CanvasIntent.RequestLoadSaved -> requestOpen(PendingOpenAction.Saved(intent.id))
            is CanvasIntent.RequestLoadPreset -> requestOpen(PendingOpenAction.Preset(intent.preset))
            is CanvasIntent.RequestNewBlank -> requestOpen(
                PendingOpenAction.Blank(intent.nameFa, intent.clientNameFa, intent.withStarterWalls)
            )
            CanvasIntent.ConfirmDiscardPending -> {
                val pending = _state.value.pendingOpen ?: return
                _state.update { it.copy(pendingOpen = null) }
                executeOpen(pending)
            }
            CanvasIntent.CancelDiscardPending -> _state.update { it.copy(pendingOpen = null) }

            is CanvasIntent.RequestDeleteSaved -> _state.update {
                it.copy(pendingDeleteProjectId = intent.id)
            }
            CanvasIntent.ConfirmDeleteSaved -> deletePendingProject()
            CanvasIntent.CancelDeleteSaved -> _state.update { it.copy(pendingDeleteProjectId = null) }

            is CanvasIntent.ExportDocument -> exportDocument(intent.uri, intent.format, intent.defaultTitle)
            CanvasIntent.ClearMessage -> _state.update { it.copy(messageRes = null) }
        }
    }

    private fun requestOpen(action: PendingOpenAction) {
        if (_state.value.busy) return
        if (_state.value.dirty) {
            _state.update { it.copy(pendingOpen = action) }
        } else {
            executeOpen(action)
        }
    }

    private fun executeOpen(action: PendingOpenAction) {
        when (action) {
            is PendingOpenAction.Preset -> {
                val doc = action.template.document
                val vp = doc.bounds?.let { Viewport.fitBounds(it, 412f, 680f, 44f) } ?: Viewport()
                _state.update { s ->
                    s.copy(
                        projectId = null,
                        projectNameFa = action.template.nameFa,
                        savedDocument = doc,
                        editor = s.editor.loadDocument(doc).copy(viewport = vp),
                        activeModal = null,
                        messageRes = R.string.loaded,
                    )
                }
            }
            is PendingOpenAction.Blank -> {
                val walls = if (action.withStarterWalls) {
                    listOf(
                        WallLine("w_1", Point(80f, 80f), Point(440f, 80f), 15f, "دیوار اصلی", true),
                        WallLine("w_2", Point(80f, 80f), Point(80f, 340f), 15f, "دیوار فرعی", true),
                    )
                } else emptyList()
                val doc = ProjectDocument(
                    lines = walls,
                    cabinets = emptyList(),
                    clientNameFa = action.clientNameFa.trim(),
                )
                val vp = doc.bounds?.let { Viewport.fitBounds(it, 412f, 680f, 44f) } ?: Viewport()
                _state.update { s ->
                    s.copy(
                        projectId = null,
                        projectNameFa = action.nameFa.trim().ifBlank { "طرح کابینت جدید" },
                        savedDocument = doc,
                        editor = s.editor.loadDocument(doc).copy(viewport = vp),
                        activeModal = null,
                        messageRes = R.string.new_created,
                    )
                }
            }
            is PendingOpenAction.Saved -> loadSavedFromRoom(action.id)
        }
    }

    private fun saveProject(asCopy: Boolean, customName: String?) {
        val current = _state.value
        if (current.busy) return
        val name = (customName ?: current.projectNameFa).trim().take(120).ifBlank { "طرح کابینت جدید" }
        val doc = current.editor.document
        val targetId = if (asCopy || current.projectId == null) UUID.randomUUID().toString() else current.projectId

        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val entity = withContext(dispatcher) {
                    ProjectEntity(
                        id = targetId,
                        name = name,
                        documentJson = DocumentCodec.encode(doc),
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                store.save(entity)
                _state.update { s ->
                    s.copy(
                        projectId = entity.id,
                        projectNameFa = entity.name,
                        savedDocument = doc,
                        busy = false,
                        messageRes = R.string.saved,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(busy = false, messageRes = R.string.save_failed) }
            }
        }
    }

    private fun loadSavedFromRoom(id: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val entity = store.load(id) ?: error("Missing project")
                val doc = withContext(dispatcher) { DocumentCodec.decode(entity.documentJson) }
                val vp = doc.bounds?.let { Viewport.fitBounds(it, 412f, 680f, 44f) } ?: Viewport()
                _state.update { s ->
                    s.copy(
                        projectId = entity.id,
                        projectNameFa = entity.name,
                        savedDocument = doc,
                        editor = s.editor.loadDocument(doc).copy(viewport = vp),
                        activeModal = null,
                        busy = false,
                        messageRes = R.string.loaded,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(busy = false, messageRes = R.string.load_failed) }
            }
        }
    }

    private fun deletePendingProject() {
        val targetId = _state.value.pendingDeleteProjectId ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, pendingDeleteProjectId = null) }
        viewModelScope.launch {
            try {
                store.delete(targetId)
                _state.update { s ->
                    val sameOpen = s.projectId == targetId
                    s.copy(
                        projectId = if (sameOpen) null else s.projectId,
                        busy = false,
                        messageRes = R.string.project_deleted,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(busy = false, messageRes = R.string.delete_failed) }
            }
        }
    }

    private fun exportDocument(uri: Uri, format: ExportFormat, defaultTitle: String) {
        val current = _state.value
        if (current.busy || !current.editor.document.hasGeometry) return
        val title = current.projectNameFa.ifBlank { defaultTitle }
        val doc = current.editor.document
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                exporter.write(uri, doc, title, format)
                _state.update { it.copy(busy = false, activeModal = null, messageRes = R.string.exported) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(busy = false, messageRes = R.string.export_failed) }
            }
        }
    }
}
