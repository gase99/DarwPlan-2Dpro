package ir.cabinetplan.fa.data

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface ProjectStore {
    val projects: Flow<List<ProjectEntity>>
    suspend fun save(project: ProjectEntity)
    suspend fun load(id: String): ProjectEntity?
    suspend fun delete(id: String)
}

class RoomProjectStore @Inject constructor(private val dao: ProjectDao) : ProjectStore {
    override val projects = dao.observeProjects()
    override suspend fun save(project: ProjectEntity) = dao.upsert(project)
    override suspend fun load(id: String) = dao.find(id)
    override suspend fun delete(id: String) = dao.delete(id)
}
