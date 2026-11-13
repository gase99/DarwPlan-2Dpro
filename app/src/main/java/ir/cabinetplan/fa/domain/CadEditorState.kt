package ir.cabinetplan.fa.domain

import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface CadGesture {
    data class DrawWall(val start: Point, val current: Point) : CadGesture
    data class Measure(val start: Point, val current: Point) : CadGesture
    data class DragEndpoint(
        val lineId: String,
        val isStart: Boolean,
        val anchor: Point,
        val current: Point,
    ) : CadGesture
    data class MoveGroup(val startWorld: Point, val delta: Point = Point()) : CadGesture
    data class ResizeCabinet(val cabinetId: String, val width: Float, val depth: Float) : CadGesture
    data class Marquee(val start: Point, val current: Point) : CadGesture
    data class PlaceCabinet(val point: Point) : CadGesture
}

data class CadEditorState(
    val document: ProjectDocument = ProjectDocument(),
    val activeTool: ToolMode = ToolMode.SELECT,
    val viewMode: ViewMode = ViewMode.PLAN_2D,
    val pendingCatalogItem: CatalogItem = PERSIAN_CABINET_CATALOG.first(),
    val selectedLineIds: Set<String> = emptySet(),
    val selectedCabinetIds: Set<String> = emptySet(),
    val multiSelectMode: Boolean = false,
    val snapEnabled: Boolean = true,
    val angleSnapEnabled: Boolean = true,
    val gridStepCm: Float = 5f,
    val showDimensions: Boolean = true,
    val showWallCabinets: Boolean = true,
    val showBaseCabinets: Boolean = true,
    val viewport: Viewport = Viewport(),
    val gesture: CadGesture? = null,
    val activeSnap: SnapIndicator? = null,
    val activeGuides: List<AlignmentGuide> = emptyList(),
    val past: List<ProjectDocument> = emptyList(),
    val future: List<ProjectDocument> = emptyList(),
) {
    val totalSelectedCount: Int
        get() = selectedLineIds.size + selectedCabinetIds.size

    val selectedSingleLine: WallLine?
        get() = selectedLineIds.singleOrNull()?.let { id -> document.lines.find { it.id == id } }

    val selectedSingleCabinet: CabinetObject?
        get() = selectedCabinetIds.singleOrNull()?.let { id -> document.cabinets.find { it.id == id } }

    val visibleDocument: ProjectDocument
        get() {
            val g = gesture ?: return document
            val candidate = when (g) {
                is CadGesture.DrawWall -> {
                    if (distance(g.start, g.current) >= MIN_WALL_LENGTH_CM) {
                        document.copy(
                            lines = document.lines + WallLine(
                                id = PREVIEW_LINE_ID,
                                start = g.start,
                                end = g.current,
                                thickness = 15f,
                                label = "دیوار جدید",
                                isWall = true,
                            )
                        )
                    } else document
                }
                is CadGesture.DragEndpoint -> document.copy(
                    lines = document.lines.map { line ->
                        if (line.id != g.lineId) line
                        else if (g.isStart) line.copy(start = g.current)
                        else line.copy(end = g.current)
                    }
                )
                is CadGesture.MoveGroup -> document.copy(
                    lines = document.lines.map { line ->
                        if (line.id in selectedLineIds) line.moved(g.delta) else line
                    },
                    cabinets = document.cabinets.map { cab ->
                        if (cab.id in selectedCabinetIds) cab.moved(g.delta) else cab
                    }
                )
                is CadGesture.ResizeCabinet -> document.copy(
                    cabinets = document.cabinets.map { cab ->
                        if (cab.id == g.cabinetId) cab.copy(width = g.width, depth = g.depth) else cab
                    }
                )
                is CadGesture.Measure, is CadGesture.Marquee, is CadGesture.PlaceCabinet -> document
            }
            return if (candidate.valid()) candidate else document
        }

    fun visibleCabinetsForPlan(doc: ProjectDocument = visibleDocument): List<CabinetObject> =
        doc.cabinets
            .filter { cab ->
                if (cab.category == CabinetCategory.WALL && !showWallCabinets) false
                else if (cab.category != CabinetCategory.WALL && !showBaseCabinets) false
                else true
            }
            .sortedBy { it.elevationCm }

    fun commit(next: ProjectDocument): CadEditorState {
        if (!next.valid() || next == document) return clearTransient()
        return clearTransient().copy(
            document = next,
            past = (past + document).takeLast(MAX_HISTORY),
            future = emptyList(),
        )
    }

    fun clearTransient(): CadEditorState = copy(
        gesture = null,
        activeSnap = null,
        activeGuides = emptyList(),
    )

    fun beginGesture(
        rawWorld: Point,
        snapRadiusCm: Float,
        handleRadiusCm: Float = snapRadiusCm,
    ): CadEditorState {
        if (!rawWorld.isFinite) return this

        if (viewMode == ViewMode.ELEVATION_FRONT) {
            // In Front Elevation view, tapping selects cabinets by (x, elevationY)
            val hitCab = document.cabinets.asReversed().firstOrNull { cab ->
                val elevTop = ELEVATION_FLOOR_Y - cab.elevationCm - cab.height
                rawWorld.x in cab.x..(cab.x + cab.width) &&
                    rawWorld.y in elevTop..(elevTop + cab.height)
            }
            return if (hitCab != null) {
                toggleOrSetCabinetSelection(hitCab.id)
            } else if (!multiSelectMode) {
                copy(selectedLineIds = emptySet(), selectedCabinetIds = emptySet())
            } else this
        }

        return when (activeTool) {
            ToolMode.PAN -> this

            ToolMode.PLACE_CABINET -> {
                val snapped = gridSnapPoint(rawWorld)
                copy(gesture = CadGesture.PlaceCabinet(snapped))
            }

            ToolMode.DRAW_WALL -> {
                val snap = SnapEngine.computeSmartSnap(
                    rawPoint = rawWorld,
                    lines = document.lines,
                    cabinets = document.cabinets,
                    snapEnabled = snapEnabled,
                    angleSnapEnabled = angleSnapEnabled,
                    gridStepCm = gridStepCm,
                    snapRadiusCm = snapRadiusCm,
                )
                copy(
                    gesture = CadGesture.DrawWall(snap.snappedPoint, snap.snappedPoint),
                    activeSnap = snap.indicator,
                    activeGuides = snap.guides,
                )
            }

            ToolMode.MEASURE -> {
                val snap = SnapEngine.computeSmartSnap(
                    rawPoint = rawWorld,
                    lines = document.lines,
                    cabinets = document.cabinets,
                    snapEnabled = snapEnabled,
                    angleSnapEnabled = false,
                    gridStepCm = 1f,
                    snapRadiusCm = snapRadiusCm,
                )
                copy(
                    gesture = CadGesture.Measure(snap.snappedPoint, snap.snappedPoint),
                    activeSnap = snap.indicator,
                    activeGuides = emptyList(),
                )
            }

            ToolMode.SELECT -> beginSelectGesture(rawWorld, snapRadiusCm, handleRadiusCm)
        }
    }

    private fun beginSelectGesture(
        rawWorld: Point,
        snapRadiusCm: Float,
        handleRadiusCm: Float,
    ): CadEditorState {
        // 1. Check endpoint handles on already-selected lines
        for (line in document.lines) {
            if (line.id !in selectedLineIds) continue
            if (distance(rawWorld, line.start) <= handleRadiusCm) {
                return copy(
                    gesture = CadGesture.DragEndpoint(
                        lineId = line.id,
                        isStart = true,
                        anchor = line.end,
                        current = line.start,
                    )
                )
            }
            if (distance(rawWorld, line.end) <= handleRadiusCm) {
                return copy(
                    gesture = CadGesture.DragEndpoint(
                        lineId = line.id,
                        isStart = false,
                        anchor = line.start,
                        current = line.end,
                    )
                )
            }
        }

        val visibleCabs = visibleCabinetsForPlan(document)

        // 2. Check bottom-right resize handle on already-selected cabinets
        for (cab in visibleCabs.asReversed()) {
            if (cab.id !in selectedCabinetIds) continue
            val resizeCorner = Point(cab.x + cab.width, cab.y + cab.depth)
            if (distance(rawWorld, resizeCorner) <= handleRadiusCm) {
                return copy(
                    gesture = CadGesture.ResizeCabinet(
                        cabinetId = cab.id,
                        width = cab.width,
                        depth = cab.depth,
                    )
                )
            }
        }

        // 3. Check cabinet body hit (top-most first)
        val hitCab = visibleCabs.asReversed().firstOrNull { it.contains(rawWorld) }
        if (hitCab != null) {
            if (multiSelectMode) {
                return toggleOrSetCabinetSelection(hitCab.id)
            }
            val nextLines = if (hitCab.id in selectedCabinetIds) selectedLineIds else emptySet()
            val nextCabs = if (hitCab.id in selectedCabinetIds) selectedCabinetIds else setOf(hitCab.id)
            return copy(
                selectedLineIds = nextLines,
                selectedCabinetIds = nextCabs,
                gesture = CadGesture.MoveGroup(startWorld = rawWorld),
            )
        }

        // 4. Check wall line segment hit
        val hitLine = document.lines.asReversed().firstOrNull { line ->
            val threshold = max(line.thickness / 2f, snapRadiusCm)
            distanceToSegment(rawWorld, line.start, line.end) <= threshold
        }
        if (hitLine != null) {
            if (multiSelectMode) {
                return toggleOrSetLineSelection(hitLine.id)
            }
            val nextLines = if (hitLine.id in selectedLineIds) selectedLineIds else setOf(hitLine.id)
            val nextCabs = if (hitLine.id in selectedLineIds) selectedCabinetIds else emptySet()
            return copy(
                selectedLineIds = nextLines,
                selectedCabinetIds = nextCabs,
                gesture = CadGesture.MoveGroup(startWorld = rawWorld),
            )
        }

        // 5. Empty space -> start marquee selection box
        val clearedLines = if (multiSelectMode) selectedLineIds else emptySet()
        val clearedCabs = if (multiSelectMode) selectedCabinetIds else emptySet()
        return copy(
            selectedLineIds = clearedLines,
            selectedCabinetIds = clearedCabs,
            gesture = CadGesture.Marquee(rawWorld, rawWorld),
        )
    }

    fun dragGesture(rawWorld: Point, snapRadiusCm: Float): CadEditorState {
        if (!rawWorld.isFinite) return this
        val g = gesture ?: return this

        return when (g) {
            is CadGesture.PlaceCabinet -> copy(
                gesture = CadGesture.PlaceCabinet(gridSnapPoint(rawWorld))
            )

            is CadGesture.DrawWall -> {
                val snap = SnapEngine.computeSmartSnap(
                    rawPoint = rawWorld,
                    lines = document.lines,
                    cabinets = document.cabinets,
                    anchorPoint = g.start,
                    snapEnabled = snapEnabled,
                    angleSnapEnabled = angleSnapEnabled,
                    gridStepCm = gridStepCm,
                    snapRadiusCm = snapRadiusCm,
                )
                copy(
                    gesture = g.copy(current = snap.snappedPoint),
                    activeSnap = snap.indicator,
                    activeGuides = snap.guides,
                )
            }

            is CadGesture.Measure -> {
                val snap = SnapEngine.computeSmartSnap(
                    rawPoint = rawWorld,
                    lines = document.lines,
                    cabinets = document.cabinets,
                    snapEnabled = snapEnabled,
                    angleSnapEnabled = false,
                    gridStepCm = 1f,
                    snapRadiusCm = snapRadiusCm,
                )
                copy(
                    gesture = g.copy(current = snap.snappedPoint),
                    activeSnap = snap.indicator,
                )
            }

            is CadGesture.DragEndpoint -> {
                val snap = SnapEngine.computeSmartSnap(
                    rawPoint = rawWorld,
                    lines = document.lines,
                    cabinets = document.cabinets,
                    excludeLineIds = setOf(g.lineId),
                    anchorPoint = g.anchor,
                    snapEnabled = snapEnabled,
                    angleSnapEnabled = angleSnapEnabled,
                    gridStepCm = gridStepCm,
                    snapRadiusCm = snapRadiusCm,
                )
                copy(
                    gesture = g.copy(current = snap.snappedPoint),
                    activeSnap = snap.indicator,
                    activeGuides = snap.guides,
                )
            }

            is CadGesture.MoveGroup -> {
                val step = if (snapEnabled) gridStepCm.coerceAtLeast(1f) else 1f
                val dx = ((rawWorld.x - g.startWorld.x) / step).roundToInt() * step
                val dy = ((rawWorld.y - g.startWorld.y) / step).roundToInt() * step
                val delta = Point(dx, dy)

                var snapInd: SnapIndicator? = null
                var guides: List<AlignmentGuide> = emptyList()
                val probePoint: Point? = selectedLineIds.firstOrNull()
                    ?.let { id -> document.lines.find { it.id == id }?.start?.plus(delta) }
                    ?: selectedCabinetIds.firstOrNull()
                        ?.let { id -> document.cabinets.find { it.id == id }?.let { Point(it.x, it.y) + delta } }

                if (probePoint != null) {
                    val snapCheck = SnapEngine.computeSmartSnap(
                        rawPoint = probePoint,
                        lines = document.lines,
                        cabinets = document.cabinets,
                        excludeLineIds = selectedLineIds,
                        excludeCabinetIds = selectedCabinetIds,
                        snapEnabled = snapEnabled,
                        angleSnapEnabled = false,
                        gridStepCm = step,
                        snapRadiusCm = snapRadiusCm,
                    )
                    snapInd = snapCheck.indicator
                    guides = snapCheck.guides
                }

                copy(
                    gesture = g.copy(delta = delta),
                    activeSnap = snapInd,
                    activeGuides = guides,
                )
            }

            is CadGesture.ResizeCabinet -> {
                val cab = document.cabinets.find { it.id == g.cabinetId } ?: return this
                val step = if (snapEnabled) 5f else 1f
                val newW = max(25f, ((rawWorld.x - cab.x) / step).roundToInt() * step)
                val newD = max(15f, ((rawWorld.y - cab.y) / step).roundToInt() * step)
                copy(gesture = g.copy(width = newW, depth = newD))
            }

            is CadGesture.Marquee -> copy(gesture = g.copy(current = rawWorld))
        }
    }

    fun finishGesture(): CadEditorState {
        val g = gesture ?: return this
        return when (g) {
            is CadGesture.PlaceCabinet -> placeCatalogItem(
                item = pendingCatalogItem,
                customPosition = g.point - Point(pendingCatalogItem.defaultWidth / 2f, pendingCatalogItem.defaultDepth / 2f),
            )

            is CadGesture.DrawWall -> {
                if (distance(g.start, g.current) >= MIN_WALL_LENGTH_CM) {
                    val newWall = WallLine(
                        start = g.start,
                        end = g.current,
                        thickness = 15f,
                        label = "دیوار جدید",
                        isWall = true,
                    )
                    commit(document.copy(lines = document.lines + newWall)).copy(
                        selectedLineIds = setOf(newWall.id),
                        selectedCabinetIds = emptySet(),
                    )
                } else {
                    clearTransient()
                }
            }

            is CadGesture.DragEndpoint -> {
                if (distance(g.anchor, g.current) < MIN_WALL_LENGTH_CM) {
                    clearTransient()
                } else {
                    commit(visibleDocument)
                }
            }

            is CadGesture.MoveGroup -> {
                if (abs(g.delta.x) >= 0.5f || abs(g.delta.y) >= 0.5f) {
                    commit(visibleDocument)
                } else {
                    clearTransient()
                }
            }

            is CadGesture.ResizeCabinet -> commit(visibleDocument)

            is CadGesture.Marquee -> {
                val minX = min(g.start.x, g.current.x)
                val maxX = max(g.start.x, g.current.x)
                val minY = min(g.start.y, g.current.y)
                val maxY = max(g.start.y, g.current.y)
                if (maxX - minX > 8f || maxY - minY > 8f) {
                    val hitLines = document.lines.filter { l ->
                        (l.start.x in minX..maxX && l.start.y in minY..maxY) ||
                            (l.end.x in minX..maxX && l.end.y in minY..maxY)
                    }.map { it.id }.toSet()
                    val hitCabs = visibleCabinetsForPlan(document).filter { c ->
                        c.center.x in minX..maxX && c.center.y in minY..maxY
                    }.map { it.id }.toSet()
                    clearTransient().copy(
                        selectedLineIds = if (multiSelectMode) selectedLineIds + hitLines else hitLines,
                        selectedCabinetIds = if (multiSelectMode) selectedCabinetIds + hitCabs else hitCabs,
                    )
                } else {
                    clearTransient()
                }
            }

            is CadGesture.Measure -> clearTransient()
        }
    }

    fun placeCatalogItem(item: CatalogItem, customPosition: Point? = null): CadEditorState {
        val offsetIdx = document.cabinets.size % 5
        val rawPos = customPosition ?: Point(140f + offsetIdx * 20f, 130f + offsetIdx * 20f)
        val pos = gridSnapPoint(rawPos)
        val newCab = item.createUnit(pos)
        return commit(document.copy(cabinets = document.cabinets + newCab)).copy(
            activeTool = ToolMode.SELECT,
            selectedLineIds = emptySet(),
            selectedCabinetIds = setOf(newCab.id),
        )
    }

    fun updateLine(updated: WallLine): CadEditorState =
        commit(document.copy(lines = document.lines.map { if (it.id == updated.id) updated else it }))

    fun updateCabinet(updated: CabinetObject): CadEditorState =
        commit(document.copy(cabinets = document.cabinets.map { if (it.id == updated.id) updated else it }))

    fun rotateSelectedCabinets90(): CadEditorState {
        if (selectedCabinetIds.isEmpty()) return this
        val next = document.copy(
            cabinets = document.cabinets.map { cab ->
                if (cab.id in selectedCabinetIds) cab.rotated90() else cab
            }
        )
        return commit(next)
    }

    fun duplicateSelected(): CadEditorState {
        if (totalSelectedCount == 0) return this
        val newLines = ArrayList<WallLine>()
        val newLineIds = LinkedHashSet<String>()
        for (line in document.lines) {
            if (line.id in selectedLineIds) {
                val copyLine = line.copy(
                    id = "wall_${UUID.randomUUID()}",
                    start = line.start + Point(25f, 25f),
                    end = line.end + Point(25f, 25f),
                )
                newLines += copyLine
                newLineIds += copyLine.id
            }
        }
        val newCabs = ArrayList<CabinetObject>()
        val newCabIds = LinkedHashSet<String>()
        for (cab in document.cabinets) {
            if (cab.id in selectedCabinetIds) {
                val copyCab = cab.copy(
                    id = "cab_${UUID.randomUUID()}",
                    x = cab.x + cab.width,
                    y = cab.y,
                )
                newCabs += copyCab
                newCabIds += copyCab.id
            }
        }
        return commit(
            document.copy(
                lines = document.lines + newLines,
                cabinets = document.cabinets + newCabs,
            )
        ).copy(
            selectedLineIds = newLineIds,
            selectedCabinetIds = newCabIds,
        )
    }

    fun deleteSelected(): CadEditorState {
        if (totalSelectedCount == 0) return this
        return commit(
            document.copy(
                lines = document.lines.filterNot { it.id in selectedLineIds },
                cabinets = document.cabinets.filterNot { it.id in selectedCabinetIds },
            )
        ).copy(selectedLineIds = emptySet(), selectedCabinetIds = emptySet())
    }

    fun nudgeSelected(dx: Float, dy: Float): CadEditorState {
        if (totalSelectedCount == 0) return this
        val delta = Point(dx, dy)
        return commit(
            document.copy(
                lines = document.lines.map { if (it.id in selectedLineIds) it.moved(delta) else it },
                cabinets = document.cabinets.map { if (it.id in selectedCabinetIds) it.moved(delta) else it },
            )
        )
    }

    fun alignSelectedGroup(horizontal: Boolean): CadEditorState {
        val selectedCabs = document.cabinets.filter { it.id in selectedCabinetIds }
        if (selectedCabs.size < 2) return this
        val anchor = selectedCabs.first()
        val next = document.copy(
            cabinets = document.cabinets.map { cab ->
                if (cab.id !in selectedCabinetIds) cab
                else if (horizontal) cab.copy(y = anchor.y)
                else cab.copy(x = anchor.x)
            }
        )
        return commit(next)
    }

    fun undo(): CadEditorState {
        val previous = past.lastOrNull() ?: return this
        return clearTransient().copy(
            document = previous,
            selectedLineIds = emptySet(),
            selectedCabinetIds = emptySet(),
            past = past.dropLast(1),
            future = (listOf(document) + future).take(MAX_HISTORY),
        )
    }

    fun redo(): CadEditorState {
        val next = future.firstOrNull() ?: return this
        return clearTransient().copy(
            document = next,
            selectedLineIds = emptySet(),
            selectedCabinetIds = emptySet(),
            past = (past + document).takeLast(MAX_HISTORY),
            future = future.drop(1),
        )
    }

    fun loadDocument(next: ProjectDocument): CadEditorState {
        if (!next.valid()) return this
        return clearTransient().copy(
            document = next,
            selectedLineIds = emptySet(),
            selectedCabinetIds = emptySet(),
            past = emptyList(),
            future = emptyList(),
        )
    }

    private fun toggleOrSetCabinetSelection(id: String): CadEditorState =
        if (multiSelectMode) {
            val next = if (id in selectedCabinetIds) selectedCabinetIds - id else selectedCabinetIds + id
            copy(selectedCabinetIds = next)
        } else {
            copy(selectedLineIds = emptySet(), selectedCabinetIds = setOf(id))
        }

    private fun toggleOrSetLineSelection(id: String): CadEditorState =
        if (multiSelectMode) {
            val next = if (id in selectedLineIds) selectedLineIds - id else selectedLineIds + id
            copy(selectedLineIds = next)
        } else {
            copy(selectedLineIds = setOf(id), selectedCabinetIds = emptySet())
        }

    private fun gridSnapPoint(p: Point): Point {
        val step = if (snapEnabled) gridStepCm.coerceAtLeast(1f) else 1f
        return Point(
            x = ((p.x / step).roundToInt() * step),
            y = ((p.y / step).roundToInt() * step),
        )
    }

    companion object {
        const val MAX_HISTORY = 60
        const val MIN_WALL_LENGTH_CM = 10f
        const val ELEVATION_FLOOR_Y = 340f
        const val ELEVATION_CEILING_CM = 280f
        private const val PREVIEW_LINE_ID = "__preview_wall__"
    }
}
