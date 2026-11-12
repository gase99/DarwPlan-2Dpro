package ir.cabinetplan.fa.domain

import kotlin.math.max
import kotlin.math.min

/** Same aspect-preserving geometry is used for raster and vector output. */
data class ExportLayout(val scale: Float, val offset: Point) {
    fun map(point: Point): Point = point * scale + offset
    companion object {
        fun fit(bounds: Bounds, width: Float, height: Float, padding: Float, header: Float): ExportLayout {
            require(width > padding * 2 && height > padding * 2 + header)
            val scale = min((width - padding * 2) / max(bounds.width, 1f),
                (height - padding * 2 - header) / max(bounds.height, 1f))
            val target = Point(width / 2, header + (height - header) / 2)
            return ExportLayout(scale, target - bounds.center * scale)
        }
    }
}
