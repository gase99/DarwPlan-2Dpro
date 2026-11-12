package ir.cabinetplan.fa.ui.canvas

import android.graphics.Paint
import android.graphics.RectF
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import ir.cabinetplan.fa.R
import ir.cabinetplan.fa.domain.*
import ir.cabinetplan.fa.ui.theme.CadPalette
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CabinetBlueprintCanvas(
    editor: CadEditorState,
    onIntent: (CanvasIntent) -> Unit,
    onCanvasSize: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentEditor by rememberUpdatedState(editor)
    val currentSend by rememberUpdatedState(onIntent)
    val density = LocalDensity.current.density
    val canvasDesc = stringResource(R.string.canvas_description)
    val zoomPercent = (editor.viewport.zoom * 100f).roundToInt().fa()
    val zoomStateDesc = stringResource(R.string.canvas_state, zoomPercent)

    val titlePaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
    }
    val subPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .background(CadPalette.BgCanvas)
            .onSizeChanged(onCanvasSize)
            .semantics {
                contentDescription = canvasDesc
                stateDescription = zoomStateDesc
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var transformed = false
                    var finished = false
                    var moved = false

                    fun toWorld(offset: Offset): Point = currentEditor.viewport.screenToWorld(
                        Point(offset.x, offset.y),
                        Point(size.width / 2f, size.height / 2f),
                    )

                    val pxPerCm = currentEditor.viewport.pixelsPerCm
                    val snapRadiusCm = (18f * density) / pxPerCm
                    val handleRadiusCm = (24f * density) / pxPerCm

                    val isPanMode = currentEditor.activeTool == ToolMode.PAN
                    if (!isPanMode) {
                        currentSend(CanvasIntent.BeginGesture(toWorld(down.position), snapRadiusCm, handleRadiusCm))
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }

                            if (pressed >= 2) {
                                if (!transformed) {
                                    currentSend(CanvasIntent.CancelGesture)
                                    transformed = true
                                }
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                currentSend(
                                    CanvasIntent.TransformViewport(
                                        centroidFromCenter = Point(centroid.x - size.width / 2f, centroid.y - size.height / 2f),
                                        pan = Point(pan.x, pan.y),
                                        zoom = zoom,
                                    )
                                )
                            } else if (!transformed && (currentEditor.activeTool == ToolMode.PAN ||
                                    (currentEditor.viewMode == ViewMode.ELEVATION_FRONT && currentEditor.gesture == null))
                            ) {
                                val pan = event.calculatePan()
                                if (pan.x != 0f || pan.y != 0f) {
                                    currentSend(CanvasIntent.TransformViewport(Point(), Point(pan.x, pan.y), 1f))
                                }
                            } else if (!transformed) {
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change != null) {
                                    moved = moved || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                    if (moved) {
                                        val moveSnapCm = (18f * density) / currentEditor.viewport.pixelsPerCm
                                        currentSend(CanvasIntent.DragGesture(toWorld(change.position), moveSnapCm))
                                    }
                                }
                            }

                            event.changes.forEach { it.consume() }
                            if (pressed == 0) {
                                if (!transformed && currentEditor.activeTool != ToolMode.PAN) {
                                    currentSend(CanvasIntent.FinishGesture)
                                }
                                finished = true
                                break
                            }
                        }
                    } finally {
                        if (!finished) currentSend(CanvasIntent.CancelGesture)
                    }
                }
            }
    ) {
        if (editor.viewMode == ViewMode.ELEVATION_FRONT) {
            drawElevationFrontView(editor, density, titlePaint, subPaint)
        } else {
            drawBlueprintPlan2D(editor, density, titlePaint, subPaint)
        }
    }
}

private fun DrawScope.drawBlueprintPlan2D(
    editor: CadEditorState,
    density: Float,
    titlePaint: Paint,
    subPaint: Paint,
) {
    val viewport = editor.viewport
    val pxPerCm = viewport.pixelsPerCm
    val center = Point(size.width / 2f, size.height / 2f)

    fun screen(world: Point): Offset {
        val s = viewport.worldToScreen(world, center)
        return Offset(s.x, s.y)
    }

    val topLeftWorld = viewport.screenToWorld(Point(0f, 0f), center)
    val bottomRightWorld = viewport.screenToWorld(Point(size.width, size.height), center)

    // 1. Dual-Scale Architectural Blueprint Grid (10cm minor, 100cm major)
    val minorStepCm = Viewport.MINOR_GRID_CM.toFloat()
    if (minorStepCm * pxPerCm >= 10f * density) {
        val xStart = floor(topLeftWorld.x / minorStepCm).toInt()
        val xEnd = ceil(bottomRightWorld.x / minorStepCm).toInt()
        for (i in xStart..xEnd) {
            val sx = screen(Point(i * minorStepCm, 0f)).x
            drawLine(CadPalette.GridMinor, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1f)
        }
        val yStart = floor(topLeftWorld.y / minorStepCm).toInt()
        val yEnd = ceil(bottomRightWorld.y / minorStepCm).toInt()
        for (i in yStart..yEnd) {
            val sy = screen(Point(0f, i * minorStepCm)).y
            drawLine(CadPalette.GridMinor, Offset(0f, sy), Offset(size.width, sy), strokeWidth = 1f)
        }
    }

    val majorStepCm = Viewport.MAJOR_GRID_CM.toFloat()
    val mxStart = floor(topLeftWorld.x / majorStepCm).toInt()
    val mxEnd = ceil(bottomRightWorld.x / majorStepCm).toInt()
    for (i in mxStart..mxEnd) {
        val sx = screen(Point(i * majorStepCm, 0f)).x
        drawLine(CadPalette.GridMajor, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1.4f * density * 0.6f)
    }
    val myStart = floor(topLeftWorld.y / majorStepCm).toInt()
    val myEnd = ceil(bottomRightWorld.y / majorStepCm).toInt()
    for (i in myStart..myEnd) {
        val sy = screen(Point(0f, i * majorStepCm)).y
        drawLine(CadPalette.GridMajor, Offset(0f, sy), Offset(size.width, sy), strokeWidth = 1.4f * density * 0.6f)
    }

    // Origin Crosshair (0,0)
    val origin = screen(Point(0f, 0f))
    drawLine(Color(0xFF334155), Offset(origin.x - 24f * density, origin.y), Offset(origin.x + 32f * density, origin.y), 1.5f * density)
    drawLine(Color(0xFF334155), Offset(origin.x, origin.y - 24f * density), Offset(origin.x, origin.y + 32f * density), 1.5f * density)
    drawCircle(CadPalette.AccentOak, radius = 3.5f * density, center = origin)

    val dashWallCab = PathEffect.dashPathEffect(floatArrayOf(7f * density, 4f * density))
    val dashSelection = PathEffect.dashPathEffect(floatArrayOf(5f * density, 3f * density))

    val doc = editor.visibleDocument

    // 2. Cabinets & Architectural Symbols
    for (cab in editor.visibleCabinetsForPlan(doc)) {
        val isSelected = cab.id in editor.selectedCabinetIds
        val tl = screen(Point(cab.x, cab.y))
        val cabSize = Size(cab.width * pxPerCm, cab.depth * pxPerCm)

        if (isSelected) {
            val pad = 3f * density
            drawRoundRect(
                color = CadPalette.AccentCyan,
                topLeft = Offset(tl.x - pad, tl.y - pad),
                size = Size(cabSize.width + pad * 2f, cabSize.height + pad * 2f),
                cornerRadius = CornerRadius(3f * density, 3f * density),
                style = Stroke(width = 2f * density, pathEffect = dashSelection),
            )
        }

        drawRoundRect(
            color = Color(cab.fillArgb),
            topLeft = tl,
            size = cabSize,
            cornerRadius = CornerRadius(2f * density, 2f * density),
        )
        drawRoundRect(
            color = if (isSelected) CadPalette.AccentCyan else Color(cab.strokeArgb),
            topLeft = tl,
            size = cabSize,
            cornerRadius = CornerRadius(2f * density, 2f * density),
            style = Stroke(
                width = (if (isSelected) 2.4f else 1.7f) * density,
                pathEffect = if (cab.category == CabinetCategory.WALL) dashWallCab else null,
            ),
        )

        drawCabinetSymbol2D(cab, tl, cabSize, pxPerCm, density)

        // Persian label & dimensions inside unit
        if (cabSize.width >= 38f * density && cabSize.height >= 24f * density) {
            val cx = tl.x + cabSize.width / 2f
            val cy = tl.y + cabSize.height / 2f
            titlePaint.color = android.graphics.Color.rgb(248, 250, 252)
            titlePaint.textSize = min(12f * density, max(8.5f * density, cabSize.width * 0.14f))
            drawContext.canvas.nativeCanvas.drawText(
                cab.nameFa,
                cx,
                cy - (if (editor.showDimensions) 3f * density else -4f * density),
                titlePaint,
            )

            if (editor.showDimensions) {
                subPaint.color = android.graphics.Color.rgb(251, 191, 36)
                subPaint.textSize = min(10.5f * density, max(8f * density, cabSize.width * 0.12f))
                drawContext.canvas.nativeCanvas.drawText(
                    "${formatCm(cab.width, false)}×${formatCm(cab.depth, false)} سم",
                    cx,
                    cy + 11f * density,
                    subPaint,
                )
            }
        }

        // Bottom-right interactive resize handle
        if (isSelected) {
            val hs = 11f * density
            val hOffset = Offset(tl.x + cabSize.width - hs / 2f, tl.y + cabSize.height - hs / 2f)
            drawRoundRect(
                color = CadPalette.AccentOak,
                topLeft = hOffset,
                size = Size(hs, hs),
                cornerRadius = CornerRadius(2.5f * density, 2.5f * density),
            )
            drawRoundRect(
                color = CadPalette.BgCanvas,
                topLeft = hOffset,
                size = Size(hs, hs),
                cornerRadius = CornerRadius(2.5f * density, 2.5f * density),
                style = Stroke(width = 1.8f * density),
            )
        }
    }

    // 3. Structural Walls & Dimension Callout Badges
    for (line in doc.lines) {
        val isSelected = line.id in editor.selectedLineIds
        val p1 = screen(line.start)
        val p2 = screen(line.end)
        val bodyStroke = max(line.thickness * pxPerCm, 5.5f * density)

        drawLine(
            color = if (isSelected) CadPalette.AccentCyan else Color(0xFFCBD5E1),
            start = p1,
            end = p2,
            strokeWidth = bodyStroke,
            cap = StrokeCap.Square,
        )
        drawLine(
            color = if (isSelected) Color(0xFF0F172A) else Color(0xFF475569),
            start = p1,
            end = p2,
            strokeWidth = 1.6f * density,
        )

        if (editor.showDimensions && (p2 - p1).getDistance() >= 48f * density) {
            val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f - 16f * density)
            drawBadge(
                center = mid,
                text = formatCm(line.lengthCm),
                bgColor = Color(0xFF1E293B),
                borderColor = CadPalette.AccentCyan,
                textArgb = android.graphics.Color.rgb(56, 189, 248),
                density = density,
                titlePaint = titlePaint,
            )
        }

        if (isSelected) {
            for (ep in listOf(p1, p2)) {
                drawCircle(CadPalette.AccentCyan, radius = 8.5f * density, center = ep)
                drawCircle(CadPalette.BgCanvas, radius = 3.5f * density, center = ep)
            }
        }
    }

    // 4. Live Laser Measure Overlay
    (editor.gesture as? CadGesture.Measure)?.let { g ->
        val a = screen(g.start)
        val b = screen(g.current)
        drawLine(
            color = CadPalette.AccentEmerald,
            start = a,
            end = b,
            strokeWidth = 2.2f * density,
            pathEffect = dashSelection,
        )
        drawCircle(CadPalette.AccentEmerald, radius = 5f * density, center = a)
        drawCircle(CadPalette.AccentEmerald, radius = 5f * density, center = b)
        val len = ((distance(g.start, g.current) * 10f).roundToInt()) / 10f
        drawBadge(
            center = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f - 22f * density),
            text = "متر لیزری: ${formatCm(len)}",
            bgColor = Color(0xFF064E3B),
            borderColor = CadPalette.AccentEmerald,
            textArgb = android.graphics.Color.rgb(167, 243, 208),
            density = density,
            titlePaint = titlePaint,
            minWidthDp = 112f,
        )
    }

    // 5. Ghost Placement Preview
    (editor.gesture as? CadGesture.PlaceCabinet)?.let { g ->
        val item = editor.pendingCatalogItem
        val tl = screen(g.point - Point(item.defaultWidth / 2f, item.defaultDepth / 2f))
        val sz = Size(item.defaultWidth * pxPerCm, item.defaultDepth * pxPerCm)
        drawRect(Color(item.fillArgb), topLeft = tl, size = sz)
        drawRect(CadPalette.AccentOak, topLeft = tl, size = sz, style = Stroke(2f * density, pathEffect = dashSelection))
    }

    // 6. Marquee Multi-Select Box
    (editor.gesture as? CadGesture.Marquee)?.let { g ->
        val a = screen(g.start)
        val b = screen(g.current)
        val left = min(a.x, b.x)
        val top = min(a.y, b.y)
        val w = max(a.x, b.x) - left
        val h = max(a.y, b.y) - top
        if (w > 4f || h > 4f) {
            drawRect(Color(0x2038BDF8), Offset(left, top), Size(w, h))
            drawRect(CadPalette.AccentCyan, Offset(left, top), Size(w, h), style = Stroke(1.5f * density, pathEffect = dashSelection))
        }
    }

    // 7. Dynamic Orthogonal Alignment Guides
    for (guide in editor.activeGuides) {
        if (guide.vertical) {
            val gx = screen(Point(guide.position, 0f)).x
            val gy1 = screen(Point(guide.position, guide.from - 35f)).y
            val gy2 = screen(Point(guide.position, guide.to + 35f)).y
            drawLine(CadPalette.AccentCyan, Offset(gx, gy1), Offset(gx, gy2), 1.6f * density, pathEffect = dashSelection)
        } else {
            val gy = screen(Point(0f, guide.position)).y
            val gx1 = screen(Point(guide.from - 35f, guide.position)).x
            val gx2 = screen(Point(guide.to + 35f, guide.position)).x
            drawLine(CadPalette.AccentCyan, Offset(gx1, gy), Offset(gx2, gy), 1.6f * density, pathEffect = dashSelection)
        }
    }

    // 8. Magnetic Snap Ring & Persian Snap Indicator Pill
    editor.activeSnap?.let { snap ->
        val sp = screen(snap.point)
        drawCircle(Color(0x3310B981), radius = 14f * density, center = sp)
        drawCircle(CadPalette.AccentEmerald, radius = 14f * density, center = sp, style = Stroke(2f * density))
        drawCircle(CadPalette.AccentEmerald, radius = 4f * density, center = sp)
        drawBadge(
            center = Offset(sp.x, sp.y - 26f * density),
            text = snap.labelFa,
            bgColor = Color(0xFF064E3B),
            borderColor = CadPalette.AccentEmerald,
            textArgb = android.graphics.Color.rgb(167, 243, 208),
            density = density,
            titlePaint = titlePaint,
            minWidthDp = 96f,
        )
    }
}

private fun DrawScope.drawCabinetSymbol2D(
    cab: CabinetObject,
    tl: Offset,
    sz: Size,
    pxPerCm: Float,
    density: Float,
) {
    when (cab.symbolType) {
        CabinetSymbolType.SINK_DOUBLE -> {
            val pad = 7f * pxPerCm
            val b1TopLeft = Offset(tl.x + pad, tl.y + pad)
            val b1Size = Size(sz.width * 0.35f, (sz.height - pad * 2f).coerceAtLeast(4f))
            val b2TopLeft = Offset(tl.x + sz.width * 0.48f, tl.y + pad)
            val b2Size = Size(sz.width * 0.31f, (sz.height - pad * 2f).coerceAtLeast(4f))
            for ((btl, bsz) in listOf(b1TopLeft to b1Size, b2TopLeft to b2Size)) {
                drawRoundRect(Color(0x8C0F172A), btl, bsz, CornerRadius(4f * density, 4f * density))
                drawRoundRect(CadPalette.AccentCyan, btl, bsz, CornerRadius(4f * density, 4f * density), Stroke(1.3f * density))
                drawCircle(CadPalette.AccentCyan, 2.5f * density, Offset(btl.x + bsz.width / 2f, btl.y + bsz.height / 2f))
            }
            for (frac in listOf(0.25f, 0.5f, 0.75f)) {
                val ry = tl.y + sz.height * frac
                drawLine(CadPalette.AccentCyan, Offset(tl.x + sz.width * 0.84f, ry), Offset(tl.x + sz.width * 0.94f, ry), 1.2f * density)
            }
        }
        CabinetSymbolType.GAS_HOB -> {
            val pad = 6f * pxPerCm
            val plateSize = Size((sz.width - pad * 2f).coerceAtLeast(4f), (sz.height - pad * 2f).coerceAtLeast(4f))
            drawRoundRect(Color(0x990F172A), Offset(tl.x + pad, tl.y + pad), plateSize, CornerRadius(3.5f * density, 3.5f * density))
            val r = min(sz.width, sz.height) * 0.10f
            val hobColor = Color(0xFFF87171)
            for ((fx, fy) in listOf(0.25f to 0.30f, 0.25f to 0.70f, 0.75f to 0.30f, 0.75f to 0.70f)) {
                drawCircle(hobColor, r, Offset(tl.x + sz.width * fx, tl.y + sz.height * fy), style = Stroke(1.3f * density))
            }
            val centerPt = Offset(tl.x + sz.width / 2f, tl.y + sz.height / 2f)
            drawCircle(hobColor, r * 1.45f, centerPt, style = Stroke(1.4f * density))
            drawCircle(hobColor, r * 0.6f, centerPt, style = Stroke(1.2f * density))
        }
        CabinetSymbolType.DOOR_SWING -> {
            val swing = sz.width * 0.85f
            val arcPath = Path().apply {
                moveTo(tl.x, tl.y + sz.height)
                lineTo(tl.x, tl.y + sz.height + swing)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        tl.x - swing,
                        tl.y + sz.height - swing,
                        tl.x + swing,
                        tl.y + sz.height + swing,
                    ),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false,
                )
            }
            drawPath(
                path = arcPath,
                color = CadPalette.AccentAmber,
                style = Stroke(1.4f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * density, 4f * density))),
            )
        }
        CabinetSymbolType.WINDOW_DOUBLE -> {
            drawLine(Color(0xFF7DD3FC), Offset(tl.x, tl.y + sz.height * 0.35f), Offset(tl.x + sz.width, tl.y + sz.height * 0.35f), 1.2f * density)
            drawLine(Color(0xFF7DD3FC), Offset(tl.x, tl.y + sz.height * 0.65f), Offset(tl.x + sz.width, tl.y + sz.height * 0.65f), 1.2f * density)
        }
        else -> {
            if (cab.category == CabinetCategory.BASE || cab.category == CabinetCategory.TALL || cab.category == CabinetCategory.ISLAND) {
                val inset = 3f * density
                drawLine(
                    color = Color(cab.strokeArgb),
                    start = Offset(tl.x + inset, tl.y + sz.height - inset),
                    end = Offset(tl.x + sz.width - inset, tl.y + sz.height - inset),
                    strokeWidth = 2.2f * density,
                )
            }
        }
    }
}

private fun DrawScope.drawElevationFrontView(
    editor: CadEditorState,
    density: Float,
    titlePaint: Paint,
    subPaint: Paint,
) {
    val viewport = editor.viewport
    val pxPerCm = viewport.pixelsPerCm
    val center = Point(size.width / 2f, size.height / 2f)
    fun screen(world: Point): Offset = viewport.worldToScreen(world, center).let { Offset(it.x, it.y) }

    val topLeftWorld = viewport.screenToWorld(Point(0f, 0f), center)
    val bottomRightWorld = viewport.screenToWorld(Point(size.width, size.height), center)
    val step = 10f
    if (step * pxPerCm >= 10f * density) {
        for (i in floor(topLeftWorld.x / step).toInt()..ceil(bottomRightWorld.x / step).toInt()) {
            val sx = screen(Point(i * step, 0f)).x
            drawLine(CadPalette.GridMinor, Offset(sx, 0f), Offset(sx, size.height), 1f)
        }
        for (i in floor(topLeftWorld.y / step).toInt()..ceil(bottomRightWorld.y / step).toInt()) {
            val sy = screen(Point(0f, i * step)).y
            drawLine(CadPalette.GridMinor, Offset(0f, sy), Offset(size.width, sy), 1f)
        }
    }

    val floorY = CadEditorState.ELEVATION_FLOOR_Y
    val ceilingY = floorY - CadEditorState.ELEVATION_CEILING_CM
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f * density, 4f * density))

    // Floor datum line (0.00)
    val fLeft = screen(Point(20f, floorY))
    val fRight = screen(Point(580f, floorY))
    drawLine(CadPalette.AccentOak, fLeft, fRight, 2.8f * density)
    titlePaint.color = android.graphics.Color.rgb(224, 159, 62)
    titlePaint.textSize = 11f * density
    drawContext.canvas.nativeCanvas.drawText("تراز کف تمام‌شده آشپزخانه (۰٫۰۰±)", (fLeft.x + fRight.x) / 2f, fRight.y + 18f * density, titlePaint)

    // Ceiling line (+280 cm)
    val cLeft = screen(Point(20f, ceilingY))
    val cRight = screen(Point(580f, ceilingY))
    drawLine(CadPalette.AccentCyan, cLeft, cRight, 1.5f * density, pathEffect = dash)
    subPaint.color = android.graphics.Color.rgb(56, 189, 248)
    subPaint.textSize = 10.5f * density
    drawContext.canvas.nativeCanvas.drawText("تراز سقف کاذب (${formatCm(280f)})", (cLeft.x + cRight.x) / 2f, cLeft.y - 8f * density, subPaint)

    // Backsplash band (55-60 cm)
    val bsTl = screen(Point(60f, floorY - 145f))
    val bsSize = Size(440f * pxPerCm, 55f * pxPerCm)
    drawRect(Color(0x0D38BDF8), bsTl, bsSize)
    drawRect(CadPalette.BorderHairline, bsTl, bsSize, style = Stroke(1f * density, pathEffect = dash))

    // Render each cabinet in Front Elevation projection
    for (cab in editor.document.cabinets) {
        val isSelected = cab.id in editor.selectedCabinetIds
        val elevTopCm = floorY - cab.elevationCm - cab.height
        val tl = screen(Point(cab.x, elevTopCm))
        val sz = Size(cab.width * pxPerCm, cab.height * pxPerCm)

        drawRect(Color(cab.fillArgb), tl, sz)
        drawRect(
            color = if (isSelected) CadPalette.AccentCyan else Color(cab.strokeArgb),
            topLeft = tl,
            size = sz,
            style = Stroke((if (isSelected) 2.5f else 1.6f) * density),
        )

        // 10cm Plinth for floor-mounted units
        if (cab.elevationCm == 0f && cab.category != CabinetCategory.FIXTURE) {
            val pTl = screen(Point(cab.x, floorY - 10f))
            val pSz = Size(cab.width * pxPerCm, 10f * pxPerCm)
            drawRect(Color(0xB30F141C), pTl, pSz)
            drawRect(Color(cab.strokeArgb), pTl, pSz, style = Stroke(1f * density))
        }

        // 5cm Countertop slab for base/island/sink/hob
        if (cab.category == CabinetCategory.BASE || cab.category == CabinetCategory.ISLAND ||
            cab.symbolType == CabinetSymbolType.SINK_DOUBLE || cab.symbolType == CabinetSymbolType.GAS_HOB
        ) {
            val slabTl = screen(Point(cab.x - 1f, elevTopCm - 5f))
            drawRect(CadPalette.TextPrimary, slabTl, Size((cab.width + 2f) * pxPerCm, 5f * pxPerCm))
        }

        // Drawers or double-door split lines
        if (cab.drawersCount > 0) {
            val usableH = (cab.height - 10f).coerceAtLeast(15f) * pxPerCm
            val dh = usableH / cab.drawersCount
            for (idx in 0 until cab.drawersCount) {
                val dy = tl.y + idx * dh
                if (idx > 0) {
                    drawLine(Color(cab.strokeArgb), Offset(tl.x, dy), Offset(tl.x + sz.width, dy), 1.2f * density)
                }
                val hy = dy + dh / 2f
                val hw = min(14f * density, sz.width * 0.25f)
                drawLine(CadPalette.TextPrimary, Offset(tl.x + sz.width / 2f - hw, hy), Offset(tl.x + sz.width / 2f + hw, hy), 2f * density)
            }
        } else if (cab.doorsCount == 2) {
            val bottomOffset = if (cab.elevationCm == 0f) 10f * pxPerCm else 0f
            drawLine(
                color = Color(cab.strokeArgb),
                start = Offset(tl.x + sz.width / 2f, tl.y),
                end = Offset(tl.x + sz.width / 2f, tl.y + sz.height - bottomOffset),
                strokeWidth = 1.2f * density,
            )
        }

        if (sz.width >= 34f * density && sz.height >= 22f * density) {
            titlePaint.color = android.graphics.Color.rgb(248, 250, 252)
            titlePaint.textSize = min(10.5f * density, max(8f * density, sz.width * 0.13f))
            drawContext.canvas.nativeCanvas.drawText(cab.nameFa, tl.x + sz.width / 2f, tl.y + sz.height / 2f - 3f * density, titlePaint)

            subPaint.color = android.graphics.Color.rgb(251, 191, 36)
            subPaint.textSize = min(9.5f * density, max(7.5f * density, sz.width * 0.11f))
            drawContext.canvas.nativeCanvas.drawText(
                "${formatCm(cab.width, false)}×${formatCm(cab.height, false)} سم",
                tl.x + sz.width / 2f,
                tl.y + sz.height / 2f + 10f * density,
                subPaint,
            )
        }
    }
}

private fun DrawScope.drawBadge(
    center: Offset,
    text: String,
    bgColor: Color,
    borderColor: Color,
    textArgb: Int,
    density: Float,
    titlePaint: Paint,
    minWidthDp: Float = 74f,
) {
    titlePaint.textSize = 11f * density
    val textW = titlePaint.measureText(text)
    val w = max(minWidthDp * density, textW + 18f * density)
    val h = 23f * density
    val tl = Offset(center.x - w / 2f, center.y - h / 2f)
    drawRoundRect(bgColor, tl, Size(w, h), CornerRadius(6f * density, 6f * density))
    drawRoundRect(borderColor, tl, Size(w, h), CornerRadius(6f * density, 6f * density), Stroke(1.3f * density))
    titlePaint.color = textArgb
    val rect = RectF(tl.x, tl.y, tl.x + w, tl.y + h)
    val fm = titlePaint.fontMetrics
    val baseline = rect.centerY() - (fm.top + fm.bottom) / 2f
    drawContext.canvas.nativeCanvas.drawText(text, rect.centerX(), baseline, titlePaint)
}
