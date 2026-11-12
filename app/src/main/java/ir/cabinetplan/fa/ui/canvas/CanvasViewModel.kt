package ir.cabinetplan.fa.ui.canvas

import androidx.lifecycle.ViewModel
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
) {
    val viewport: Viewport get() = editor.viewport
}

sealed interface CanvasIntent {
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
class CanvasViewModel @Inject constructor() : ViewModel() {
    private val mutableState = MutableStateFlow(CanvasState())
    val state = mutableState.asStateFlow()

    fun onIntent(intent: CanvasIntent) {
        mutableState.update { state ->
            val editor = state.editor
            when (intent) {
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
