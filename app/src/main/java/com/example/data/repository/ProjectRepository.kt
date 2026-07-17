package com.example.data.repository

import com.example.data.db.ProjectDao
import com.example.domain.model.Project
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {
    val allProjects: Flow<List<Project>> = projectDao.getAllProjects()

    suspend fun insertProject(project: Project): Long {
        return projectDao.insertProject(project)
    }

    suspend fun deleteProject(project: Project) {
        projectDao.deleteProject(project)
    }

    suspend fun deleteProjectById(id: Long) {
        projectDao.deleteProjectById(id)
    }

    suspend fun getProjectById(id: Long): Project? {
        return projectDao.getProjectById(id)
    }
}
