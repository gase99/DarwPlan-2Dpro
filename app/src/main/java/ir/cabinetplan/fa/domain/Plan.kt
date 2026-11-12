package ir.cabinetplan.fa.domain

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import java.util.UUID

data class PlanLine(
    val id: String = UUID.randomUUID().toString(),
    val start: Point,
    val end: Point,
    val group: String? = null,
    val kind: CabinetKind? = null,
) {
    val length: Float get() = distance(start, end)
    fun moved(delta: Point) = copy(start = start + delta, end = end + delta)
}

data class PlanDocument(val lines: List<PlanLine> = emptyList()) {
    val bounds: Bounds? get() {
        val points = lines.flatMap { listOf(it.start, it.end) }
        if (points.isEmpty()) return null
        return Bounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    }
    fun valid(): Boolean = lines.size <= MAX_LINES && lines.map { it.id }.distinct().size == lines.size &&
        lines.all { line ->
            line.id.isNotBlank() && line.start.isFinite && line.end.isFinite && line.length >= 0.1f &&
                listOf(line.start.x, line.start.y, line.end.x, line.end.y).all { abs(it) <= MAX_COORDINATE }
        }
    companion object {
        const val MAX_LINES = 10000
        const val MAX_COORDINATE = 100000f
    }
}

data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val center: Point get() = Point((left + right) / 2, (top + bottom) / 2)
}

enum class CabinetKind(val width: Float, val depth: Float) {
    BASE(600f, 600f), WALL(600f, 350f), DRAWER(450f, 550f), DOOR(600f, 30f), SHELF(800f, 350f);

    /** A symbol is a group of editable millimeter-based segments, not a bitmap. */
    fun create(center: Point, width: Float = this.width, depth: Float = this.depth): List<PlanLine> {
        require(width in 10f..10000f && depth in 10f..10000f)
        val group = UUID.randomUUID().toString()
        val origin = center - Point(width / 2, depth / 2)
        fun segment(a: Point, b: Point) = PlanLine(start = origin + a, end = origin + b, group = group, kind = this)
        val result = mutableListOf(
            segment(Point(), Point(width, 0f)), segment(Point(width, 0f), Point(width, depth)),
            segment(Point(width, depth), Point(0f, depth)), segment(Point(0f, depth), Point()),
        )
        when (this) {
            BASE, WALL -> result += segment(Point(width / 2, 0f), Point(width / 2, depth))
            DRAWER -> for (i in 1..2) result += segment(Point(0f, depth * i / 3), Point(width, depth * i / 3))
            DOOR -> result += segment(Point(), Point(width, depth))
            SHELF -> result += segment(Point(0f, depth / 2), Point(width, depth / 2))
        }
        return result
    }
}

fun distance(a: Point, b: Point): Float = hypot(a.x - b.x, a.y - b.y)
fun distanceToSegment(point: Point, line: PlanLine): Float {
    val v = line.end - line.start
    val squared = v.x * v.x + v.y * v.y
    if (squared == 0f) return distance(point, line.start)
    val w = point - line.start
    val t = ((w.x * v.x + w.y * v.y) / squared).coerceIn(0f, 1f)
    return distance(point, line.start + v * t)
}

data class SnapResult(val point: Point, val vertex: Boolean = false, val guideX: Float? = null, val guideY: Float? = null)

object SnapEngine {
    fun snap(point: Point, lines: List<PlanLine>, tolerance: Float, grid: Boolean = true): SnapResult {
        require(tolerance.isFinite() && tolerance >= 0)
        val vertices = lines.flatMap { listOf(it.start, it.end) }
        val nearest = vertices.minByOrNull { distance(point, it) }
        if (nearest != null && distance(point, nearest) <= tolerance) {
            return SnapResult(nearest, vertex = true, guideX = nearest.x, guideY = nearest.y)
        }
        val x = vertices.minByOrNull { abs(it.x - point.x) }?.x?.takeIf { abs(it - point.x) <= tolerance }
        val y = vertices.minByOrNull { abs(it.y - point.y) }?.y?.takeIf { abs(it - point.y) <= tolerance }
        val gridPoint = Point(round(point.x / 10f) * 10f, round(point.y / 10f) * 10f)
        val useGrid = grid && distance(point, gridPoint) <= tolerance
        return SnapResult(Point(x ?: if (useGrid) gridPoint.x else point.x, y ?: if (useGrid) gridPoint.y else point.y),
            guideX = x, guideY = y)
    }
}

fun fitViewport(document: PlanDocument, width: Float, height: Float): Viewport {
    val bounds = document.bounds ?: return Viewport()
    val zoom = min(max(1f, width - 80f) / max(bounds.width * Viewport.BASE_PIXELS_PER_MM, 1f),
        max(1f, height - 80f) / max(bounds.height * Viewport.BASE_PIXELS_PER_MM, 1f))
        .coerceIn(Viewport.MIN_ZOOM, Viewport.MAX_ZOOM)
    return Viewport(zoom, bounds.center * (-Viewport.BASE_PIXELS_PER_MM * zoom))
}
