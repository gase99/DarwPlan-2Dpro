package ir.cabinetplan.fa.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// Schema foundation only. Save/load UI and document serialization belong to phase 4.
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val documentJson: String,
    val updatedAt: Long,
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)
}

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = true)
abstract class ProjectDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
