package ir.cabinetplan.fa.ui.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.cabinetplan.fa.R
import ir.cabinetplan.fa.domain.*
import ir.cabinetplan.fa.ui.icons.CadIcons
import ir.cabinetplan.fa.ui.theme.CadPalette

@Composable
fun ProjectsManagerContent(
    state: CanvasUiState,
    onIntent: (CanvasIntent) -> Unit,
    onClose: () -> Unit,
) {
    var nameInput by remember(state.projectId, state.projectNameFa) { mutableStateOf(state.projectNameFa) }
    var clientInput by remember(state.projectId, state.editor.document.clientNameFa) {
        mutableStateOf(state.editor.document.clientNameFa)
    }
    var notesInput by remember(state.projectId, state.editor.document.notesFa) {
        mutableStateOf(state.editor.document.notesFa)
    }

    var newBlankName by remember { mutableStateOf("") }
    var newBlankClient by remember { mutableStateOf("") }
    var withStarterWalls by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("مدیریت و ذخیره پروژه‌ها", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }

        // 1. Active Project Save Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CadPalette.BgElevated,
            border = BorderStroke(1.dp, CadPalette.AccentOak.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("پروژه فعال در حال ویرایش", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CadPalette.AccentOak)

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        onIntent(CanvasIntent.UpdateProjectMeta(it, clientInput, notesInput))
                    },
                    label = { Text("نام طرح", fontSize = 11.5.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = clientInput,
                    onValueChange = {
                        clientInput = it
                        onIntent(CanvasIntent.UpdateProjectMeta(nameInput, it, notesInput))
                    },
                    label = { Text("نام کارفرما / واحد", fontSize = 11.5.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = {
                        notesInput = it
                        onIntent(CanvasIntent.UpdateProjectMeta(nameInput, clientInput, it))
                    },
                    label = { Text("یادداشت اجرایی (جنس ورق، یراق، صفحه...)", fontSize = 11.5.sp) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onIntent(CanvasIntent.SaveProject(asCopy = false, customName = nameInput)) },
                        enabled = !state.busy,
                        colors = ButtonDefaults.buttonColors(containerColor = CadPalette.AccentEmerald, contentColor = CadPalette.BgCanvas),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.ExtraBold)
                    }
                    if (state.projectId != null) {
                        OutlinedButton(
                            onClick = { onIntent(CanvasIntent.SaveProject(asCopy = true, customName = "$nameInput - کپی")) },
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.save_copy), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 2. Room Saved Projects on Device
        Text(
            "پروژه‌های ذخیره‌شده روی گوشی (${state.savedProjects.size.fa()})",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        if (state.savedProjects.isEmpty()) {
            Text("هنوز پروژه‌ای در حافظه گوشی ذخیره نشده است.", fontSize = 12.sp, color = CadPalette.TextMuted)
        } else {
            for (proj in state.savedProjects) {
                val isCurrent = proj.id == state.projectId
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CadPalette.BgElevated,
                    border = BorderStroke(1.dp, if (isCurrent) CadPalette.AccentCyan else CadPalette.BorderHairline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(proj.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatPersianDate(proj.updatedAt), fontSize = 11.sp, color = CadPalette.TextMuted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = { onIntent(CanvasIntent.RequestLoadSaved(proj.id)) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text("بارگذاری", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { onIntent(CanvasIntent.RequestDeleteSaved(proj.id)) }) {
                                Icon(CadIcons.Trash, contentDescription = "حذف ${proj.name}", tint = CadPalette.AccentRed)
                            }
                        }
                    }
                }
            }
        }

        // 3. Built-in Preset Kitchens
        Text("پلان‌های نمونه آماده", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        for (preset in PRESET_PROJECTS) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CadPalette.BgElevated,
                border = BorderStroke(1.dp, CadPalette.BorderHairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.nameFa, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        Text(
                            "${preset.document.lines.size.fa()} دیوار • ${preset.document.cabinets.size.fa()} یونیت",
                            fontSize = 11.sp,
                            color = CadPalette.AccentOak,
                        )
                    }
                    OutlinedButton(onClick = { onIntent(CanvasIntent.RequestLoadPreset(preset)) }) {
                        Text("باز کردن", fontSize = 11.5.sp)
                    }
                }
            }
        }

        // 4. Create Blank Project
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CadPalette.BgElevated,
            border = BorderStroke(1.dp, CadPalette.BorderHairline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ساخت بوم خام جدید", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = newBlankName,
                    onValueChange = { newBlankName = it },
                    placeholder = { Text("عنوان پروژه جدید (مثلاً آشپزخانه نیاوران)", fontSize = 11.5.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newBlankClient,
                    onValueChange = { newBlankClient = it },
                    placeholder = { Text("نام کارفرما (اختیاری)", fontSize = 11.5.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = withStarterWalls, onCheckedChange = { withStarterWalls = it })
                    Text("همراه با دو دیوار اولیه عمود بر هم", fontSize = 12.sp, color = CadPalette.TextSecondary)
                }
                Button(
                    onClick = {
                        onIntent(
                            CanvasIntent.RequestNewBlank(
                                nameFa = newBlankName,
                                clientNameFa = newBlankClient,
                                withStarterWalls = withStarterWalls,
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CadPalette.AccentOak, contentColor = CadPalette.BgCanvas),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.new_project), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun ExportSheetContent(
    state: CanvasUiState,
    onExportPng: () -> Unit,
    onExportPdf: () -> Unit,
    onClose: () -> Unit,
) {
    val doc = state.editor.document
    val bom = remember(doc.cabinets) { BomCalculator.calculate(doc.cabinets) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("خروجی نقشه اجرایی و برگه فنی", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }

        if (!doc.hasGeometry) {
            Text("پلان فعلی خالی است؛ ابتدا حداقل یک دیوار یا کابینت رسم کنید.", fontSize = 12.5.sp, color = CadPalette.AccentAmber)
        }

        Button(
            onClick = onExportPng,
            enabled = doc.hasGeometry && !state.busy,
            colors = ButtonDefaults.buttonColors(containerColor = CadPalette.AccentOak, contentColor = CadPalette.BgCanvas),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Icon(CadIcons.Export, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.export_png), fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
        }

        Button(
            onClick = onExportPdf,
            enabled = doc.hasGeometry && !state.busy,
            colors = ButtonDefaults.buttonColors(containerColor = CadPalette.AccentCyan, contentColor = CadPalette.BgCanvas),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Icon(CadIcons.Export, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.export_pdf), fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
        }

        // Technical Summary Preview Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CadPalette.BgElevated,
            border = BorderStroke(1.dp, CadPalette.BorderHairline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("پیش‌نمایش شناسنامه فنی پروژه: ${state.projectNameFa}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "کارفرما: ${doc.clientNameFa.ifBlank { "کارفرمای محترم" }}",
                    fontSize = 11.5.sp,
                    color = CadPalette.TextMuted,
                )
                HorizontalDivider(color = CadPalette.BorderHairline, modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "زمینی: ${formatMeters(bom.baseRunningCm)} • دیواری: ${formatMeters(bom.wallRunningCm)} • ایستاده: ${formatMeters(bom.tallRunningCm)}",
                    fontSize = 11.5.sp,
                    color = CadPalette.AccentOak,
                    fontWeight = FontWeight.Bold,
                )
                for ((idx, cab) in doc.cabinets.take(10).withIndex()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CadPalette.BgCanvas)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${(idx + 1).fa()}. ${cab.nameFa}", fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(
                            "${cab.codeFa} | ${formatCm(cab.width, false)}×${formatCm(cab.depth, false)}×${formatCm(cab.height, false)}",
                            fontSize = 11.sp,
                            color = CadPalette.AccentCyan,
                        )
                    }
                }
            }
        }
    }
}
