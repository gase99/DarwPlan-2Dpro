package ir.cabinetplan.fa.ui.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.cabinetplan.fa.R
import ir.cabinetplan.fa.domain.*
import ir.cabinetplan.fa.ui.icons.CadIcons
import ir.cabinetplan.fa.ui.theme.CadPalette
import kotlin.math.roundToInt

@Composable
fun InspectorPanel(
    editor: CadEditorState,
    activeTab: SideTab,
    canvasSize: IntSize,
    onIntent: (CanvasIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(CadPalette.BgSurface)
            .fillMaxHeight()
    ) {
        // 3-Tab Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CadPalette.BgDeep)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TabChip(
                selected = activeTab == SideTab.LIBRARY,
                label = stringResource(R.string.tab_library),
                activeColor = CadPalette.AccentOak,
                onClick = { onIntent(CanvasIntent.SelectSideTab(SideTab.LIBRARY)) },
                modifier = Modifier.weight(1f),
            )
            TabChip(
                selected = activeTab == SideTab.INSPECTOR,
                label = if (editor.totalSelectedCount > 0) {
                    "${stringResource(R.string.tab_inspector)} (${editor.totalSelectedCount.fa()})"
                } else {
                    stringResource(R.string.tab_inspector)
                },
                activeColor = CadPalette.AccentCyan,
                onClick = { onIntent(CanvasIntent.SelectSideTab(SideTab.INSPECTOR)) },
                modifier = Modifier.weight(1f),
            )
            TabChip(
                selected = activeTab == SideTab.BOM,
                label = stringResource(R.string.tab_bom),
                activeColor = CadPalette.AccentEmerald,
                onClick = { onIntent(CanvasIntent.SelectSideTab(SideTab.BOM)) },
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(color = CadPalette.BorderHairline)

        when (activeTab) {
            SideTab.LIBRARY -> CatalogLibraryTab(canvasSize = canvasSize, onIntent = onIntent)
            SideTab.INSPECTOR -> ElementInspectorTab(editor = editor, onIntent = onIntent)
            SideTab.BOM -> CarpentryBomTab(editor = editor, onIntent = onIntent)
        }
    }
}

@Composable
private fun TabChip(
    selected: Boolean,
    label: String,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) activeColor else CadPalette.BgElevated,
        modifier = modifier.height(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) CadPalette.BgCanvas else CadPalette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CatalogLibraryTab(
    canvasSize: IntSize,
    onIntent: (CanvasIntent) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf<CabinetCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(selectedCategory, searchQuery) {
        filterCatalog(selectedCategory, searchQuery)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("جستجوی کابینت، سینک، کشو، جزیره...", fontSize = 12.sp, color = CadPalette.TextMuted)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.5.sp, color = CadPalette.TextPrimary),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("همه", fontSize = 11.sp) },
                )
                for (cat in CabinetCategory.entries) {
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.shortFa, fontSize = 11.sp) },
                    )
                }
            }
        }

        HorizontalDivider(color = CadPalette.BorderHairline)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.id }) { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CadPalette.BgElevated,
                    border = BorderStroke(1.dp, CadPalette.BorderHairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onIntent(
                                CanvasIntent.AddCatalogUnit(
                                    item = item,
                                    canvasWidthPx = canvasSize.width.toFloat(),
                                    canvasHeightPx = canvasSize.height.toFloat(),
                                )
                            )
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(item.fillArgb))
                                .border(1.5.dp, Color(item.strokeArgb), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(item.codeFa, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = CadPalette.TextPrimary)
                                Text(
                                    "${formatCm(item.defaultWidth, false)}×${formatCm(item.defaultDepth, false)}",
                                    fontSize = 9.sp,
                                    color = CadPalette.AccentAmber,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.nameFa,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = CadPalette.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.descriptionFa,
                                fontSize = 11.sp,
                                color = CadPalette.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "عرض: ${formatCm(item.defaultWidth)} • عمق: ${formatCm(item.defaultDepth)} • ارتفاع: ${formatCm(item.defaultHeight)}",
                                fontSize = 10.5.sp,
                                color = CadPalette.TextSecondary,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalIconButton(
                                onClick = {
                                    onIntent(
                                        CanvasIntent.AddCatalogUnit(
                                            item = item,
                                            canvasWidthPx = canvasSize.width.toFloat(),
                                            canvasHeightPx = canvasSize.height.toFloat(),
                                        )
                                    )
                                },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(CadIcons.Plus, contentDescription = "افزودن ${item.nameFa}", tint = CadPalette.AccentOak)
                            }
                            TextButton(
                                onClick = { onIntent(CanvasIntent.StartPlaceCatalogUnit(item)) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp),
                            ) {
                                Text("نقطه‌ای", fontSize = 10.sp, color = CadPalette.AccentCyan)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ElementInspectorTab(
    editor: CadEditorState,
    onIntent: (CanvasIntent) -> Unit,
) {
    val selectedLine = editor.selectedSingleLine
    val selectedCab = editor.selectedSingleCabinet
    var fineNudge by remember { mutableStateOf(false) }
    val nudgeStep = if (fineNudge) 1f else 10f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (editor.totalSelectedCount == 0) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CadPalette.BgElevated,
                border = BorderStroke(1.dp, CadPalette.BorderHairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(CadIcons.Measure, contentDescription = null, tint = CadPalette.AccentCyan, modifier = Modifier.size(30.dp))
                    Text("هیچ جزئی انتخاب نشده است", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Text(
                        "روی یک دیوار یا کابینت در نقشه ضربه بزنید تا طول دقیق، عرض، عمق، ارتفاع و چرخش آن فعال شود.",
                        fontSize = 12.sp,
                        color = CadPalette.TextMuted,
                    )
                }
            }
        }

        // Single Wall Inspector
        if (selectedLine != null) {
            var labelText by remember(selectedLine.id, selectedLine.label) { mutableStateOf(selectedLine.label) }
            var lengthText by remember(selectedLine.id, selectedLine.lengthCm) { mutableStateOf(formatCm(selectedLine.lengthCm, false)) }
            var thickText by remember(selectedLine.id, selectedLine.thickness) { mutableStateOf(formatCm(selectedLine.thickness, false)) }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CadPalette.BgElevated,
                border = BorderStroke(1.dp, CadPalette.AccentCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("ویرایش خط / دیوار", fontSize = 11.sp, color = CadPalette.AccentCyan, fontWeight = FontWeight.Bold)
                            Text(selectedLine.label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(
                            formatCm(selectedLine.lengthCm),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CadPalette.AccentCyan,
                        )
                    }

                    OutlinedTextField(
                        value = labelText,
                        onValueChange = {
                            labelText = it
                            onIntent(CanvasIntent.UpdateLine(selectedLine.copy(label = it.take(80))))
                        },
                        label = { Text("عنوان دیوار", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lengthText,
                            onValueChange = { txt ->
                                lengthText = txt
                                parseCentimeters(txt, minCm = 10f, maxCm = 10_000f)?.let { len ->
                                    onIntent(CanvasIntent.UpdateLine(selectedLine.withLength(len)))
                                }
                            },
                            label = { Text("طول (سم)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = thickText,
                            onValueChange = { txt ->
                                thickText = txt
                                parseCentimeters(txt, minCm = 2f, maxCm = 100f)?.let { th ->
                                    onIntent(CanvasIntent.UpdateLine(selectedLine.copy(thickness = th)))
                                }
                            },
                            label = { Text("ضخامت (سم)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Text(
                        text = "زاویه فعلی: ${formatAngle(selectedLine.angleDeg)} • شروع: (${selectedLine.start.x.roundToInt().fa()}، ${selectedLine.start.y.roundToInt().fa()})",
                        fontSize = 11.sp,
                        color = CadPalette.TextMuted,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onIntent(CanvasIntent.UpdateLine(selectedLine.straightened(horizontal = true))) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("تراز افقی (۰°)", fontSize = 11.5.sp)
                        }
                        OutlinedButton(
                            onClick = { onIntent(CanvasIntent.UpdateLine(selectedLine.straightened(horizontal = false))) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("تراز عمودی (۹۰°)", fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        // Single Cabinet Inspector
        if (selectedCab != null) {
            var nameText by remember(selectedCab.id, selectedCab.nameFa) { mutableStateOf(selectedCab.nameFa) }
            var widthText by remember(selectedCab.id, selectedCab.width) { mutableStateOf(formatCm(selectedCab.width, false)) }
            var depthText by remember(selectedCab.id, selectedCab.depth) { mutableStateOf(formatCm(selectedCab.depth, false)) }
            var heightText by remember(selectedCab.id, selectedCab.height) { mutableStateOf(formatCm(selectedCab.height, false)) }
            var elevText by remember(selectedCab.id, selectedCab.elevationCm) { mutableStateOf(formatCm(selectedCab.elevationCm, false)) }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CadPalette.BgElevated,
                border = BorderStroke(1.dp, CadPalette.AccentOak.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ویرایش یونیت کابینت", fontSize = 11.sp, color = CadPalette.AccentOak, fontWeight = FontWeight.Bold)
                            Text(selectedCab.nameFa, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        }
                        Text(selectedCab.codeFa, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CadPalette.AccentOak)
                    }

                    OutlinedTextField(
                        value = nameText,
                        onValueChange = {
                            nameText = it
                            if (it.isNotBlank()) {
                                onIntent(CanvasIntent.UpdateCabinet(selectedCab.copy(nameFa = it.take(100))))
                            }
                        },
                        label = { Text("نام یونیت", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { txt ->
                                widthText = txt
                                parseCentimeters(txt, minCm = 15f, maxCm = 600f)?.let { w ->
                                    onIntent(CanvasIntent.UpdateCabinet(selectedCab.copy(width = w)))
                                }
                            },
                            label = { Text("عرض (سم)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = depthText,
                            onValueChange = { txt ->
                                depthText = txt
                                parseCentimeters(txt, minCm = 10f, maxCm = 400f)?.let { d ->
                                    onIntent(CanvasIntent.UpdateCabinet(selectedCab.copy(depth = d)))
                                }
                            },
                            label = { Text("عمق (سم)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { txt ->
                                heightText = txt
                                parseCentimeters(txt, minCm = 15f, maxCm = 350f)?.let { h ->
                                    onIntent(CanvasIntent.UpdateCabinet(selectedCab.copy(height = h)))
                                }
                            },
                            label = { Text("ارتفاع (سم)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = elevText,
                            onValueChange = { txt ->
                                elevText = txt
                                parseCentimeters(txt, minCm = 0f, maxCm = 280f)?.let { el ->
                                    onIntent(CanvasIntent.UpdateCabinet(selectedCab.copy(elevationCm = el)))
                                }
                            },
                            label = { Text("نصب از کف (سم)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Text("عرض‌های استاندارد کارگاهی (سم):", fontSize = 11.sp, color = CadPalette.TextMuted)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (presetW in WORKSHOP_WIDTH_PRESETS_CM) {
                            val active = selectedCab.width.roundToInt() == presetW
                            Surface(
                                onClick = {
                                    onIntent(CanvasIntent.UpdateCabinet(selectedCab.copy(width = presetW.toFloat())))
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) CadPalette.AccentOak else CadPalette.BgCanvas,
                                border = BorderStroke(1.dp, CadPalette.BorderHairline),
                            ) {
                                Text(
                                    text = presetW.fa(),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) CadPalette.BgCanvas else CadPalette.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { onIntent(CanvasIntent.RotateSelected90) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CadPalette.AccentOak, contentColor = CadPalette.BgCanvas),
                    ) {
                        Icon(CadIcons.Rotate90, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("چرخش ۹۰ درجه در پلان (${formatAngle(selectedCab.rotation.toFloat())})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Group Move, Alignment, Duplicate & Delete Controls
        if (editor.totalSelectedCount > 0) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CadPalette.BgElevated,
                border = BorderStroke(1.dp, CadPalette.BorderHairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("عملیات و حرکت دقیق", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        FilterChip(
                            selected = fineNudge,
                            onClick = { fineNudge = !fineNudge },
                            label = { Text(if (fineNudge) "گام ۱ سم" else "گام ۱۰ سم", fontSize = 10.5.sp) },
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { onIntent(CanvasIntent.NudgeSelected(nudgeStep, 0f)) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                            Text("راست +", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = { onIntent(CanvasIntent.NudgeSelected(-nudgeStep, 0f)) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                            Text("چپ −", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = { onIntent(CanvasIntent.NudgeSelected(0f, -nudgeStep)) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                            Text("بالا −", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = { onIntent(CanvasIntent.NudgeSelected(0f, nudgeStep)) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                            Text("پایین +", fontSize = 11.sp)
                        }
                    }

                    if (editor.selectedCabinetIds.size >= 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onIntent(CanvasIntent.AlignSelected(horizontal = true)) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("هم‌تراز افقی", fontSize = 11.5.sp, color = CadPalette.AccentCyan)
                            }
                            OutlinedButton(
                                onClick = { onIntent(CanvasIntent.AlignSelected(horizontal = false)) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("هم‌تراز عمودی", fontSize = 11.5.sp, color = CadPalette.AccentCyan)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = { onIntent(CanvasIntent.DuplicateSelected) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(CadIcons.Duplicate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("تکثیر", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { onIntent(CanvasIntent.DeleteSelected) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CadPalette.AccentRed.copy(alpha = 0.2f),
                                contentColor = CadPalette.AccentRed,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(CadIcons.Trash, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("حذف", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarpentryBomTab(
    editor: CadEditorState,
    onIntent: (CanvasIntent) -> Unit,
) {
    val bom = remember(editor.document.cabinets) {
        BomCalculator.calculate(editor.document.cabinets)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CadPalette.BgElevated,
                border = BorderStroke(1.dp, CadPalette.BorderHairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "خلاصه متره طولی کابینت‌ها (استاندارد اتحادیه)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CadPalette.AccentOak,
                    )
                    BomMetricRow("کابینت زمینی و زیرسینک:", formatMeters(bom.baseRunningCm), CadPalette.AccentOak)
                    BomMetricRow("کابینت دیواری (هوایی):", formatMeters(bom.wallRunningCm), CadPalette.AccentCyan)
                    BomMetricRow("کمد ایستاده و قدی:", formatMeters(bom.tallRunningCm), CadPalette.AccentPurple)
                    if (bom.islandRunningCm > 0f) {
                        BomMetricRow("جزیره و پیشخوان:", formatMeters(bom.islandRunningCm), CadPalette.AccentEmerald)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("لیست یونیت‌های موجود در پلان", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${bom.totalUnits.fa()} یونیت", fontSize = 11.5.sp, color = CadPalette.TextMuted)
            }
        }

        itemsIndexed(bom.items, key = { _, c -> c.id }) { index, cab ->
            Surface(
                onClick = { onIntent(CanvasIntent.SelectSingleCabinet(cab.id)) },
                shape = RoundedCornerShape(10.dp),
                color = CadPalette.BgElevated,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (cab.id in editor.selectedCabinetIds) CadPalette.AccentCyan else CadPalette.BorderHairline,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(CadPalette.BgCanvas),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text((index + 1).fa(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CadPalette.AccentOak)
                        }
                        Column {
                            Text(cab.nameFa, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "کد: ${cab.codeFa} | ارتفاع: ${formatCm(cab.height)}",
                                fontSize = 10.5.sp,
                                color = CadPalette.TextMuted,
                            )
                        }
                    }

                    Text(
                        "${formatCm(cab.width, false)}×${formatCm(cab.depth, false)} سم",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = CadPalette.AccentAmber,
                    )
                }
            }
        }
    }
}

@Composable
private fun BomMetricRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CadPalette.BgCanvas)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.5.sp, color = CadPalette.TextSecondary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
