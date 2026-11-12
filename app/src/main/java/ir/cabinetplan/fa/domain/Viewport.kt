package ir.cabinetplan.fa.domain

/** Physical canvas coordinates do not mirror in RTL; only the surrounding UI does. */
data class Point(val x: Float = 0f, val y: Float = 0f) {
    val isFinite: Boolean get() = x.isFinite() && y.isFinite()
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun minus(other: Point) = Point(x - other.x, y - other.y)
    operator fun times(scale: Float) = Point(x * scale, y * scale)
    operator fun div(scale: Float) = Point(x / scale, y / scale)
}

/** Pan is measured in screen pixels relative to the center; world units are millimeters. */
data class Viewport(val zoom: Float = 1f, val pan: Point = Point()) {
    val pixelsPerMillimeter: Float get() = BASE_PIXELS_PER_MM * zoom

    fun worldToScreen(world: Point, center: Point): Point =
        center + pan + world * pixelsPerMillimeter

    fun screenToWorld(screen: Point, center: Point): Point =
        (screen - center - pan) / pixelsPerMillimeter

    /** Keep the world point underneath the fingers anchored while zooming. */
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
        const val BASE_PIXELS_PER_MM = 2f
        const val MIN_ZOOM = 0.05f
        const val MAX_ZOOM = 8f
        const val GRID_MILLIMETERS = 50
    }
}
