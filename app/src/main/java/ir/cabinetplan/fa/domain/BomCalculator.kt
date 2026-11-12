package ir.cabinetplan.fa.domain

import kotlin.math.max

data class BomSummary(
    val baseRunningCm: Float,
    val wallRunningCm: Float,
    val tallRunningCm: Float,
    val islandRunningCm: Float,
    val totalUnits: Int,
    val items: List<CabinetObject>,
)

object BomCalculator {
    /**
     * Computes union-standard running meters (متر طول اتحادیه کابینت‌سازان)
     * across Base (including Sink/Gas under-units), Wall, Tall, and Island categories.
     */
    fun calculate(cabinets: List<CabinetObject>): BomSummary {
        var baseCm = 0f
        var wallCm = 0f
        var tallCm = 0f
        var islandCm = 0f

        for (cab in cabinets) {
            val span = if (cab.rotation == 90 || cab.rotation == 270) cab.depth else cab.width
            val effectiveSpan = max(span, 0f)
            when {
                cab.category == CabinetCategory.BASE ||
                    cab.symbolType == CabinetSymbolType.SINK_DOUBLE ||
                    cab.symbolType == CabinetSymbolType.GAS_HOB -> baseCm += effectiveSpan
                cab.category == CabinetCategory.WALL -> wallCm += effectiveSpan
                cab.category == CabinetCategory.TALL -> tallCm += effectiveSpan
                cab.category == CabinetCategory.ISLAND -> islandCm += effectiveSpan
                else -> Unit
            }
        }

        return BomSummary(
            baseRunningCm = baseCm,
            wallRunningCm = wallCm,
            tallRunningCm = tallCm,
            islandRunningCm = islandCm,
            totalUnits = cabinets.size,
            items = cabinets,
        )
    }
}
