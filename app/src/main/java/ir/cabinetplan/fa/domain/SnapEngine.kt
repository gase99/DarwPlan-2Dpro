package ir.cabinetplan.fa.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class SnapType {
    VERTEX,
    MIDPOINT,
    CABINET_CORNER,
    ANGLE,
    GRID,
}

data class SnapIndicator(
    val point: Point,
    val type: SnapType,
    val labelFa: String,
    val sourceId: String? = null,
)

data class AlignmentGuide(
    val vertical: Boolean,
    val position: Float,
    val from: Float,
    val to: Float,
    val labelFa: String,
)

data class SmartSnapResult(
    val snappedPoint: Point,
    val indicator: SnapIndicator?,
    val guides: List<AlignmentGuide>,
)

fun distanceToSegment(point: Point, a: Point, b: Point): Float {
    val v = b - a
    val squared = v.x * v.x + v.y * v.y
    if (squared == 0f) return distance(point, a)
    val w = point - a
    val t = ((w.x * v.x + w.y * v.y) / squared).coerceIn(0f, 1f)
    return distance(point, a + v * t)
}

object SnapEngine {
    private val SNAP_ANGLES = intArrayOf(0, 45, 90, 135, 180, -45, -90, -135, -180)

    fun computeSmartSnap(
        rawPoint: Point,
        lines: List<WallLine>,
        cabinets: List<CabinetObject>,
        excludeLineIds: Set<String> = emptySet(),
        excludeCabinetIds: Set<String> = emptySet(),
        anchorPoint: Point? = null,
        snapEnabled: Boolean = true,
        angleSnapEnabled: Boolean = true,
        gridStepCm: Float = 5f,
        snapRadiusCm: Float = 8f,
    ): SmartSnapResult {
        val safeStep = gridStepCm.coerceAtLeast(1f)
        if (!snapEnabled) {
            return SmartSnapResult(
                snappedPoint = Point(rawPoint.x.roundToInt().toFloat(), rawPoint.y.roundToInt().toFloat()),
                indicator = null,
                guides = emptyList(),
            )
        }

        val candidates = ArrayList<SnapIndicator>(lines.size * 3 + cabinets.size * 4)
        for (line in lines) {
            if (line.id in excludeLineIds) continue
            candidates += SnapIndicator(line.start, SnapType.VERTEX, "چسبیدن به گره خط", line.id)
            candidates += SnapIndicator(line.end, SnapType.VERTEX, "چسبیدن به گره خط", line.id)
            candidates += SnapIndicator(line.midpoint, SnapType.MIDPOINT, "وسط ضلع", line.id)
        }
        for (cab in cabinets) {
            if (cab.id in excludeCabinetIds) continue
            for (corner in cab.corners) {
                candidates += SnapIndicator(
                    point = corner,
                    type = SnapType.CABINET_CORNER,
                    labelFa = "گوشه ${cab.nameFa}",
                    sourceId = cab.id,
                )
            }
        }

        // 1. Direct magnetic snap to vertex / midpoint / cabinet corner
        var bestSnap: SnapIndicator? = null
        var minDist = snapRadiusCm
        for (target in candidates) {
            val d = distance(rawPoint, target.point)
            if (d <= minDist) {
                minDist = d
                bestSnap = target
            }
        }

        var workingX = bestSnap?.point?.x ?: ((rawPoint.x / safeStep).roundToInt() * safeStep)
        var workingY = bestSnap?.point?.y ?: ((rawPoint.y / safeStep).roundToInt() * safeStep)

        // 2. Angle snap (45 / 90 deg) relative to anchor point when drawing/editing wall
        if (bestSnap == null && anchorPoint != null && angleSnapEnabled) {
            val dx = workingX - anchorPoint.x
            val dy = workingY - anchorPoint.y
            val len = hypot(dx, dy)
            if (len > 5f) {
                val rawDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                for (targetAngle in SNAP_ANGLES) {
                    if (abs(rawDeg - targetAngle) <= 6f) {
                        val rad = Math.toRadians(targetAngle.toDouble())
                        val snappedLen = (len / safeStep).roundToInt() * safeStep
                        workingX = (anchorPoint.x + cos(rad) * snappedLen).roundToInt().toFloat()
                        workingY = (anchorPoint.y + sin(rad) * snappedLen).roundToInt().toFloat()
                        bestSnap = SnapIndicator(
                            point = Point(workingX, workingY),
                            type = SnapType.ANGLE,
                            labelFa = "قفل زاویه ${toPersianDigits(abs(targetAngle))}°",
                        )
                        break
                    }
                }
            }
        }

        // 3. Dynamic orthogonal alignment guides (horizontal & vertical)
        val guides = ArrayList<AlignmentGuide>(3)
        val alignTol = max(4f, snapRadiusCm * 0.65f)
        for (target in candidates) {
            if (bestSnap != null && distance(target.point, Point(workingX, workingY)) < 0.1f) continue

            if (abs(workingX - target.point.x) <= alignTol) {
                if (bestSnap == null) workingX = target.point.x
                val aligned = abs(workingX - target.point.x) < 0.5f
                val duplicate = guides.any { it.vertical && abs(it.position - target.point.x) < 1f }
                if (aligned && !duplicate && guides.size < 3) {
                    guides += AlignmentGuide(
                        vertical = true,
                        position = target.point.x,
                        from = min(workingY, target.point.y),
                        to = max(workingY, target.point.y),
                        labelFa = "تراز عمودی (${formatCm(abs(workingY - target.point.y))})",
                    )
                }
            }

            if (abs(workingY - target.point.y) <= alignTol) {
                if (bestSnap == null) workingY = target.point.y
                val aligned = abs(workingY - target.point.y) < 0.5f
                val duplicate = guides.any { !it.vertical && abs(it.position - target.point.y) < 1f }
                if (aligned && !duplicate && guides.size < 3) {
                    guides += AlignmentGuide(
                        vertical = false,
                        position = target.point.y,
                        from = min(workingX, target.point.x),
                        to = max(workingX, target.point.x),
                        labelFa = "تراز افقی (${formatCm(abs(workingX - target.point.x))})",
                    )
                }
            }
        }

        return SmartSnapResult(
            snappedPoint = Point(workingX, workingY),
            indicator = bestSnap,
            guides = guides,
        )
    }
}
