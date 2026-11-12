package ir.cabinetplan.fa.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.cabinetplan.fa.data.ProjectDao
import ir.cabinetplan.fa.data.ProjectDatabase
import javax.inject.Singleton
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ir.cabinetplan.fa.data.ProjectStore
import ir.cabinetplan.fa.data.RoomProjectStore
import ir.cabinetplan.fa.export.PlanExport
import ir.cabinetplan.fa.export.AndroidPlanExporter

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DocumentDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @DocumentDispatcher
    fun documentDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun store(store: RoomProjectStore): ProjectStore = store

    @Provides
    @Singleton
    fun exporter(exporter: AndroidPlanExporter): PlanExport = exporter

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ProjectDatabase =
        Room.databaseBuilder(context, ProjectDatabase::class.java, "cabinetplan.db").build()

    @Provides
    fun projectDao(database: ProjectDatabase): ProjectDao = database.projectDao()
}
