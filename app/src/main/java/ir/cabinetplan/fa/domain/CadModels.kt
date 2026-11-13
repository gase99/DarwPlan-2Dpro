package ir.cabinetplan.fa.domain

import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ToolMode {
    SELECT,
    DRAW_WALL,
    MEASURE,
    PAN,
    PLACE_CABINET,
}

enum class ViewMode {
    PLAN_2D,
    ELEVATION_FRONT,
}

enum class CabinetCategory(val labelFa: String, val shortFa: String) {
    BASE("کابینت زمینی", "زمینی"),
    WALL("کابینت دیواری", "دیواری"),
    TALL("کمدی و ایستاده", "ایستاده"),
    ISLAND("جزیره و پیشخوان", "جزیره"),
    FIXTURE("تجهیزات و بازشو", "تجهیزات"),
}

enum class CabinetSymbolType {
    BASE_SINGLE,
    BASE_DOUBLE,
    BASE_DRAWERS,
    BASE_CORNER,
    WALL_DOUBLE,
    WALL_DISHRACK,
    WALL_LIFT,
    TALL_PANTRY,
    TALL_OVEN,
    FRIDGE_ENCLOSURE,
    ISLAND_UNIT,
    SINK_DOUBLE,
    GAS_HOB,
    DOOR_SWING,
    WINDOW_DOUBLE,
    COLUMN_PILLAR,
}

data class WallLine(
    val id: String = "wall_${UUID.randomUUID()}",
    val start: Point,
    val end: Point,
    val thickness: Float = 15f,
    val label: String = "دیوار سازه‌ای",
    val isWall: Boolean = true,
) {
    val lengthCm: Float
        get() = ((distance(start, end) * 10f).roundToInt()) / 10f

    val midpoint: Point
        get() = Point((start.x + end.x) / 2f, (start.y + end.y) / 2f)

    val angleDeg: Float
        get() {
            val deg = Math.toDegrees(atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble()))
            return (((deg.roundToInt()) % 360) + 360) % 360 * 1f
        }

    val bounds: Bounds
        get() {
            val halfT = thickness / 2f
            return Bounds(
                left = minOf(start.x, end.x) - halfT,
                top = minOf(start.y, end.y) - halfT,
                right = maxOf(start.x, end.x) + halfT,
                bottom = maxOf(start.y, end.y) + halfT,
            )
        }

    fun moved(delta: Point): WallLine = copy(start = start + delta, end = end + delta)

    fun withLength(newLengthCm: Float): WallLine {
        if (!newLengthCm.isFinite() || newLengthCm !in 5f..MAX_WALL_LENGTH_CM) return this
        val rad = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
        val nx = (start.x + cos(rad) * newLengthCm).roundToInt().toFloat()
        val ny = (start.y + sin(rad) * newLengthCm).roundToInt().toFloat()
        return copy(end = Point(nx, ny))
    }

    fun straightened(horizontal: Boolean): WallLine {
        val len = lengthCm.coerceAtLeast(10f)
        return if (horizontal) {
            copy(end = Point((start.x + len).roundToInt().toFloat(), start.y))
        } else {
            copy(end = Point(start.x, (start.y + len).roundToInt().toFloat()))
        }
    }

    companion object {
        const val MAX_WALL_LENGTH_CM = 20_000f
    }
}

data class CabinetObject(
    val id: String = "cab_${UUID.randomUUID()}",
    val catalogId: String,
    val symbolType: CabinetSymbolType,
    val category: CabinetCategory,
    val nameFa: String,
    val codeFa: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val depth: Float,
    val height: Float,
    val elevationCm: Float = 0f,
    val rotation: Int = 0,
    val fillArgb: Long = 0x38E09F3EL,
    val strokeArgb: Long = 0xFFE09F3EL,
    val doorsCount: Int = 0,
    val drawersCount: Int = 0,
    val notes: String = "",
) {
    val bounds: Bounds
        get() = Bounds(x, y, x + width, y + depth)

    val center: Point
        get() = Point(x + width / 2f, y + depth / 2f)

    val corners: List<Point>
        get() = listOf(
            Point(x, y),
            Point(x + width, y),
            Point(x, y + depth),
            Point(x + width, y + depth),
        )

    fun contains(point: Point): Boolean =
        point.x in x..(x + width) && point.y in y..(y + depth)

    fun moved(delta: Point): CabinetObject = copy(x = x + delta.x, y = y + delta.y)

    /** Rotates 90 degrees around top-left/footprint Swap as expected in 2D top-down kitchen plans. */
    fun rotated90(): CabinetObject = copy(
        rotation = ((rotation + 90) % 360 + 360) % 360,
        width = depth,
        depth = width,
    )
}

data class CatalogItem(
    val id: String,
    val symbolType: CabinetSymbolType,
    val category: CabinetCategory,
    val nameFa: String,
    val codeFa: String,
    val descriptionFa: String,
    val defaultWidth: Float,
    val defaultDepth: Float,
    val defaultHeight: Float,
    val elevationCm: Float,
    val fillArgb: Long,
    val strokeArgb: Long,
    val doorsCount: Int = 0,
    val drawersCount: Int = 0,
) {
    fun createUnit(position: Point): CabinetObject = CabinetObject(
        catalogId = id,
        symbolType = symbolType,
        category = category,
        nameFa = nameFa,
        codeFa = codeFa,
        x = position.x,
        y = position.y,
        width = defaultWidth,
        depth = defaultDepth,
        height = defaultHeight,
        elevationCm = elevationCm,
        rotation = 0,
        fillArgb = fillArgb,
        strokeArgb = strokeArgb,
        doorsCount = doorsCount,
        drawersCount = drawersCount,
    )
}

data class ProjectDocument(
    val lines: List<WallLine> = emptyList(),
    val cabinets: List<CabinetObject> = emptyList(),
    val clientNameFa: String = "",
    val notesFa: String = "",
) {
    val isEmpty: Boolean
        get() = lines.isEmpty() && cabinets.isEmpty()

    val hasGeometry: Boolean
        get() = lines.isNotEmpty() || cabinets.isNotEmpty()

    val bounds: Bounds?
        get() {
            val allBounds = lines.map { it.bounds } + cabinets.map { it.bounds }
            if (allBounds.isEmpty()) return null
            return allBounds.reduce { acc, b -> acc.union(b) }
        }

    fun valid(): Boolean {
        if (lines.size > MAX_ITEMS || cabinets.size > MAX_ITEMS) return false
        if (clientNameFa.length > 500 || notesFa.length > 4000) return false
        if (lines.map { it.id }.distinct().size != lines.size) return false
        if (cabinets.map { it.id }.distinct().size != cabinets.size) return false
        val linesValid = lines.all { line ->
            line.id.isNotBlank() &&
                line.start.isFinite &&
                line.end.isFinite &&
                distance(line.start, line.end) >= 0.5f &&
                line.thickness.isFinite() && line.thickness in 1f..200f &&
                listOf(line.start.x, line.start.y, line.end.x, line.end.y).all { abs(it) <= MAX_COORDINATE_CM }
        }
        val cabinetsValid = cabinets.all { cab ->
            cab.id.isNotBlank() &&
                cab.nameFa.isNotBlank() &&
                cab.x.isFinite() && cab.y.isFinite() &&
                abs(cab.x) <= MAX_COORDINATE_CM && abs(cab.y) <= MAX_COORDINATE_CM &&
                cab.width.isFinite() && cab.width in 5f..2000f &&
                cab.depth.isFinite() && cab.depth in 5f..2000f &&
                cab.height.isFinite() && cab.height in 5f..1000f &&
                cab.elevationCm.isFinite() && cab.elevationCm in 0f..1000f
        }
        return linesValid && cabinetsValid
    }

    companion object {
        const val MAX_ITEMS = 10_000
        const val MAX_COORDINATE_CM = 100_000f
    }
}
