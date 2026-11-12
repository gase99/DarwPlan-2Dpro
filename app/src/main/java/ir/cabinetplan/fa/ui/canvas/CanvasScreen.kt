package ir.cabinetplan.fa.ui.canvas

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import ir.cabinetplan.fa.export.ExportFormat
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.cabinetplan.fa.R
import ir.cabinetplan.fa.domain.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(viewModel: CanvasViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val send = viewModel::onIntent
    val editor = state.editor
    val compact = LocalConfiguration.current.screenHeightDp < 480
    var canvasBounds by remember { mutableStateOf(Rect.Zero) }
    val snackbar = remember { SnackbarHostState() }
    val defaultName = stringResource(R.string.project_title)
    val context = LocalContext.current
    var exitPrompt by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = state.dirty && !state.busy) { exitPrompt = true }
    val message = state.message?.let { stringResource(it) }
    LaunchedEffect(message) {
        if (message != null) { snackbar.showSnackbar(message); send(CanvasIntent.ClearMessage) }
    }
    val png = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) send(CanvasIntent.Export(uri, ExportFormat.PNG, defaultName))
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) send(CanvasIntent.Export(uri, ExportFormat.PDF, defaultName))
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(if (compact) Modifier.statusBarsPadding() else Modifier) {
                if (!compact) TopAppBar(title = {
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                        Text(state.projectName.ifBlank { defaultName } + if (state.dirty) stringResource(R.string.unsaved_mark) else "",
                            style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }, actions = {
                    TextButton(onClick = { send(CanvasIntent.ShowHelp) }) { Text(stringResource(R.string.help)) }
                })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { send(CanvasIntent.NewProject) }) { Text(stringResource(R.string.new_project)) }
                    TextButton(onClick = { send(CanvasIntent.RequestSave) }) { Text(stringResource(R.string.save)) }
                    if (compact) TextButton(onClick = { send(CanvasIntent.ShowHelp) }) { Text(stringResource(R.string.help)) }
                    TextButton(onClick = { send(CanvasIntent.Browse) }) { Text(stringResource(R.string.projects)) }
                    TextButton(onClick = { png.launch("${state.projectName.ifBlank { defaultName }}.png") }, enabled = editor.document.lines.isNotEmpty()) { Text(stringResource(R.string.export_png)) }
                    TextButton(onClick = { pdf.launch("${state.projectName.ifBlank { defaultName }}.pdf") }, enabled = editor.document.lines.isNotEmpty()) { Text(stringResource(R.string.export_pdf)) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Tool.entries.filter { it != Tool.PLACE }.forEach { tool ->
                        FilterChip(selected = editor.tool == tool, onClick = { send(CanvasIntent.SetTool(tool)) },
                            label = { Text(stringResource(when (tool) {
                                Tool.PAN -> R.string.tool_pan
                                Tool.LINE -> R.string.tool_line
                                else -> R.string.tool_select
                            })) }, modifier = Modifier.padding(horizontal = 3.dp))
                    }
                    FilterChip(selected = state.libraryVisible, onClick = { send(CanvasIntent.ToggleLibrary) },
                        label = { Text(stringResource(R.string.library)) })
                }
                if (state.libraryVisible) {
                    if (!compact) Text(stringResource(R.string.library_hint), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CabinetKind.entries.forEach { kind ->
                            LibraryCard(kind, editor.tool == Tool.PLACE && editor.cabinet == kind,
                                onClick = { send(CanvasIntent.ChooseCabinet(kind)) }, onDrop = { root ->
                                    if (canvasBounds.contains(root)) {
                                        val local = root - canvasBounds.topLeft
                                        send(CanvasIntent.Place(kind, editor.viewport.screenToWorld(Point(local.x, local.y),
                                            Point(canvasBounds.width / 2, canvasBounds.height / 2))))
                                    }
                                })
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { send(CanvasIntent.Undo) }, enabled = editor.past.isNotEmpty()) { Text(stringResource(R.string.undo)) }
                        TextButton(onClick = { send(CanvasIntent.Redo) }, enabled = editor.future.isNotEmpty()) { Text(stringResource(R.string.redo)) }
                        TextButton(onClick = { send(CanvasIntent.EditSize) }, enabled = editor.selected.isNotEmpty()) { Text(stringResource(R.string.edit_size)) }
                        TextButton(onClick = { send(CanvasIntent.Delete) }, enabled = editor.selected.isNotEmpty()) { Text(stringResource(R.string.delete_selected)) }
                        FilterChip(selected = editor.multiple, onClick = { send(CanvasIntent.ToggleMultiple) }, label = { Text(stringResource(R.string.multiple)) })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = editor.gridSnap, onClick = { send(CanvasIntent.ToggleGrid) }, label = { Text(stringResource(R.string.snap_grid)) })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = editor.dimensions, onClick = { send(CanvasIntent.ToggleDimensions) }, label = { Text(stringResource(R.string.dimensions)) })
                    }
                    Text(stringResource(when (editor.tool) {
                        Tool.PAN -> R.string.pan_hint
                        Tool.LINE -> R.string.line_hint
                        Tool.SELECT -> if (editor.multiple) R.string.multiple_hint else R.string.select_hint
                        Tool.PLACE -> R.string.place_hint
                    }), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PlanCanvas(editor, send, Modifier.fillMaxSize()) { canvasBounds = it }
            Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp), tonalElevation = 4.dp, shape = MaterialTheme.shapes.large) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZoomButton(R.string.zoom_in, R.string.zoom_in_symbol) { send(CanvasIntent.Zoom(1.25f)) }
                    Text(stringResource(R.string.zoom_value, (editor.viewport.zoom * 100).roundToInt().fa()))
                    ZoomButton(R.string.zoom_out, R.string.zoom_out_symbol) { send(CanvasIntent.Zoom(.8f)) }
                    TextButton(onClick = { send(CanvasIntent.Fit(canvasBounds.width, canvasBounds.height)) }) { Text(stringResource(R.string.fit)) }
                    ZoomButton(R.string.reset_view, R.string.reset_symbol) { send(CanvasIntent.ResetView) }
                }
            }
        }
    }
    ProjectDialogs(state, send)
    if (exitPrompt) AlertDialog(onDismissRequest = { exitPrompt = false },
        title = { Text(stringResource(R.string.unsaved_title)) }, text = { Text(stringResource(R.string.exit_unsaved)) },
        confirmButton = { TextButton(onClick = { context.activity()?.finish() }) { Text(stringResource(R.string.exit)) } },
        dismissButton = { TextButton(onClick = { exitPrompt = false }) { Text(stringResource(R.string.cancel)) } })
    if (state.busy) AlertDialog(onDismissRequest = {},
        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.size(32.dp)); Text(stringResource(R.string.please_wait))
        } }, confirmButton = {})
    if (state.helpVisible) AlertDialog(onDismissRequest = { send(CanvasIntent.DismissHelp) },
        title = { Text(stringResource(R.string.help_title)) },
        text = { Text(stringResource(R.string.help_body), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { send(CanvasIntent.DismissHelp) }) { Text(stringResource(R.string.understood)) } })
}

@Composable
private fun ZoomButton(label: Int, symbol: Int, onClick: () -> Unit) {
    val description = stringResource(label)
    TextButton(onClick = onClick, modifier = Modifier.sizeIn(minWidth = 48.dp).semantics { contentDescription = description }) {
        Text(stringResource(symbol))
    }
}

@Composable
private fun LibraryCard(kind: CabinetKind, selected: Boolean, onClick: () -> Unit, onDrop: (Offset) -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val drop by rememberUpdatedState(onDrop)
    ElevatedCard(onClick = onClick, modifier = Modifier.widthIn(min = 140.dp)
        .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
        .pointerInput(kind) {
            detectDragGesturesAfterLongPress(onDragStart = { drag = origin + it; dragging = true },
                onDragEnd = { drop(drag); dragging = false }, onDragCancel = { dragging = false },
                onDrag = { change, amount -> change.consume(); drag += amount })
        }, colors = CardDefaults.elevatedCardColors(containerColor = if (selected || dragging)
            MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(cabinetLabel(kind)), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.object_dimensions, kind.width.roundToInt().fa(), kind.depth.roundToInt().fa()),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
