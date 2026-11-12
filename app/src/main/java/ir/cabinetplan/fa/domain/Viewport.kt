package ir.cabinetplan.fa.domain

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Physical canvas coordinates in centimeters (1 world unit = 1 cm); never mirrored in RTL. */
data class Point(val x: Float = 0f, val y: Float = 0f) {
    val isFinite: Boolean get() = x.isFinite() && y.isFinite()
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun minus(other: Point) = Point(x - other.x, y - other.y)
    operator fun times(scale: Float) = Point(x * scale, y * scale)
    operator fun div(scale: Float) = Point(x / scale, y / scale)
}

data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val center: Point get() = Point((left + right) / 2f, (top + bottom) / 2f)

    fun union(other: Bounds): Bounds = Bounds(
        left = min(left, other.left),
        top = min(top, other.top),
        right = max(right, other.right),
        bottom = max(bottom, other.bottom),
    )
}

fun distance(a: Point, b: Point): Float = hypot(a.x - b.x, a.y - b.y)

/** Pan is measured in screen pixels relative to canvas center; world units are centimeters. */
data class Viewport(val zoom: Float = 1f, val pan: Point = Point()) {
    val pixelsPerCm: Float get() = BASE_PIXELS_PER_CM * zoom

    fun worldToScreen(world: Point, center: Point): Point =
        center + pan + world * pixelsPerCm

    fun screenToWorld(screen: Point, center: Point): Point =
        (screen - center - pan) / pixelsPerCm

    /** Keeps the world coordinate underneath the fingers anchored during pinch-to-zoom. */
    fun transform(centroidFromCenter: Point, panChange: Point, zoomChange: Float): Viewport {
        if (!centroidFromCenter.isFinite || !panChange.isFinite ||
            !zoomChange.isFinite() || zoomChange <= 0f
        ) return this
        val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val ratio = newZoom / zoom
        val newPan = centroidFromCenter - (centroidFromCenter - pan) * ratio + panChange
        return if (newPan.isFinite) copy(zoom = newZoom, pan = newPan) else this
    }

    companion object {
        const val BASE_PIXELS_PER_CM = 2.2f
        const val MIN_ZOOM = 0.15f
        const val MAX_ZOOM = 6f
        const val MINOR_GRID_CM = 10
        const val MAJOR_GRID_CM = 100

        fun fitBounds(
            bounds: Bounds,
            screenWidth: Float,
            screenHeight: Float,
            paddingPx: Float = 56f,
        ): Viewport {
            if (screenWidth <= paddingPx * 2f || screenHeight <= paddingPx * 2f) return Viewport()
            val availW = screenWidth - paddingPx * 2f
            val availH = screenHeight - paddingPx * 2f
            val contentW = max(bounds.width, 60f)
            val contentH = max(bounds.height, 60f)
            val targetPxPerCm = min(availW / contentW, availH / contentH)
            val targetZoom = (targetPxPerCm / BASE_PIXELS_PER_CM).coerceIn(MIN_ZOOM, MAX_ZOOM)
            val pan = bounds.center * (-BASE_PIXELS_PER_CM * targetZoom)
            return Viewport(zoom = targetZoom, pan = pan)
        }
    }
}
