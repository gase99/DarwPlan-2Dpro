package ir.cabinetplan.fa.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.cabinetplan.fa.R
import ir.cabinetplan.fa.domain.Point
import ir.cabinetplan.fa.domain.Viewport
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private fun Int.persianDigits(): String = toString().map { char ->
    if (char in '0'..'9') '۰' + (char - '0') else char
}.joinToString("")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(viewModel: CanvasViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val send = viewModel::onIntent
    val zoom = (state.viewport.zoom * 100).roundToInt().persianDigits()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.phase_label), style = MaterialTheme.typography.labelMedium)
                    }
                },
                actions = {
                    CanvasButton(R.string.help, R.string.help_symbol) { send(CanvasIntent.ShowHelp) }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.gesture_hint), style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.phase_notice),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PlanCanvas(state.viewport, send, Modifier.fillMaxSize())
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 3.dp,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.project_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.grid_hint, Viewport.GRID_MILLIMETERS.persianDigits()),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp,
                tonalElevation = 3.dp,
            ) {
                Row(
                    Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CanvasButton(R.string.zoom_in, R.string.zoom_in_symbol) { send(CanvasIntent.Zoom(1.25f)) }
                    Text(
                        stringResource(R.string.zoom_value, zoom),
                        modifier = Modifier.widthIn(min = 56.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    CanvasButton(R.string.zoom_out, R.string.zoom_out_symbol) { send(CanvasIntent.Zoom(0.8f)) }
                    CanvasButton(R.string.reset_view, R.string.reset_symbol) { send(CanvasIntent.ResetView) }
                }
            }
        }
    }
    if (state.helpVisible) {
        AlertDialog(
            onDismissRequest = { send(CanvasIntent.DismissHelp) },
            title = { Text(stringResource(R.string.help_title)) },
            text = { Text(stringResource(R.string.help_body)) },
            confirmButton = {
                TextButton(onClick = { send(CanvasIntent.DismissHelp) }) {
                    Text(stringResource(R.string.understood))
                }
            },
        )
    }
}

@Composable
private fun CanvasButton(label: Int, symbol: Int, onClick: () -> Unit) {
    val description = stringResource(label)
    IconButton(
        onClick = onClick,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = description },
    ) {
        Text(stringResource(symbol), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun PlanCanvas(viewport: Viewport, send: (CanvasIntent) -> Unit, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val originColor = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.canvas_description)
    val title = stringResource(R.string.canvas_title)
    val zoomDescription = stringResource(
        R.string.canvas_state, (viewport.zoom * 100).roundToInt().persianDigits(),
    )
    Canvas(
        modifier.background(MaterialTheme.colorScheme.background)
            .semantics {
                contentDescription = "$title. $description"
                stateDescription = zoomDescription
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    send(
                        CanvasIntent.Transform(
                            centroid = Point(centroid.x - size.width / 2f, centroid.y - size.height / 2f),
                            pan = Point(pan.x, pan.y),
                            zoom = zoom,
                        ),
                    )
                }
            },
    ) {
        val center = Point(size.width / 2f, size.height / 2f)
        val topLeft = viewport.screenToWorld(Point(), center)
        val bottomRight = viewport.screenToWorld(Point(size.width, size.height), center)
        val step = Viewport.GRID_MILLIMETERS.toFloat()
        val firstX = floor(topLeft.x / step).toInt()
        val lastX = ceil(bottomRight.x / step).toInt()
        val firstY = floor(topLeft.y / step).toInt()
        val lastY = ceil(bottomRight.y / step).toInt()
        // Only visible grid lines are drawn; work remains bounded at minimum zoom.
        for (index in firstX..lastX) {
            val x = viewport.worldToScreen(Point(index * step, 0f), center).x
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }
        for (index in firstY..lastY) {
            val y = viewport.worldToScreen(Point(0f, index * step), center).y
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        val origin = viewport.worldToScreen(Point(), center)
        drawLine(axisColor, Offset(origin.x, 0f), Offset(origin.x, size.height), 1.5.dp.toPx())
        drawLine(axisColor, Offset(0f, origin.y), Offset(size.width, origin.y), 1.5.dp.toPx())
        drawCircle(originColor, radius = 4.dp.toPx(), center = Offset(origin.x, origin.y))
    }
}
