package ir.cabinetplan.fa.integration

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.cabinetplan.fa.data.*
import ir.cabinetplan.fa.domain.*
import ir.cabinetplan.fa.export.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StorageExportTest {
    @Test fun `Room survives reopening and preserves Persian project names`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, ProjectDatabase::class.java, name).build()
        val document = PlanDocument(CabinetKind.BASE.create(Point(-50f, 200f)))
        val entity = ProjectEntity("test", "آشپزخانهٔ ایرانی", DocumentCodec.encode(document), 42)
        val first = open()
        try { RoomProjectStore(first.projectDao()).save(entity) } finally { first.close() }
        val second = open()
        try {
            val store = RoomProjectStore(second.projectDao())
            assertEquals(entity, store.load(entity.id))
            assertEquals(document, DocumentCodec.decode(store.load(entity.id)!!.documentJson))
            assertEquals(1, store.projects.first().size)
            store.delete(entity.id)
            assertNull(store.load(entity.id))
        } finally { second.close(); context.deleteDatabase(name) }
    }

    @Test fun `PNG and PDF contain real encoded output`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exporter = AndroidPlanExporter(context)
        val document = PlanDocument(CabinetKind.DRAWER.create(Point(-400f, 100f)))
        val png = File(context.cacheDir, "test.png")
        val pdf = File(context.cacheDir, "test.pdf")
        try {
            exporter.write(Uri.fromFile(png), document, "طرح آزمایشی", ExportFormat.PNG)
            val image = BitmapFactory.decodeFile(png.path)
            assertNotNull(image); assertEquals(2048, image.width); assertEquals(2048, image.height)
            image.recycle()
            exporter.write(Uri.fromFile(pdf), document, "طرح آزمایشی", ExportFormat.PDF)
            assertTrue(pdf.length() > 100)
            assertEquals("%PDF", pdf.inputStream().use { String(ByteArray(4).also { bytes -> check(it.read(bytes) == 4) }, Charsets.US_ASCII) })
        } finally { png.delete(); pdf.delete() }
    }
}
