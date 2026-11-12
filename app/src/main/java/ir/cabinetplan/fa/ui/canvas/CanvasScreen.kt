package ir.cabinetplan.fa.ui.canvas

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.cabinetplan.fa.R
import ir.cabinetplan.fa.domain.*
import ir.cabinetplan.fa.export.ExportFormat
import ir.cabinetplan.fa.ui.icons.CadIcons
import ir.cabinetplan.fa.ui.theme.CadPalette
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(viewModel: CanvasViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val send = viewModel::onIntent
    val editor = state.editor
    val config = LocalConfiguration.current
    val isWideScreen = config.screenWidthDp >= 720
    val context = LocalContext.current

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var initialFitDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(canvasSize) {
        if (!initialFitDone && canvasSize.width > 100 && canvasSize.height > 100) {
            initialFitDone = true
            send(CanvasIntent.FitToScreen(canvasSize.width.toFloat(), canvasSize.height.toFloat()))
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val defaultTitle = stringResource(R.string.project_title)
    var exitPrompt by rememberSaveable { mutableStateOf(false) }

    val canInterceptBack = state.activeModal != null ||
        editor.activeTool == ToolMode.PLACE_CABINET ||
        editor.totalSelectedCount > 0 ||
        (state.dirty && !state.busy)

    BackHandler(enabled = canInterceptBack) {
        when {
            state.activeModal != null -> send(CanvasIntent.OpenModal(null))
            editor.activeTool == ToolMode.PLACE_CABINET -> send(CanvasIntent.SetTool(ToolMode.SELECT))
            editor.totalSelectedCount > 0 -> send(CanvasIntent.ClearSelection)
            state.dirty -> exitPrompt = true
        }
    }

    val messageText = state.messageRes?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            send(CanvasIntent.ClearMessage)
        }
    }

    val pngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) send(CanvasIntent.ExportDocument(uri, ExportFormat.PNG, defaultTitle))
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) send(CanvasIntent.ExportDocument(uri, ExportFormat.PDF, defaultTitle))
    }

    Scaffold(
        containerColor = CadPalette.BgCanvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CadPalette.BgSurface)
                    .statusBarsPadding()
            ) {
                // Top Brand & Project Action Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CadPalette.AccentOak),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("ک", fontWeight = FontWeight.Black, fontSize = 15.sp, color = CadPalette.BgCanvas)
                        }

                        Surface(
                            onClick = { send(CanvasIntent.OpenModal(ActiveModal.PROJECTS)) },
                            shape = RoundedCornerShape(10.dp),
                            color = CadPalette.BgElevated,
                            border = BorderStroke(1.dp, CadPalette.BorderHairline),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(CadIcons.Projects, contentDescription = null, tint = CadPalette.AccentOak, modifier = Modifier.size(16.dp))
                                Text(
                                    text = state.projectNameFa.ifBlank { defaultTitle } +
                                        if (state.dirty) stringResource(R.string.unsaved_mark) else "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CadPalette.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            onClick = { send(CanvasIntent.SaveProject()) },
                            shape = RoundedCornerShape(10.dp),
                            color = CadPalette.AccentEmerald,
                        ) {
                            Text(
                                text = stringResource(R.string.save),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CadPalette.BgCanvas,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }

                        if (isWideScreen) {
                            Surface(
                                onClick = { send(CanvasIntent.OpenModal(ActiveModal.EXPORT)) },
                                shape = RoundedCornerShape(10.dp),
                                color = CadPalette.BgElevated,
                                border = BorderStroke(1.dp, CadPalette.BorderHairline),
                            ) {
                                Text(
                                    text = stringResource(R.string.tab_export),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CadPalette.TextPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                            Surface(
                                onClick = { send(CanvasIntent.ToggleDesktopPanel) },
                                shape = RoundedCornerShape(10.dp),
                                color = CadPalette.BgElevated,
                                border = BorderStroke(1.dp, CadPalette.BorderHairline),
                            ) {
                                Text(
                                    text = if (state.isDesktopPanelOpen) "بستن پنل" else "باز کردن پنل",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CadPalette.AccentCyan,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }

                        Surface(
                            onClick = { send(CanvasIntent.OpenModal(ActiveModal.HELP)) },
                            shape = RoundedCornerShape(10.dp),
                            color = CadPalette.BgElevated,
                            border = BorderStroke(1.dp, CadPalette.BorderHairline),
                        ) {
                            Text(
                                text = stringResource(R.string.help),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = CadPalette.TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(color = CadPalette.BorderHairline)

                // Scrollable CAD Drafting Tool Ribbon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ToolPill(
                        label = stringResource(R.string.tool_select),
                        icon = CadIcons.Select,
                        active = editor.activeTool == ToolMode.SELECT,
                        activeColor = CadPalette.AccentOak,
                        onClick = { send(CanvasIntent.SetTool(ToolMode.SELECT)) },
                    )
                    ToolPill(
                        label = stringResource(R.string.tool_draw_wall),
                        icon = CadIcons.DrawWall,
                        active = editor.activeTool == ToolMode.DRAW_WALL,
                        activeColor = CadPalette.AccentOak,
                        onClick = { send(CanvasIntent.SetTool(ToolMode.DRAW_WALL)) },
                    )
                    ToolActionChip(
                        label = stringResource(R.string.undo),
                        icon = CadIcons.Undo,
                        enabled = editor.past.isNotEmpty(),
                        onClick = { send(CanvasIntent.Undo) },
                    )
                    ToolActionChip(
                        label = stringResource(R.string.redo),
                        icon = CadIcons.Redo,
                        enabled = editor.future.isNotEmpty(),
                        onClick = { send(CanvasIntent.Redo) },
                    )

                    VerticalDivider(modifier = Modifier.height(22.dp), color = CadPalette.BorderHairline)

                    ToolPill(
                        label = stringResource(R.string.tool_measure),
                        icon = CadIcons.Measure,
                        active = editor.activeTool == ToolMode.MEASURE,
                        activeColor = CadPalette.AccentEmerald,
                        onClick = { send(CanvasIntent.SetTool(ToolMode.MEASURE)) },
                    )
                    ToolPill(
                        label = stringResource(R.string.tool_pan),
                        icon = CadIcons.Pan,
                        active = editor.activeTool == ToolMode.PAN,
                        activeColor = CadPalette.AccentOak,
                        onClick = { send(CanvasIntent.SetTool(ToolMode.PAN)) },
                    )
                    ToolPill(
                        label = stringResource(R.string.multiple),
                        icon = CadIcons.MultiSelect,
                        active = editor.multiSelectMode,
                        activeColor = CadPalette.AccentCyan,
                        onClick = { send(CanvasIntent.ToggleMultiSelect) },
                    )
                    ToolPill(
                        label = stringResource(R.string.snap_magnetic),
                        icon = CadIcons.Magnet,
                        active = editor.snapEnabled,
                        activeColor = CadPalette.AccentEmerald,
                        onClick = { send(CanvasIntent.ToggleSnap) },
                    )
                    ToolPill(
                        label = stringResource(R.string.angle_lock),
                        icon = CadIcons.AngleLock,
                        active = editor.angleSnapEnabled,
                        activeColor = CadPalette.AccentCyan,
                        onClick = { send(CanvasIntent.ToggleAngleLock) },
                    )
                }

                HorizontalDivider(color = CadPalette.BorderHairline)
            }
        },
        bottomBar = {
            if (!isWideScreen) {
                MobileBottomBar(
                    state = state,
                    onIntent = send,
                )
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isWideScreen && state.isDesktopPanelOpen) {
                InspectorPanel(
                    editor = editor,
                    activeTab = state.activeSideTab,
                    canvasSize = canvasSize,
                    onIntent = send,
                    modifier = Modifier.width(330.dp),
                )
                VerticalDivider(color = CadPalette.BorderHairline)
            }

            // Interactive 2D / Elevation Canvas + Floating HUDs
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                CabinetBlueprintCanvas(
                    editor = editor,
                    onIntent = send,
                    onCanvasSize = { canvasSize = it },
                    modifier = Modifier.fillMaxSize(),
                )

                // Top-Right View Mode & Layer Visibility Pills
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val isElev = editor.viewMode == ViewMode.ELEVATION_FRONT
                    OverlayPill(
                        text = if (isElev) stringResource(R.string.view_plan_2d) else stringResource(R.string.view_elevation),
                        active = isElev,
                        activeBorder = CadPalette.AccentOak,
                        activeText = CadPalette.AccentOak,
                        onClick = {
                            send(
                                CanvasIntent.SetViewMode(
                                    if (isElev) ViewMode.PLAN_2D else ViewMode.ELEVATION_FRONT
                                )
                            )
                        },
                    )
                    OverlayPill(
                        text = stringResource(R.string.dimensions),
                        active = editor.showDimensions,
                        activeBorder = CadPalette.AccentCyan,
                        activeText = CadPalette.AccentCyan,
                        onClick = { send(CanvasIntent.ToggleDimensions) },
                    )
                    OverlayPill(
                        text = "${stringResource(R.string.wall_layer)}: ${if (editor.showWallCabinets) "روشن" else "مخفی"}",
                        active = editor.showWallCabinets,
                        activeBorder = CadPalette.AccentOak,
                        activeText = CadPalette.AccentOak,
                        onClick = { send(CanvasIntent.ToggleWallLayer) },
                    )
                    OverlayPill(
                        text = "${stringResource(R.string.base_layer)}: ${if (editor.showBaseCabinets) "روشن" else "مخفی"}",
                        active = editor.showBaseCabinets,
                        activeBorder = CadPalette.AccentEmerald,
                        activeText = CadPalette.AccentEmerald,
                        onClick = { send(CanvasIntent.ToggleBaseLayer) },
                    )
                }

                // Placement Banner when PLACE_CABINET is active
                if (editor.activeTool == ToolMode.PLACE_CABINET) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = CadPalette.AccentOak,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 48.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "محل «${editor.pendingCatalogItem.nameFa}» را روی بوم لمس کنید",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CadPalette.BgCanvas,
                            )
                            TextButton(
                                onClick = { send(CanvasIntent.SetTool(ToolMode.SELECT)) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp),
                            ) {
                                Text(stringResource(R.string.cancel), fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = CadPalette.BgCanvas)
                            }
                        }
                    }
                }

                // Floating Quick-Action Bar for Selected Element(s)
                if (editor.totalSelectedCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CadPalette.BgSurface.copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, CadPalette.AccentCyan.copy(alpha = 0.6f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 10.dp, bottom = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (editor.selectedCabinetIds.isNotEmpty()) {
                                QuickMiniButton(
                                    text = stringResource(R.string.rotate_90),
                                    color = CadPalette.AccentOak,
                                    onClick = { send(CanvasIntent.RotateSelected90) },
                                )
                            }
                            if (!isWideScreen) {
                                QuickMiniButton(
                                    text = stringResource(R.string.tab_inspector),
                                    color = CadPalette.AccentCyan,
                                    onClick = {
                                        send(CanvasIntent.SelectSideTab(SideTab.INSPECTOR, openMobileSheet = true))
                                    },
                                )
                            }
                            QuickMiniButton(
                                text = stringResource(R.string.duplicate),
                                color = CadPalette.AccentEmerald,
                                onClick = { send(CanvasIntent.DuplicateSelected) },
                            )
                            QuickMiniButton(
                                text = stringResource(R.string.delete_selected),
                                color = CadPalette.AccentRed,
                                onClick = { send(CanvasIntent.DeleteSelected) },
                            )
                        }
                    }
                }

                // Bottom-Left Zoom & Fit HUD
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CadPalette.BgSurface.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, CadPalette.BorderHairline),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(
                            onClick = { send(CanvasIntent.ZoomBy(0.84f)) },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(30.dp),
                        ) {
                            Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CadPalette.TextPrimary)
                        }
                        Text(
                            text = stringResource(
                                R.string.zoom_value,
                                (editor.viewport.zoom * 100f).roundToInt().fa()
                            ),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CadPalette.AccentOak,
                        )
                        TextButton(
                            onClick = { send(CanvasIntent.ZoomBy(1.18f)) },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(30.dp),
                        ) {
                            Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CadPalette.TextPrimary)
                        }
                        IconButton(
                            onClick = {
                                if (canvasSize.width > 0 && canvasSize.height > 0) {
                                    send(CanvasIntent.FitToScreen(canvasSize.width.toFloat(), canvasSize.height.toFloat()))
                                } else {
                                    send(CanvasIntent.ResetView)
                                }
                            },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                CadIcons.FitView,
                                contentDescription = stringResource(R.string.fit),
                                tint = CadPalette.AccentCyan,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Mobile Bottom Sheet for Library / Inspector / BOM / Projects / Export
    when (state.activeModal) {
        ActiveModal.MOBILE_SHEET -> {
            ModalBottomSheet(
                onDismissRequest = { send(CanvasIntent.OpenModal(null)) },
                containerColor = CadPalette.BgSurface,
                contentColor = CadPalette.TextPrimary,
            ) {
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.76f)) {
                    InspectorPanel(
                        editor = editor,
                        activeTab = state.activeSideTab,
                        canvasSize = canvasSize,
                        onIntent = send,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        ActiveModal.PROJECTS -> {
            ModalBottomSheet(
                onDismissRequest = { send(CanvasIntent.OpenModal(null)) },
                containerColor = CadPalette.BgSurface,
                contentColor = CadPalette.TextPrimary,
            ) {
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.84f)) {
                    ProjectsManagerContent(
                        state = state,
                        onIntent = send,
                        onClose = { send(CanvasIntent.OpenModal(null)) },
                    )
                }
            }
        }

        ActiveModal.EXPORT -> {
            ModalBottomSheet(
                onDismissRequest = { send(CanvasIntent.OpenModal(null)) },
                containerColor = CadPalette.BgSurface,
                contentColor = CadPalette.TextPrimary,
            ) {
                val safeFileName = state.projectNameFa.ifBlank { defaultTitle }.replace(' ', '-')
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.80f)) {
                    ExportSheetContent(
                        state = state,
                        onExportPng = { pngLauncher.launch("$safeFileName.png") },
                        onExportPdf = { pdfLauncher.launch("$safeFileName.pdf") },
                        onClose = { send(CanvasIntent.OpenModal(null)) },
                    )
                }
            }
        }

        ActiveModal.HELP -> {
            AlertDialog(
                onDismissRequest = { send(CanvasIntent.OpenModal(null)) },
                containerColor = CadPalette.BgSurface,
                title = { Text(stringResource(R.string.help_title), fontWeight = FontWeight.ExtraBold) },
                text = { Text(stringResource(R.string.help_body), fontSize = 13.sp, lineHeight = 21.sp) },
                confirmButton = {
                    Button(onClick = { send(CanvasIntent.OpenModal(null)) }) {
                        Text(stringResource(R.string.understood), fontWeight = FontWeight.Bold)
                    }
                },
            )
        }

        null -> Unit
    }

    // Confirmation Dialog: Discard Unsaved Changes
    if (state.pendingOpen != null) {
        AlertDialog(
            onDismissRequest = { send(CanvasIntent.CancelDiscardPending) },
            containerColor = CadPalette.BgSurface,
            title = { Text(stringResource(R.string.unsaved_title), fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.unsaved_body)) },
            confirmButton = {
                Button(
                    onClick = { send(CanvasIntent.ConfirmDiscardPending) },
                    colors = ButtonDefaults.buttonColors(containerColor = CadPalette.AccentRed),
                ) {
                    Text(stringResource(R.string.discard), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { send(CanvasIntent.CancelDiscardPending) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Confirmation Dialog: Delete Saved Project
    if (state.pendingDeleteProjectId != null) {
        AlertDialog(
            onDismissRequest = { send(CanvasIntent.CancelDeleteSaved) },
            containerColor = CadPalette.BgSurface,
            title = { Text(stringResource(R.string.delete_project), fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                Button(
                    onClick = { send(CanvasIntent.ConfirmDeleteSaved) },
                    colors = ButtonDefaults.buttonColors(containerColor = CadPalette.AccentRed),
                ) {
                    Text(stringResource(R.string.delete), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { send(CanvasIntent.CancelDeleteSaved) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Confirmation Dialog: Exit App with Unsaved Changes
    if (exitPrompt) {
        AlertDialog(
            onDismissRequest = { exitPrompt = false },
            containerColor = CadPalette.BgSurface,
            title = { Text(stringResource(R.string.unsaved_title), fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.exit_unsaved)) },
            confirmButton = {
                Button(
                    onClick = {
                        exitPrompt = false
                        context.findActivity()?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CadPalette.AccentRed),
                ) {
                    Text(stringResource(R.string.exit), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { exitPrompt = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun MobileBottomBar(
    state: CanvasUiState,
    onIntent: (CanvasIntent) -> Unit,
) {
    Surface(
        color = CadPalette.BgSurface,
        border = BorderStroke(1.dp, CadPalette.BorderHairline),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavButton(
                label = stringResource(R.string.tab_library),
                icon = CadIcons.Library,
                highlighted = state.activeModal == ActiveModal.MOBILE_SHEET && state.activeSideTab == SideTab.LIBRARY,
                onClick = { onIntent(CanvasIntent.SelectSideTab(SideTab.LIBRARY, openMobileSheet = true)) },
            )
            BottomNavButton(
                label = if (state.editor.totalSelectedCount > 0) {
                    "${stringResource(R.string.tab_inspector)} (${state.editor.totalSelectedCount.fa()})"
                } else {
                    stringResource(R.string.tab_inspector)
                },
                icon = CadIcons.Inspector,
                highlighted = state.activeModal == ActiveModal.MOBILE_SHEET && state.activeSideTab == SideTab.INSPECTOR,
                onClick = { onIntent(CanvasIntent.SelectSideTab(SideTab.INSPECTOR, openMobileSheet = true)) },
            )
            BottomNavButton(
                label = stringResource(R.string.tab_bom),
                icon = CadIcons.Bom,
                highlighted = state.activeModal == ActiveModal.MOBILE_SHEET && state.activeSideTab == SideTab.BOM,
                onClick = { onIntent(CanvasIntent.SelectSideTab(SideTab.BOM, openMobileSheet = true)) },
            )
            BottomNavButton(
                label = stringResource(R.string.tab_projects),
                icon = CadIcons.Projects,
                highlighted = state.activeModal == ActiveModal.PROJECTS,
                onClick = { onIntent(CanvasIntent.OpenModal(ActiveModal.PROJECTS)) },
            )
            BottomNavButton(
                label = stringResource(R.string.tab_export),
                icon = CadIcons.Export,
                highlighted = state.activeModal == ActiveModal.EXPORT,
                onClick = { onIntent(CanvasIntent.OpenModal(ActiveModal.EXPORT)) },
            )
        }
    }
}

@Composable
private fun BottomNavButton(
    label: String,
    icon: ImageVector,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (highlighted) CadPalette.AccentOak.copy(alpha = 0.2f) else Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) CadPalette.AccentOak else CadPalette.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = if (highlighted) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (highlighted) CadPalette.AccentOak else CadPalette.TextSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ToolPill(
    label: String,
    icon: ImageVector,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = if (active) activeColor else CadPalette.BgCanvas,
        border = BorderStroke(1.dp, if (active) activeColor else CadPalette.BorderHairline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) CadPalette.BgCanvas else CadPalette.TextSecondary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) CadPalette.BgCanvas else CadPalette.TextSecondary,
            )
        }
    }
}

@Composable
private fun ToolActionChip(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(9.dp),
        color = CadPalette.BgCanvas,
        border = BorderStroke(1.dp, CadPalette.BorderHairline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) CadPalette.TextPrimary else CadPalette.TextMuted.copy(alpha = 0.45f),
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) CadPalette.TextPrimary else CadPalette.TextMuted.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun OverlayPill(
    text: String,
    active: Boolean,
    activeBorder: Color,
    activeText: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = CadPalette.BgSurface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, if (active) activeBorder.copy(alpha = 0.7f) else CadPalette.BorderHairline),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) activeText else CadPalette.TextMuted,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun QuickMiniButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}
