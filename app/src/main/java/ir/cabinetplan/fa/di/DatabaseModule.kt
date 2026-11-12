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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ProjectDatabase =
        Room.databaseBuilder(context, ProjectDatabase::class.java, "cabinetplan.db").build()

    @Provides
    fun projectDao(database: ProjectDatabase): ProjectDao = database.projectDao()
}
