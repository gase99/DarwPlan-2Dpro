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

    @Test
    fun `Room survives reopening and preserves Persian kitchen project and metadata`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test-${UUID.randomUUID()}.db"
        fun openDb() = Room.databaseBuilder(context, ProjectDatabase::class.java, dbName).build()

        val document = PRESET_PROJECTS.first().document
        val entity = ProjectEntity("proj_1", "آشپزخانه مدرن ال", DocumentCodec.encode(document), 100L)

        val first = openDb()
        try {
            RoomProjectStore(first.projectDao()).save(entity)
        } finally {
            first.close()
        }

        val second = openDb()
        try {
            val store = RoomProjectStore(second.projectDao())
            val loaded = requireNotNull(store.load("proj_1"))
            assertEquals(entity, loaded)
            assertEquals(document, DocumentCodec.decode(loaded.documentJson))
            assertEquals(1, store.projects.first().size)
            store.delete("proj_1")
            assertNull(store.load("proj_1"))
        } finally {
            second.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `PNG export writes high resolution architectural blueprint image`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exporter = AndroidPlanExporter(context)
        val document = PRESET_PROJECTS.first().document
        val png = File(context.cacheDir, "blueprint-test.png")
        try {
            exporter.write(Uri.fromFile(png), document, "آشپزخانه سعادت‌آباد", ExportFormat.PNG)
            val bitmap = requireNotNull(BitmapFactory.decodeFile(png.path))
            try {
                assertEquals(AndroidPlanExporter.PNG_WIDTH, bitmap.width)
                assertEquals(AndroidPlanExporter.PNG_HEIGHT, bitmap.height)
            } finally {
                bitmap.recycle()
            }
        } finally {
            png.delete()
        }
    }
}
