package com.example

import com.example.data.db.ProjectDao
import com.example.data.repository.ProjectRepository
import com.example.domain.model.Project
import com.example.domain.model.ProjectMode
import com.example.ui.ProjectViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HistoryUndoRedoTest {

    private lateinit var viewModel: ProjectViewModel

    private val fakeDao = object : ProjectDao {
        override fun getAllProjects(): Flow<List<Project>> = flowOf(emptyList())
        override suspend fun insertProject(project: Project): Long = 1L
        override suspend fun deleteProject(project: Project) {}
        override suspend fun deleteProjectById(id: Long) {}
        override suspend fun getProjectById(id: Long): Project? = null
    }

    @Before
    fun setUp() {
        val repository = ProjectRepository(fakeDao)
        viewModel = ProjectViewModel(repository)
        viewModel.startNewProject() // sets initial state S0
    }

    @Test
    fun testInitialStateCannotUndoOrRedo() {
        assertFalse(viewModel.canUndo)
        assertFalse(viewModel.canRedo)
    }

    @Test
    fun testPushingNewStateEnablesUndo() {
        viewModel.widthCm = "4.0"
        viewModel.pushHistoryState()

        assertTrue(viewModel.canUndo)
        assertFalse(viewModel.canRedo)
        assertEquals("4.0", viewModel.widthCm)
    }

    @Test
    fun testConsecutiveDuplicatePushesDoNotDoublePush() {
        viewModel.widthCm = "4.0"
        viewModel.pushHistoryState()
        viewModel.pushHistoryState() // consecutive duplicate push

        viewModel.undo() // undo S1 to S0
        assertEquals("3.5", viewModel.widthCm) // should be back to initial state
        assertFalse(viewModel.canUndo) // should not have any more undo states
    }

    @Test
    fun testUndoAndRedoLifecycle() {
        // Step 1: Change quantity and margin
        viewModel.quantity = "20"
        viewModel.marginCm = "1.0"
        viewModel.pushHistoryState() // State S1

        // Step 2: Change resolution and cutting guides
        viewModel.dpi = 600
        viewModel.cuttingGuidesEnabled = false
        viewModel.pushHistoryState() // State S2

        assertEquals("20", viewModel.quantity)
        assertEquals("1.0", viewModel.marginCm)
        assertEquals(600, viewModel.dpi)
        assertFalse(viewModel.cuttingGuidesEnabled)

        // Step 3: Undo once (back to S1)
        assertTrue(viewModel.canUndo)
        viewModel.undo()

        assertEquals("20", viewModel.quantity)
        assertEquals("1.0", viewModel.marginCm)
        assertEquals(300, viewModel.dpi) // restored to S1 (300 DPI)
        assertTrue(viewModel.cuttingGuidesEnabled) // restored to S1 (true)
        assertTrue(viewModel.canUndo)
        assertTrue(viewModel.canRedo)

        // Step 4: Undo again (back to S0)
        viewModel.undo()

        assertEquals("16", viewModel.quantity) // restored to S0 (16 copies)
        assertEquals("0.5", viewModel.marginCm) // restored to S0 (0.5 cm)
        assertFalse(viewModel.canUndo) // reached initial state
        assertTrue(viewModel.canRedo)

        // Step 5: Redo (back to S1)
        viewModel.redo()

        assertEquals("20", viewModel.quantity)
        assertEquals("1.0", viewModel.marginCm)
        assertTrue(viewModel.canUndo)
        assertTrue(viewModel.canRedo)

        // Step 6: Redo again (back to S2)
        viewModel.redo()

        assertEquals(600, viewModel.dpi)
        assertFalse(viewModel.cuttingGuidesEnabled)
        assertTrue(viewModel.canUndo)
        assertFalse(viewModel.canRedo) // reached newest state
    }

    @Test
    fun testNewEditClearsRedoStack() {
        viewModel.quantity = "30"
        viewModel.pushHistoryState() // S1

        viewModel.undo() // back to S0
        assertTrue(viewModel.canRedo)

        // Make a brand new edit while in undo state S0
        viewModel.spacingCm = "0.8"
        viewModel.pushHistoryState() // New branch S1'

        // Redo stack should be cleared since we made a new action
        assertFalse(viewModel.canRedo)
    }
}
