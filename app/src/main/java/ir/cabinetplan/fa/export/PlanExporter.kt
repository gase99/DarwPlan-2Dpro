package ir.cabinetplan.fa.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.cabinetplan.fa.R
import ir.cabinetplan.fa.domain.*
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class ExportFormat { PNG, PDF }
interface PlanExport {
    suspend fun write(uri: Uri, document: PlanDocument, title: String, format: ExportFormat)
}
class AndroidPlanExporter @Inject constructor(@ApplicationContext private val context: Context) : PlanExport {
    override suspend fun write(uri: Uri, document: PlanDocument, title: String, format: ExportFormat) = withContext(Dispatchers.IO) {
        require(document.valid() && document.lines.isNotEmpty())
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { stream ->
            when (format) {
                ExportFormat.PNG -> {
                    val bitmap = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888)
                    try {
                        draw(Canvas(bitmap), document, title, 2048, 2048)
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    } finally { bitmap.recycle() }
                }
                ExportFormat.PDF -> {
                    val pdf = PdfDocument()
                    try {
                        val page = pdf.startPage(PdfDocument.PageInfo.Builder(842, 595, 1).create())
                        draw(page.canvas, document, title, 842, 595)
                        pdf.finishPage(page)
                        pdf.writeTo(stream)
                    } finally { pdf.close() }
                }
            }
        }
    }

    private fun draw(canvas: Canvas, document: PlanDocument, title: String, width: Int, height: Int) {
        val factor = width / 842f
        val padding = 32f * factor
        val header = 48f * factor
        val layout = ExportLayout.fit(requireNotNull(document.bounds), width.toFloat(), height.toFloat(), padding, header)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(23, 107, 87); textSize = 18f * factor; textAlign = Paint.Align.RIGHT
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        canvas.drawText(title, width - padding, padding, paint)
        paint.textSize = 9f * factor
        paint.color = Color.DKGRAY
        canvas.drawText(context.getString(R.string.export_note), width - padding, height - 10f * factor, paint)
        paint.strokeWidth = 1.4f * factor
        paint.textAlign = Paint.Align.CENTER
        document.lines.forEach { line ->
            val a = layout.map(line.start); val b = layout.map(line.end)
            paint.color = Color.rgb(23, 107, 87)
            canvas.drawLine(a.x, a.y, b.x, b.y, paint)
            if (line.group == null && distance(a, b) > 45f * factor) {
                paint.color = Color.DKGRAY
                canvas.drawText(context.getString(R.string.length_mm, number(line.length)), (a.x + b.x) / 2,
                    (a.y + b.y) / 2 - 5f * factor, paint)
            }
        }
        document.lines.filter { it.group != null }.groupBy { it.group }.values.forEach { lines ->
            val bounds = requireNotNull(PlanDocument(lines).bounds)
            val center = layout.map(bounds.center)
            val label = when (lines.first().kind) {
                CabinetKind.BASE -> R.string.cabinet_base
                CabinetKind.WALL -> R.string.cabinet_wall
                CabinetKind.DRAWER -> R.string.cabinet_drawer
                CabinetKind.DOOR -> R.string.cabinet_door
                CabinetKind.SHELF -> R.string.cabinet_shelf
                null -> R.string.app_name
            }
            paint.color = Color.DKGRAY
            canvas.drawText(context.getString(label), center.x, center.y, paint)
            canvas.drawText(context.getString(R.string.object_dimensions, number(bounds.width), number(bounds.height)),
                center.x, center.y + 13f * factor, paint)
        }
    }
    private fun number(value: Float): String = String.format(Locale.forLanguageTag("fa"), "%d", value.roundToInt())
}
