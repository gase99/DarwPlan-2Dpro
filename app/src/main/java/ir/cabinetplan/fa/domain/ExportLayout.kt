package ir.cabinetplan.fa.domain

import kotlin.math.max
import kotlin.math.min

/** Aspect-preserving world-to-sheet layout shared by raster PNG and vector PDF exporters. */
data class ExportLayout(val scale: Float, val offset: Point) {
    fun map(point: Point): Point = point * scale + offset

    companion object {
        fun fit(
            bounds: Bounds,
            width: Float,
            height: Float,
            padding: Float,
            header: Float = 0f,
            footer: Float = 0f,
            rightPanelWidth: Float = 0f,
        ): ExportLayout {
            val drawWidth = width - rightPanelWidth - padding * 2f
            val drawHeight = height - header - footer - padding * 2f
            require(drawWidth > 10f && drawHeight > 10f)
            val contentW = max(bounds.width, 40f)
            val contentH = max(bounds.height, 40f)
            val scale = min(drawWidth / contentW, drawHeight / contentH)
            val targetCenter = Point(
                x = padding + drawWidth / 2f,
                y = header + padding + drawHeight / 2f,
            )
            return ExportLayout(scale = scale, offset = targetCenter - bounds.center * scale)
        }
    }
}
