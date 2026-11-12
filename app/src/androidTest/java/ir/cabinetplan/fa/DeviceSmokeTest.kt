package ir.cabinetplan.fa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.cabinetplan.fa.domain.*
import ir.cabinetplan.fa.export.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android test: Robolectric does not implement PdfDocument's native writer. */
@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    @Test fun vectorPdfIsReadableAndRendersOnAndroid() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "native-test.pdf")
        val document = PlanDocument(CabinetKind.BASE.create(Point(-300f, 200f)) +
            PlanLine(start = Point(-400f, 0f), end = Point(800f, 0f)))
        try {
            AndroidPlanExporter(context).write(Uri.fromFile(file), document, "آشپزخانهٔ آزمایش", ExportFormat.PDF)
            assertTrue(file.length() > 100)
            assertEquals("%PDF", file.inputStream().use { input ->
                String(ByteArray(4).also { check(input.read(it) == 4) }, Charsets.US_ASCII)
            })
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { pdf ->
                    assertEquals(1, pdf.pageCount)
                    pdf.openPage(0).use { page ->
                        assertEquals(842, page.width); assertEquals(595, page.height)
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            assertTrue("PDF must contain visible drawing, not just a white page", pixels.any { it != android.graphics.Color.WHITE })
                        } finally { bitmap.recycle() }
                    }
                }
            }
        } finally { file.delete() }
    }
}
