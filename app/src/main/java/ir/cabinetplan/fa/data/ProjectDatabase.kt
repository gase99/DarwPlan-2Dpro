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

// Documents use a versioned JSON payload; Room manages project metadata and transactions.
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

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun find(id: String): ProjectEntity?

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)
}

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = true)
abstract class ProjectDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
