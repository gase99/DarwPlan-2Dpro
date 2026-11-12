package ir.cabinetplan.fa.ui.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.cabinetplan.fa.R
import ir.cabinetplan.fa.domain.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectDialogs(state: CanvasState, send: (CanvasIntent) -> Unit) {
    if (state.sizeDialog) {
        val lines = state.editor.document.lines.filter { it.id in state.editor.selected }
        val single = lines.singleOrNull()?.takeIf { it.group == null }
        val bounds = PlanDocument(lines).bounds
        var first by remember { mutableStateOf((single?.length ?: bounds?.width ?: 0f).millimeters()) }
        var second by remember { mutableStateOf((bounds?.height ?: 0f).millimeters()) }
        val a = parseMillimeters(first)
        val b = if (single == null) parseMillimeters(second) else null
        AlertDialog(onDismissRequest = { send(CanvasIntent.DismissSize) }, title = { Text(stringResource(R.string.edit_size)) },
            text = { Column {
                OutlinedTextField(first, { first = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(stringResource(if (single != null) R.string.line_length else R.string.selection_width)) })
                if (single == null) OutlinedTextField(second, { second = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = { Text(stringResource(R.string.selection_depth)) })
                Text(stringResource(R.string.size_hint), style = MaterialTheme.typography.labelSmall)
            } },
            confirmButton = { TextButton(onClick = { if (a != null) send(CanvasIntent.Resize(a, b)) },
                enabled = a != null && (single != null || (b != null && (bounds?.width ?: 0f) > 0 && (bounds?.height ?: 0f) > 0))) {
                Text(stringResource(R.string.apply_size))
            } }, dismissButton = { TextButton(onClick = { send(CanvasIntent.DismissSize) }) { Text(stringResource(R.string.cancel)) } })
    }
    if (state.saveDialog) {
        var name by remember { mutableStateOf(state.projectName) }
        var copy by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { send(CanvasIntent.DismissSave) }, title = { Text(stringResource(R.string.save_project)) },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { if (it.length <= 100) name = it },
                        singleLine = true, label = { Text(stringResource(R.string.project_name)) })
                    if (state.projectId != null) {
                        Row {
                            Checkbox(checked = copy, onCheckedChange = { copy = it })
                            Text(stringResource(R.string.save_copy), Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { send(CanvasIntent.Save(name, copy)) }, enabled = name.isNotBlank() && !state.busy) {
                Text(stringResource(R.string.save))
            } }, dismissButton = { TextButton(onClick = { send(CanvasIntent.DismissSave) }) { Text(stringResource(R.string.cancel)) } })
    }
    if (state.browser) AlertDialog(onDismissRequest = { send(CanvasIntent.DismissBrowser) },
        title = { Text(stringResource(R.string.projects)) }, text = {
            if (state.projects.isEmpty()) Text(stringResource(R.string.no_projects)) else LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(state.projects, key = { it.id }) { project ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        TextButton(onClick = { send(CanvasIntent.Load(project.id)) }) { Text(project.name) }
                        Row {
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.forLanguageTag("fa"))
                                .format(Date(project.updatedAt)), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { send(CanvasIntent.RequestDeleteProject(project.id)) }) { Text(stringResource(R.string.delete)) }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { send(CanvasIntent.DismissBrowser) }) { Text(stringResource(R.string.close)) } })
    if (state.pendingDiscard != null) AlertDialog(onDismissRequest = { send(CanvasIntent.CancelDiscard) },
        title = { Text(stringResource(R.string.unsaved_title)) }, text = { Text(stringResource(R.string.unsaved_body)) },
        confirmButton = { TextButton(onClick = { send(CanvasIntent.ConfirmDiscard) }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { send(CanvasIntent.CancelDiscard) }) { Text(stringResource(R.string.cancel)) } })
    if (state.pendingDelete != null) AlertDialog(onDismissRequest = { send(CanvasIntent.CancelDeleteProject) },
        title = { Text(stringResource(R.string.delete_project)) }, text = { Text(stringResource(R.string.delete_body)) },
        confirmButton = { TextButton(onClick = { send(CanvasIntent.ConfirmDeleteProject) }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { send(CanvasIntent.CancelDeleteProject) }) { Text(stringResource(R.string.cancel)) } })
}
