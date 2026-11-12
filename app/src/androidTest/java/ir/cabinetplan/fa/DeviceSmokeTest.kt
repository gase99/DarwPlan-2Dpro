package ir.cabinetplan.fa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.cabinetplan.fa.domain.PRESET_PROJECTS
import ir.cabinetplan.fa.export.AndroidPlanExporter
import ir.cabinetplan.fa.export.ExportFormat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android device/emulator test verifying native PdfDocument + PdfRenderer output. */
@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {

    @Test
    fun vectorPdfSheetIsReadableAndRendersBlueprintAndBomOnAndroid() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "native-sheet-test.pdf")
        val document = PRESET_PROJECTS.first().document
        try {
            AndroidPlanExporter(context).write(
                uri = Uri.fromFile(file),
                document = document,
                title = "آشپزخانه آزمایشی سعادت‌آباد",
                format = ExportFormat.PDF,
            )
            assertTrue(file.length() > 200)
            assertEquals("%PDF", file.inputStream().use { input ->
                String(ByteArray(4).also { check(input.read(it) == 4) }, Charsets.US_ASCII)
            })
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { pdf ->
                    assertEquals(1, pdf.pageCount)
                    pdf.openPage(0).use { page ->
                        assertEquals(AndroidPlanExporter.PDF_WIDTH, page.width)
                        assertEquals(AndroidPlanExporter.PDF_HEIGHT, page.height)
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            assertTrue(
                                "PDF must contain visible CAD blueprint and BOM drawing, not just a white page",
                                pixels.any { it != Color.WHITE }
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
    }
}
