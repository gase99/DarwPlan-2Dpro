package ir.cabinetplan.fa.ui.canvas

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.MaterialTheme
import ir.cabinetplan.fa.R
import ir.cabinetplan.fa.domain.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

fun Number.fa(): String = toString().map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")
fun Float.millimeters(): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("fa")).apply {
    maximumFractionDigits = 1
}.format(this)
fun cabinetLabel(kind: CabinetKind): Int = when (kind) {
    CabinetKind.BASE -> R.string.cabinet_base
    CabinetKind.WALL -> R.string.cabinet_wall
    CabinetKind.DRAWER -> R.string.cabinet_drawer
    CabinetKind.DOOR -> R.string.cabinet_door
    CabinetKind.SHELF -> R.string.cabinet_shelf
}

@Composable
fun PlanCanvas(state: EditorState, send: (CanvasIntent) -> Unit, modifier: Modifier = Modifier, onBounds: (Rect) -> Unit) {
    val current by rememberUpdatedState(state)
    val currentSend by rememberUpdatedState(send)
    val colors = MaterialTheme.colorScheme
    val labels = CabinetKind.entries.associateWith { stringResource(cabinetLabel(it)) }
    val unit = stringResource(R.string.unit_short)
    val description = stringResource(R.string.canvas_description)
    val zoomDescription = stringResource(R.string.canvas_state, (state.viewport.zoom * 100).roundToInt().fa())
    val density = LocalDensity.current.density
    val textPaint = remember(density, colors.onSurface) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.onSurface.toArgb(); textSize = 12f * density
            textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }
    Canvas(modifier.background(colors.background).onGloballyPositioned { onBounds(it.boundsInRoot()) }
        .semantics { contentDescription = description; stateDescription = zoomDescription }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                var transformed = false
                var finished = false
                fun world(offset: Offset): Point = current.viewport.screenToWorld(
                    Point(offset.x, offset.y), Point(size.width / 2f, size.height / 2f))
                val tolerance = 18f * density / current.viewport.pixelsPerMillimeter
                currentSend(CanvasIntent.Begin(world(down.position), tolerance))
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            if (!transformed) { currentSend(CanvasIntent.Cancel); transformed = true }
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val pan = event.calculatePan()
                            currentSend(CanvasIntent.Transform(Point(centroid.x - size.width / 2f, centroid.y - size.height / 2f),
                                Point(pan.x, pan.y), event.calculateZoom()))
                        } else if (!transformed && current.tool == Tool.PAN) {
                            val pan = event.calculatePan()
                            currentSend(CanvasIntent.Transform(Point(), Point(pan.x, pan.y), 1f))
                        } else if (!transformed) {
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null) currentSend(CanvasIntent.Drag(world(change.position), tolerance))
                        }
                        event.changes.forEach { it.consume() }
                        if (pressed == 0) {
                            if (!transformed) currentSend(CanvasIntent.Finish)
                            finished = true
                            break
                        }
                    }
                } finally {
                    if (!finished) currentSend(CanvasIntent.Cancel)
                }
            }
        }) {
        val viewport = state.viewport
        val center = Point(size.width / 2f, size.height / 2f)
        fun screen(point: Point): Offset = viewport.worldToScreen(point, center).let { Offset(it.x, it.y) }
        val topLeft = viewport.screenToWorld(Point(), center)
        val bottomRight = viewport.screenToWorld(Point(size.width, size.height), center)
        val step = Viewport.GRID_MILLIMETERS.toFloat()
        for (index in floor(topLeft.x / step).toInt()..ceil(bottomRight.x / step).toInt()) {
            val x = screen(Point(index * step, 0f)).x
            drawLine(colors.outlineVariant, Offset(x, 0f), Offset(x, size.height), density)
        }
        for (index in floor(topLeft.y / step).toInt()..ceil(bottomRight.y / step).toInt()) {
            val y = screen(Point(0f, index * step)).y
            drawLine(colors.outlineVariant, Offset(0f, y), Offset(size.width, y), density)
        }
        val origin = screen(Point())
        drawLine(colors.primary.copy(alpha = .35f), Offset(origin.x, 0f), Offset(origin.x, size.height), density)
        drawLine(colors.primary.copy(alpha = .35f), Offset(0f, origin.y), Offset(size.width, origin.y), density)
        drawCircle(colors.primary, 3f * density, origin)
        state.visibleDocument.lines.forEach { line ->
            val a = screen(line.start); val b = screen(line.end)
            val selected = line.id in state.selected
            drawLine(if (selected) colors.tertiary else colors.primary, a, b, (if (selected) 3f else 2f) * density)
            if (selected && line.group == null) {
                drawCircle(colors.tertiary, 7f * density, a)
                drawCircle(colors.tertiary, 7f * density, b)
            }
            if (state.dimensions && line.group == null && (a - b).getDistance() > 55 * density) {
                drawContext.canvas.nativeCanvas.drawText("${line.length.millimeters()} $unit",
                    (a.x + b.x) / 2, (a.y + b.y) / 2 - 9 * density, textPaint)
            }
        }
        state.visibleDocument.lines.filter { it.group != null }.groupBy { it.group }.values.forEach { lines ->
            val bounds = PlanDocument(lines).bounds!!
            val location = screen(bounds.center)
            if (bounds.width * viewport.pixelsPerMillimeter > 75f * density) {
                drawContext.canvas.nativeCanvas.drawText(labels[lines.first().kind].orEmpty(), location.x, location.y, textPaint)
                if (state.dimensions) drawContext.canvas.nativeCanvas.drawText(
                    "${bounds.width.millimeters()} × ${bounds.height.millimeters()} $unit",
                    location.x, location.y + 18f * density, textPaint)
            }
        }
        val guideColor = colors.tertiary
        val effect = PathEffect.dashPathEffect(floatArrayOf(8f * density, 6f * density))
        state.snap?.guideX?.let {
            val x = screen(Point(it, 0f)).x
            drawLine(guideColor, Offset(x, 0f), Offset(x, size.height), density, pathEffect = effect)
        }
        state.snap?.guideY?.let {
            val y = screen(Point(0f, it)).y
            drawLine(guideColor, Offset(0f, y), Offset(size.width, y), density, pathEffect = effect)
        }
        state.snap?.takeIf { it.vertex }?.let { drawCircle(guideColor, 10f * density, screen(it.point), style = Stroke(2f * density)) }
        (state.gesture as? EditGesture.Marquee)?.let {
            val a = screen(it.anchor); val b = screen(it.point)
            val rect = Rect(a, b).let { r -> Rect(minOf(r.left, r.right), minOf(r.top, r.bottom), maxOf(r.left, r.right), maxOf(r.top, r.bottom)) }
            drawRect(colors.primary.copy(alpha = .1f), rect.topLeft, rect.size)
            drawRect(colors.primary, rect.topLeft, rect.size, style = Stroke(density, pathEffect = effect))
        }
        (state.gesture as? EditGesture.Place)?.let {
            val a = screen(it.point - Point(state.cabinet.width / 2, state.cabinet.depth / 2))
            drawRect(colors.primary.copy(alpha = .2f), a,
                Size(state.cabinet.width * viewport.pixelsPerMillimeter, state.cabinet.depth * viewport.pixelsPerMillimeter))
        }
    }
}
