package ir.cabinetplan.fa.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.cabinetplan.fa.domain.*
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ExportFormat { PNG, PDF }

interface PlanExport {
    suspend fun write(uri: Uri, document: ProjectDocument, title: String, format: ExportFormat)
}

class AndroidPlanExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlanExport {

    override suspend fun write(
        uri: Uri,
        document: ProjectDocument,
        title: String,
        format: ExportFormat,
    ) = withContext(Dispatchers.IO) {
        require(document.valid() && document.hasGeometry)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { stream ->
            when (format) {
                ExportFormat.PNG -> {
                    val bitmap = Bitmap.createBitmap(PNG_WIDTH, PNG_HEIGHT, Bitmap.Config.ARGB_8888)
                    try {
                        drawDarkBlueprintPng(Canvas(bitmap), document, title, PNG_WIDTH.toFloat(), PNG_HEIGHT.toFloat())
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    } finally {
                        bitmap.recycle()
                    }
                }
                ExportFormat.PDF -> {
                    val pdf = PdfDocument()
                    try {
                        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, 1).create())
                        drawPrintableVectorPdf(page.canvas, document, title, PDF_WIDTH.toFloat(), PDF_HEIGHT.toFloat())
                        pdf.finishPage(page)
                        pdf.writeTo(stream)
                    } finally {
                        pdf.close()
                    }
                }
            }
        }
    }

    private fun drawDarkBlueprintPng(
        canvas: Canvas,
        document: ProjectDocument,
        title: String,
        width: Float,
        height: Float,
    ) {
        val bounds = requireNotNull(document.bounds)
        val footerHeight = 190f
        val margin = 44f
        val layout = ExportLayout.fit(
            bounds = bounds,
            width = width,
            height = height,
            padding = margin + 48f,
            header = 24f,
            footer = footerHeight,
        )

        // 1. Deep Blueprint Background
        canvas.drawColor(Color.rgb(15, 20, 28))

        // 2. Subtle Architectural Grid
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.rgb(24, 33, 47)
        }
        val minorStep = (10f * layout.scale).coerceAtLeast(14f)
        var gx = margin
        while (gx < width - margin) {
            canvas.drawLine(gx, margin, gx, height - margin - footerHeight, gridPaint)
            gx += minorStep
        }
        var gy = margin
        while (gy < height - margin - footerHeight) {
            canvas.drawLine(margin, gy, width - margin, gy, gridPaint)
            gy += minorStep
        }

        // 3. Draw CAD Geometry (Cabinets + Walls + Dimension Badges)
        drawPlanGeometry(
            canvas = canvas,
            document = document,
            layout = layout,
            darkTheme = true,
            factor = width / 1600f,
        )

        // 4. Outer Frame & Persian Architectural Title Block
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(224, 159, 62)
            strokeWidth = 3.5f
        }
        canvas.drawRect(margin, margin, width - margin, height - margin, framePaint)

        val footerTop = height - margin - footerHeight
        val footerBgPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.rgb(20, 28, 40)
        }
        canvas.drawRect(margin + 2f, footerTop, width - margin - 2f, height - margin - 2f, footerBgPaint)
        framePaint.color = Color.rgb(46, 58, 78)
        framePaint.strokeWidth = 2f
        canvas.drawLine(margin, footerTop, width - margin, footerTop, framePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val rightX = width - margin - 34f
        textPaint.color = Color.rgb(224, 159, 62)
        textPaint.textSize = 28f
        canvas.drawText("پروژه: ${title.ifBlank { "طرح کابینت" }}", rightX, footerTop + 52f, textPaint)

        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textPaint.color = Color.rgb(203, 213, 225)
        textPaint.textSize = 20f
        val client = document.clientNameFa.ifBlank { "کارفرمای محترم" }
        canvas.drawText("کارفرما: $client", rightX, footerTop + 96f, textPaint)

        textPaint.color = Color.rgb(148, 163, 184)
        textPaint.textSize = 18f
        canvas.drawText("تاریخ صدور نقشه: ${formatPersianDate(System.currentTimeMillis())}", rightX, footerTop + 138f, textPaint)

        val bom = BomCalculator.calculate(document.cabinets)
        val leftX = margin + 34f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textPaint.color = Color.rgb(56, 189, 248)
        textPaint.textSize = 24f
        canvas.drawText("CabinetPlan-FA | کابینت‌پلن فارسی", leftX, footerTop + 52f, textPaint)

        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textPaint.color = Color.rgb(226, 232, 240)
        textPaint.textSize = 19f
        val summaryLine = "زمینی: ${formatMeters(bom.baseRunningCm, false)} | دیواری: ${formatMeters(bom.wallRunningCm, false)} | ایستاده: ${formatMeters(bom.tallRunningCm, false)} | کل یونیت: ${bom.totalUnits.fa()}"
        canvas.drawText(summaryLine, leftX, footerTop + 96f, textPaint)

        textPaint.color = Color.rgb(16, 185, 129)
        textPaint.textSize = 17f
        canvas.drawText("واحد اندازه‌گیری: سانتی‌متر (خروجی مرجع کارگاهی)", leftX, footerTop + 138f, textPaint)
    }

    /**
     * Single-page A4 Landscape (842 x 595 pt) print-friendly vector technical sheet:
     * - Left/Center: Scaled 2D CAD Blueprint with walls, cabinet symbols, and Persian dimensions.
     * - Right Panel: Persian Title Block + Union Running-Meter Summary + BOM Unit Table.
     */
    private fun drawPrintableVectorPdf(
        canvas: Canvas,
        document: ProjectDocument,
        title: String,
        width: Float,
        height: Float,
    ) {
        val bounds = requireNotNull(document.bounds)
        canvas.drawColor(Color.WHITE)

        val margin = 22f
        val bomPanelWidth = 270f
        val headerHeight = 36f
        val footerHeight = 22f

        val layout = ExportLayout.fit(
            bounds = bounds,
            width = width,
            height = height,
            padding = margin + 16f,
            header = headerHeight,
            footer = footerHeight,
            rightPanelWidth = bomPanelWidth,
        )

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(30, 41, 59)
            strokeWidth = 1.4f
        }
        canvas.drawRect(margin, margin, width - margin, height - margin, borderPaint)

        val splitX = width - margin - bomPanelWidth
        canvas.drawLine(splitX, margin, splitX, height - margin, borderPaint)

        // Subtle grid inside plan area
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(235, 240, 245)
            strokeWidth = 0.6f
        }
        val step = (20f * layout.scale).coerceAtLeast(18f)
        var gx = margin + step
        while (gx < splitX) {
            canvas.drawLine(gx, margin + headerHeight, gx, height - margin - footerHeight, gridPaint)
            gx += step
        }
        var gy = margin + headerHeight + step
        while (gy < height - margin - footerHeight) {
            canvas.drawLine(margin, gy, splitX, gy, gridPaint)
            gy += step
        }

        // Plan header bar
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            color = Color.rgb(15, 23, 42)
            textSize = 12f
        }
        canvas.drawText(
            "پلان اجرایی کابینت: ${title.ifBlank { "طرح کابینت" }}",
            splitX - 10f,
            margin + 22f,
            textPaint,
        )
        canvas.drawLine(margin, margin + headerHeight, splitX, margin + headerHeight, borderPaint)

        // Draw CAD geometry inside left viewport
        canvas.save()
        canvas.clipRect(margin + 1f, margin + headerHeight + 1f, splitX - 1f, height - margin - footerHeight - 1f)
        drawPlanGeometry(
            canvas = canvas,
            document = document,
            layout = layout,
            darkTheme = false,
            factor = 0.55f,
        )
        canvas.restore()

        // Footer note under plan
        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textPaint.textSize = 8f
        textPaint.color = Color.rgb(71, 85, 105)
        canvas.drawText(
            "ابعاد به سانتی‌متر است؛ مقیاس متناسب با صفحه بوده و پیش از برش کارگاهی کنترل شود.",
            splitX - 10f,
            height - margin - 7f,
            textPaint,
        )

        // Right-hand Persian Title Block & Carpentry BOM Schedule
        val panelRight = width - margin - 10f
        var cursorY = margin + 20f
        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textPaint.textSize = 12.5f
        textPaint.color = Color.rgb(180, 83, 9)
        canvas.drawText("کابینت‌پلن فارسی | شناسنامه و متره", panelRight, cursorY, textPaint)

        cursorY += 17f
        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textPaint.textSize = 9.5f
        textPaint.color = Color.rgb(30, 41, 59)
        canvas.drawText("کارفرما: ${document.clientNameFa.ifBlank { "کارفرمای محترم" }}", panelRight, cursorY, textPaint)

        cursorY += 14f
        textPaint.textSize = 8.5f
        textPaint.color = Color.rgb(100, 116, 139)
        canvas.drawText("تاریخ: ${formatPersianDate(System.currentTimeMillis())}", panelRight, cursorY, textPaint)

        cursorY += 10f
        canvas.drawLine(splitX, cursorY, width - margin, cursorY, borderPaint)

        val bom = BomCalculator.calculate(document.cabinets)
        cursorY += 15f
        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textPaint.textSize = 9.5f
        textPaint.color = Color.rgb(15, 118, 110)
        canvas.drawText(
            "زمینی: ${formatMeters(bom.baseRunningCm, false)}  |  دیواری: ${formatMeters(bom.wallRunningCm, false)}",
            panelRight,
            cursorY,
            textPaint,
        )
        cursorY += 14f
        canvas.drawText(
            "ایستاده: ${formatMeters(bom.tallRunningCm, false)}  |  تعداد یونیت: ${bom.totalUnits.fa()}",
            panelRight,
            cursorY,
            textPaint,
        )

        cursorY += 10f
        canvas.drawLine(splitX, cursorY, width - margin, cursorY, borderPaint)

        // BOM Unit Table Header
        cursorY += 15f
        textPaint.color = Color.rgb(15, 23, 42)
        textPaint.textSize = 8.5f
        canvas.drawText("ردیف و شرح یونیت", panelRight, cursorY, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("کد  |  عرض×عمق×ارتفاع", splitX + 8f, cursorY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT

        cursorY += 6f
        canvas.drawLine(splitX, cursorY, width - margin, cursorY, borderPaint)

        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textPaint.textSize = 8f
        val maxRows = 22
        document.cabinets.take(maxRows).forEachIndexed { idx, cab ->
            cursorY += 16f
            if (cursorY < height - margin - 14f) {
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.color = Color.rgb(30, 41, 59)
                val rowTitle = "${(idx + 1).fa()}. ${cab.nameFa.take(24)}"
                canvas.drawText(rowTitle, panelRight, cursorY, textPaint)

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = Color.rgb(71, 85, 105)
                val dims = "${cab.codeFa} | ${formatCm(cab.width, false)}×${formatCm(cab.depth, false)}×${formatCm(cab.height, false)}"
                canvas.drawText(dims, splitX + 8f, cursorY, textPaint)
            }
        }
        if (document.cabinets.size > maxRows) {
            cursorY += 14f
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = Color.rgb(100, 116, 139)
            canvas.drawText("+ ${(document.cabinets.size - maxRows).fa()} یونیت دیگر در پروژه", panelRight, cursorY, textPaint)
        }
    }

    private fun drawPlanGeometry(
        canvas: Canvas,
        document: ProjectDocument,
        layout: ExportLayout,
        darkTheme: Boolean,
        factor: Float,
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val dashEffect = DashPathEffect(floatArrayOf(8f * factor, 5f * factor), 0f)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        // 1. Cabinets sorted by elevation (base -> tall/island -> wall)
        for (cab in document.cabinets.sortedBy { it.elevationCm }) {
            val tl = layout.map(Point(cab.x, cab.y))
            val br = layout.map(Point(cab.x + cab.width, cab.y + cab.depth))
            val rect = RectF(tl.x, tl.y, br.x, br.y)

            fillPaint.color = if (darkTheme) {
                cab.fillArgb.toInt()
            } else {
                when (cab.category) {
                    CabinetCategory.WALL -> Color.argb(45, 14, 165, 233)
                    CabinetCategory.TALL -> Color.argb(45, 147, 51, 234)
                    CabinetCategory.ISLAND -> Color.argb(45, 16, 185, 129)
                    CabinetCategory.FIXTURE -> Color.argb(45, 59, 130, 246)
                    CabinetCategory.BASE -> Color.argb(50, 217, 119, 6)
                }
            }
            strokePaint.color = if (darkTheme) {
                cab.strokeArgb.toInt()
            } else {
                when (cab.category) {
                    CabinetCategory.WALL -> Color.rgb(2, 132, 199)
                    CabinetCategory.TALL -> Color.rgb(126, 34, 206)
                    CabinetCategory.ISLAND -> Color.rgb(5, 150, 105)
                    CabinetCategory.FIXTURE -> Color.rgb(37, 99, 235)
                    CabinetCategory.BASE -> Color.rgb(180, 83, 9)
                }
            }
            strokePaint.strokeWidth = max(1.4f, 2.2f * factor)
            strokePaint.pathEffect = if (cab.category == CabinetCategory.WALL) dashEffect else null

            canvas.drawRect(rect, fillPaint)
            canvas.drawRect(rect, strokePaint)
            strokePaint.pathEffect = null

            // Architectural symbol details
            drawCabinetSymbolDetails(canvas, cab, rect, strokePaint, factor)

            // Label & Dimensions
            if (rect.width() > 36f * factor && rect.height() > 22f * factor) {
                labelPaint.color = if (darkTheme) Color.rgb(248, 250, 252) else Color.rgb(15, 23, 42)
                labelPaint.textSize = min(14f * factor, max(8f * factor, rect.width() * 0.13f))
                canvas.drawText(cab.nameFa, rect.centerX(), rect.centerY() - 3f * factor, labelPaint)

                labelPaint.color = if (darkTheme) Color.rgb(251, 191, 36) else Color.rgb(71, 85, 105)
                labelPaint.textSize = min(11.5f * factor, max(7.5f * factor, rect.width() * 0.11f))
                canvas.drawText(
                    "${formatCm(cab.width, false)}×${formatCm(cab.depth, false)} سم",
                    rect.centerX(),
                    rect.centerY() + 12f * factor,
                    labelPaint,
                )
            }
        }

        // 2. Structural Walls & Dimension Callout Badges
        for (line in document.lines) {
            val p1 = layout.map(line.start)
            val p2 = layout.map(line.end)

            strokePaint.strokeCap = Paint.Cap.SQUARE
            strokePaint.color = if (darkTheme) Color.rgb(226, 232, 240) else Color.rgb(30, 41, 59)
            strokePaint.strokeWidth = max(4f * factor, line.thickness * layout.scale * 0.65f)
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, strokePaint)

            strokePaint.color = if (darkTheme) Color.rgb(71, 85, 105) else Color.rgb(148, 163, 184)
            strokePaint.strokeWidth = max(1f, 1.8f * factor)
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, strokePaint)

            if (distance(p1, p2) > 42f * factor) {
                val mx = (p1.x + p2.x) / 2f
                val my = (p1.y + p2.y) / 2f - 16f * factor
                val dimText = formatCm(line.lengthCm)
                val badgeW = 76f * factor
                val badgeH = 22f * factor
                val badgeRect = RectF(mx - badgeW / 2f, my - badgeH / 2f, mx + badgeW / 2f, my + badgeH / 2f)

                fillPaint.color = if (darkTheme) Color.rgb(30, 41, 59) else Color.rgb(241, 245, 249)
                strokePaint.color = if (darkTheme) Color.rgb(56, 189, 248) else Color.rgb(2, 132, 199)
                strokePaint.strokeWidth = max(1f, 1.3f * factor)
                canvas.drawRoundRect(badgeRect, 6f * factor, 6f * factor, fillPaint)
                canvas.drawRoundRect(badgeRect, 6f * factor, 6f * factor, strokePaint)

                labelPaint.color = if (darkTheme) Color.rgb(56, 189, 248) else Color.rgb(3, 105, 161)
                labelPaint.textSize = 11.5f * factor
                canvas.drawText(dimText, mx, my + 4f * factor, labelPaint)
            }
        }
    }

    private fun drawCabinetSymbolDetails(
        canvas: Canvas,
        cab: CabinetObject,
        rect: RectF,
        strokePaint: Paint,
        factor: Float,
    ) {
        val w = rect.width()
        val h = rect.height()
        strokePaint.strokeWidth = max(0.9f, 1.3f * factor)
        when (cab.symbolType) {
            CabinetSymbolType.SINK_DOUBLE -> {
                val pad = min(w, h) * 0.12f
                val b1 = RectF(rect.left + pad, rect.top + pad, rect.left + w * 0.44f, rect.bottom - pad)
                val b2 = RectF(rect.left + w * 0.50f, rect.top + pad, rect.left + w * 0.82f, rect.bottom - pad)
                canvas.drawRoundRect(b1, 4f * factor, 4f * factor, strokePaint)
                canvas.drawRoundRect(b2, 4f * factor, 4f * factor, strokePaint)
            }
            CabinetSymbolType.GAS_HOB -> {
                val r = min(w, h) * 0.10f
                canvas.drawCircle(rect.left + w * 0.25f, rect.top + h * 0.30f, r, strokePaint)
                canvas.drawCircle(rect.left + w * 0.25f, rect.bottom - h * 0.30f, r, strokePaint)
                canvas.drawCircle(rect.right - w * 0.25f, rect.top + h * 0.30f, r, strokePaint)
                canvas.drawCircle(rect.right - w * 0.25f, rect.bottom - h * 0.30f, r, strokePaint)
                canvas.drawCircle(rect.centerX(), rect.centerY(), r * 1.35f, strokePaint)
            }
            CabinetSymbolType.DOOR_SWING -> {
                val swing = w * 0.8f
                val path = Path().apply {
                    moveTo(rect.left, rect.bottom)
                    lineTo(rect.left, rect.bottom + swing)
                    val arcRect = RectF(rect.left - swing, rect.bottom - swing, rect.left + swing, rect.bottom + swing)
                    arcTo(arcRect, 90f, -90f)
                }
                canvas.drawPath(path, strokePaint)
            }
            CabinetSymbolType.WINDOW_DOUBLE -> {
                canvas.drawLine(rect.left, rect.top + h * 0.35f, rect.right, rect.top + h * 0.35f, strokePaint)
                canvas.drawLine(rect.left, rect.top + h * 0.65f, rect.right, rect.top + h * 0.65f, strokePaint)
            }
            else -> Unit
        }
    }

    companion object {
        const val PNG_WIDTH = 2048
        const val PNG_HEIGHT = 1472
        const val PDF_WIDTH = 842
        const val PDF_HEIGHT = 595
    }
}
