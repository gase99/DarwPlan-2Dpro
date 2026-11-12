package ir.cabinetplan.fa.ui.canvas

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.cabinetplan.fa.domain.Point
import ir.cabinetplan.fa.domain.Viewport
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CanvasState(
    val viewport: Viewport = Viewport(),
    val helpVisible: Boolean = false,
)

sealed interface CanvasIntent {
    data class Transform(val centroid: Point, val pan: Point, val zoom: Float) : CanvasIntent
    data class Zoom(val factor: Float) : CanvasIntent
    data object ResetView : CanvasIntent
    data object ShowHelp : CanvasIntent
    data object DismissHelp : CanvasIntent
}

/** Single state owner; composables only render state and send intents. */
@HiltViewModel
class CanvasViewModel @Inject constructor() : ViewModel() {
    private val mutableState = MutableStateFlow(CanvasState())
    val state = mutableState.asStateFlow()

    fun onIntent(intent: CanvasIntent) {
        mutableState.update { state ->
            when (intent) {
                is CanvasIntent.Transform -> state.copy(
                    viewport = state.viewport.transform(intent.centroid, intent.pan, intent.zoom),
                )
                is CanvasIntent.Zoom -> state.copy(
                    viewport = state.viewport.transform(Point(), Point(), intent.factor),
                )
                CanvasIntent.ResetView -> state.copy(viewport = Viewport())
                CanvasIntent.ShowHelp -> state.copy(helpVisible = true)
                CanvasIntent.DismissHelp -> state.copy(helpVisible = false)
            }
        }
    }
}
