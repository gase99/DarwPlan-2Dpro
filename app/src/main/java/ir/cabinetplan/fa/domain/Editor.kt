package ir.cabinetplan.fa.domain

import kotlin.math.max
import kotlin.math.min

enum class Tool { PAN, LINE, SELECT, PLACE }
sealed interface EditGesture {
    data class Draw(val line: PlanLine) : EditGesture
    data class Move(val anchor: Point, val delta: Point = Point()) : EditGesture
    data class Endpoint(val id: String, val start: Boolean, val point: Point) : EditGesture
    data class Place(val point: Point) : EditGesture
    data class Marquee(val anchor: Point, val point: Point) : EditGesture
}

data class EditorState(
    val document: PlanDocument = PlanDocument(),
    val selected: Set<String> = emptySet(),
    val viewport: Viewport = Viewport(),
    val tool: Tool = Tool.PAN,
    val cabinet: CabinetKind = CabinetKind.BASE,
    val multiple: Boolean = false,
    val gridSnap: Boolean = true,
    val dimensions: Boolean = true,
    val gesture: EditGesture? = null,
    val snap: SnapResult? = null,
    val past: List<PlanDocument> = emptyList(),
    val future: List<PlanDocument> = emptyList(),
) {
    val visibleDocument: PlanDocument get() = when (val edit = gesture) {
        is EditGesture.Draw -> PlanDocument(document.lines + edit.line)
        is EditGesture.Move -> PlanDocument(document.lines.map { if (it.id in selected) it.moved(edit.delta) else it })
        is EditGesture.Endpoint -> PlanDocument(document.lines.map {
            if (it.id != edit.id) it else if (edit.start) it.copy(start = edit.point) else it.copy(end = edit.point)
        })
        else -> document
    }

    fun commit(next: PlanDocument): EditorState {
        if (next == document || !next.valid()) return copy(gesture = null, snap = null)
        return copy(document = next, selected = selected.intersect(next.lines.map { it.id }.toSet()),
            past = (past + document).takeLast(60), future = emptyList(), gesture = null, snap = null)
    }

    fun begin(point: Point, tolerance: Float): EditorState {
        if (!point.isFinite) return this
        return when (tool) {
            Tool.PAN -> this
            Tool.LINE -> {
                val snapped = SnapEngine.snap(point, document.lines, tolerance, gridSnap)
                copy(gesture = EditGesture.Draw(PlanLine(start = snapped.point, end = snapped.point)), snap = snapped)
            }
            Tool.PLACE -> copy(gesture = EditGesture.Place(point))
            Tool.SELECT -> {
                val only = document.lines.singleOrNull { it.id in selected && it.group == null }
                if (!multiple && selected.size == 1 && only != null) {
                    if (distance(point, only.start) <= tolerance) return copy(gesture = EditGesture.Endpoint(only.id, true, only.start))
                    if (distance(point, only.end) <= tolerance) return copy(gesture = EditGesture.Endpoint(only.id, false, only.end))
                }
                val hit = document.lines.asReversed().minByOrNull { distanceToSegment(point, it) }
                    ?.takeIf { distanceToSegment(point, it) <= tolerance }
                if (hit == null) return copy(selected = if (multiple) selected else emptySet(),
                    gesture = EditGesture.Marquee(point, point))
                val ids = document.lines.filter { it.id == hit.id || (hit.group != null && it.group == hit.group) }.map { it.id }.toSet()
                if (multiple) copy(selected = if (hit.id in selected) selected - ids else selected + ids, gesture = null)
                else copy(selected = if (hit.id in selected) selected else ids, gesture = EditGesture.Move(point))
            }
        }
    }

    fun drag(point: Point, tolerance: Float): EditorState {
        if (!point.isFinite) return this
        return when (val edit = gesture) {
            is EditGesture.Draw -> {
                val snap = SnapEngine.snap(point, document.lines + edit.line.copy(end = edit.line.start), tolerance, gridSnap)
                copy(gesture = edit.copy(line = edit.line.copy(end = snap.point)), snap = snap)
            }
            is EditGesture.Endpoint -> {
                val snap = SnapEngine.snap(point, document.lines.filter { it.id != edit.id }, tolerance, gridSnap)
                copy(gesture = edit.copy(point = snap.point), snap = snap)
            }
            is EditGesture.Move -> {
                val delta = point - edit.anchor
                val reference = document.lines.firstOrNull { it.id in selected }?.start ?: return this
                val snap = SnapEngine.snap(reference + delta, document.lines.filter { it.id !in selected }, tolerance, gridSnap)
                copy(gesture = edit.copy(delta = snap.point - reference), snap = snap)
            }
            is EditGesture.Place -> copy(gesture = edit.copy(point = point))
            is EditGesture.Marquee -> copy(gesture = edit.copy(point = point))
            null -> this
        }
    }

    fun finish(): EditorState = when (val edit = gesture) {
        is EditGesture.Place -> place(cabinet, edit.point)
        is EditGesture.Marquee -> {
            val left = min(edit.anchor.x, edit.point.x); val right = max(edit.anchor.x, edit.point.x)
            val top = min(edit.anchor.y, edit.point.y); val bottom = max(edit.anchor.y, edit.point.y)
            val ids = document.lines.filter { line -> listOf(line.start, line.end).all {
                it.x in left..right && it.y in top..bottom
            } }.map { it.id }.toSet()
            // A rectangle touching one cabinet selects its complete symbol.
            val groups = document.lines.filter { it.id in ids }.mapNotNull { it.group }.toSet()
            val expanded = document.lines.filter { it.id in ids || it.group in groups }.map { it.id }.toSet()
            copy(selected = selected + expanded, gesture = null, snap = null)
        }
        else -> commit(visibleDocument)
    }

    fun place(kind: CabinetKind, point: Point): EditorState {
        if (!point.isFinite) return this
        val lines = kind.create(point)
        val next = commit(PlanDocument(document.lines + lines))
        return if (next.document.lines.size == document.lines.size + lines.size)
            next.copy(selected = lines.map { it.id }.toSet(), tool = Tool.SELECT) else next
    }

    fun undo(): EditorState = if (past.isEmpty()) this else copy(document = past.last(), past = past.dropLast(1),
        future = (future + document).takeLast(60), selected = emptySet(), gesture = null, snap = null)
    fun redo(): EditorState = if (future.isEmpty()) this else copy(document = future.last(), future = future.dropLast(1),
        past = (past + document).takeLast(60), selected = emptySet(), gesture = null, snap = null)
}
