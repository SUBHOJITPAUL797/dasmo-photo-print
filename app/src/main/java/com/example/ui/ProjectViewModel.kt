package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ProjectRepository
import com.example.domain.layout.LayoutEngine
import com.example.domain.layout.LayoutException
import com.example.domain.model.*
import com.example.domain.pdf.PdfExporter
import com.example.util.BitmapUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ProjectViewModel(private val repository: ProjectRepository) : ViewModel() {

    // Active wizard navigation steps:
    // 0 = History List (Home)
    // 1 = Mode Select (Single vs Joint)
    // 2 = Size & Quantity (and settings)
    // 3 = Crop Screen for Photo A
    // 4 = Crop Screen for Photo B (Joint mode only)
    // 5 = Joint Balance Screen (Joint mode only)
    // 6 = Print Multi-Page Preview
    // 7 = Export Screen (Save, Share, Print)
    var currentStep by mutableStateOf(0)
    
    // Approval status for restrictions (pending accounts cannot print or upload to Drive)
    var isApproved by mutableStateOf(true)
    
    // Expiration status and duration for DASMO photo print access
    var isExpired by mutableStateOf(false)
    var expiryTimeMs by mutableStateOf(0L)

    // Configuration states
    var mode by mutableStateOf(ProjectMode.SINGLE)
    var widthCm by mutableStateOf("3.5")
    var heightCm by mutableStateOf("4.5")
    var quantity by mutableStateOf("16")

    // Multi-person split quantities
    var quantityA by mutableStateOf(8)
    var quantityB by mutableStateOf(8)

    fun syncMultiPersonQuantities() {
        if (mode == ProjectMode.MULTI_PERSON) {
            val total = quantity.toIntOrNull() ?: 16
            val qA = Math.round(total * jointSplitRatio).coerceIn(1, maxOf(1, total - 1))
            quantityA = qA
            quantityB = maxOf(0, total - qA)
        }
    }

    fun updateQuantityA(newVal: Int) {
        val total = quantity.toIntOrNull() ?: 16
        val safeA = newVal.coerceIn(1, maxOf(1, total - 1))
        quantityA = safeA
        quantityB = maxOf(0, total - safeA)
        jointSplitRatio = safeA.toFloat() / total.toFloat()
        pushHistoryState()
        computeCurrentLayout()
    }

    // Advanced margins and spacing
    var marginCm by mutableStateOf("0.5")
    var spacingCm by mutableStateOf("0.2")
    var dpi by mutableStateOf(300)
    var cuttingGuidesEnabled by mutableStateOf(true)
    var cuttingGuideThicknessPt by mutableStateOf(1.0f)
    var cuttingGuideStyle by mutableStateOf("dashed")
    var cuttingGuideColor by mutableStateOf(0xFF000000.toInt())
    var allowRotation by mutableStateOf(true)
    var pageOrientation by mutableStateOf(PageOrientation.PORTRAIT)
    var idCardArrangement by mutableStateOf("HORIZONTAL") // "HORIZONTAL" or "VERTICAL"

    // Printer Alignment Calibration Offsets (mm)
    var topOffsetMm by mutableStateOf("0.0")
    var leftOffsetMm by mutableStateOf("0.0")

    // Cyber Cafe Customer Billing Fields
    var customerName by mutableStateOf("")
    var customerPhone by mutableStateOf("")
    var ratePerSheet by mutableStateOf("20.0")
    var extraServicesFee by mutableStateOf("0.0")

    // Batch Paper Saver items
    val batchItems = mutableStateListOf<com.example.domain.model.BatchItem>()

    fun addBatchItem(label: String, widthCm: Float, heightCm: Float, quantity: Int) {
        batchItems.add(com.example.domain.model.BatchItem(label = label, widthCm = widthCm, heightCm = heightCm, quantity = quantity))
        computeCurrentLayout()
    }

    fun removeBatchItem(id: String) {
        batchItems.removeAll { it.id == id }
        computeCurrentLayout()
    }

    // Photo Assets State
    var photoAUri by mutableStateOf<Uri?>(null)
    var photoBUri by mutableStateOf<Uri?>(null)

    var cropABitmap by mutableStateOf<Bitmap?>(null)
    var cropBBitmap by mutableStateOf<Bitmap?>(null)

    // Combined or final single printable bitmap
    var finalUnitBitmap by mutableStateOf<Bitmap?>(null)

    // Joint Mode customization values
    var jointSplitRatio by mutableStateOf(0.5f)
    var jointDividerLinesEnabled by mutableStateOf(false)
    var jointDividerColor by mutableStateOf(0xFF000000.toInt())

    // Layout engine output
    var isLayoutTooLargeError by mutableStateOf(false)
    var layoutEngineErrorText by mutableStateOf("")
    var computedPages by mutableStateOf<List<PageLayout>>(emptyList())

    // Final exported details
    var filename by mutableStateOf("")
    var generatedPdfUri by mutableStateOf<Uri?>(null)
    var isSavingPdf by mutableStateOf(false)
    var saveSuccessMessage by mutableStateOf<String?>(null)

    // Selected Historic Project for re-export
    var reExportProject by mutableStateOf<Project?>(null)

    // Recent projects reactive flow
    val recentProjects: StateFlow<List<Project>> = repository.allProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- Session State History / Undo-Redo Stack (INDUSTRY GRADE) ---
    private val undoStack = mutableListOf<HistoryState>()
    private val redoStack = mutableListOf<HistoryState>()

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private var historyJob: kotlinx.coroutines.Job? = null

    private fun captureCurrentState(): HistoryState {
        return HistoryState(
            mode = mode,
            widthCm = widthCm,
            heightCm = heightCm,
            quantity = quantity,
            marginCm = marginCm,
            spacingCm = spacingCm,
            dpi = dpi,
            cuttingGuidesEnabled = cuttingGuidesEnabled,
            cuttingGuideThicknessPt = cuttingGuideThicknessPt,
            cuttingGuideStyle = cuttingGuideStyle,
            cuttingGuideColor = cuttingGuideColor,
            allowRotation = allowRotation,
            jointSplitRatio = jointSplitRatio,
            jointDividerLinesEnabled = jointDividerLinesEnabled,
            jointDividerColor = jointDividerColor,
            pageOrientation = pageOrientation,
            idCardArrangement = idCardArrangement
        )
    }

    fun initHistory() {
        historyJob?.cancel()
        undoStack.clear()
        redoStack.clear()
        val initialState = captureCurrentState()
        undoStack.add(initialState)
        canUndo = false
        canRedo = false
    }

    fun pushHistoryState() {
        historyJob?.cancel()
        val currentState = captureCurrentState()
        // Prevent pushing duplicate consecutive states
        if (undoStack.isNotEmpty() && undoStack.last() == currentState) {
            return
        }
        undoStack.add(currentState)
        redoStack.clear()
        canUndo = undoStack.size > 1
        canRedo = false
    }

    fun pushHistoryStateDebounced() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            kotlinx.coroutines.delay(800) // 800ms quiet period before saving state
            pushHistoryState()
        }
    }

    fun undo() {
        if (undoStack.size > 1) {
            val current = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(current)
            val previous = undoStack.last()
            restoreState(previous)
            canUndo = undoStack.size > 1
            canRedo = redoStack.isNotEmpty()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val target = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(target)
            restoreState(target)
            canUndo = undoStack.size > 1
            canRedo = redoStack.isNotEmpty()
        }
    }

    private fun restoreState(state: HistoryState) {
        mode = state.mode
        widthCm = state.widthCm
        heightCm = state.heightCm
        quantity = state.quantity
        marginCm = state.marginCm
        spacingCm = state.spacingCm
        dpi = state.dpi
        cuttingGuidesEnabled = state.cuttingGuidesEnabled
        cuttingGuideThicknessPt = state.cuttingGuideThicknessPt
        cuttingGuideStyle = state.cuttingGuideStyle
        cuttingGuideColor = state.cuttingGuideColor
        allowRotation = state.allowRotation
        jointSplitRatio = state.jointSplitRatio
        jointDividerLinesEnabled = state.jointDividerLinesEnabled
        jointDividerColor = state.jointDividerColor
        pageOrientation = state.pageOrientation
        idCardArrangement = state.idCardArrangement

        // Re-generate joint template composite if necessary
        if ((mode == ProjectMode.JOINT || mode == ProjectMode.ID_CARD) && cropABitmap != null && cropBBitmap != null) {
            generateJointComposite()
        }

        if (mode == ProjectMode.MULTI_PERSON) {
            syncMultiPersonQuantities()
        }

        // Recompute the grid boundaries and print density layout calculations
        computeCurrentLayout()
    }

    fun startNewProject() {
        mode = ProjectMode.SINGLE
        widthCm = "3.5"
        heightCm = "4.5"
        quantity = "16"
        quantityA = 8
        quantityB = 8
        marginCm = "0.5"
        spacingCm = "0.2"
        dpi = 300
        cuttingGuidesEnabled = true
        cuttingGuideThicknessPt = 1.0f
        cuttingGuideStyle = "dashed"
        cuttingGuideColor = 0xFF000000.toInt()
        allowRotation = true
        pageOrientation = PageOrientation.PORTRAIT

        photoAUri = null
        photoBUri = null
        cropABitmap = null
        cropBBitmap = null
        finalUnitBitmap = null

        jointSplitRatio = 0.5f
        jointDividerLinesEnabled = false
        jointDividerColor = 0xFF000000.toInt()

        isLayoutTooLargeError = false
        layoutEngineErrorText = ""
        computedPages = emptyList()
        generatedPdfUri = null
        filename = "PassportPhotos_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        saveSuccessMessage = null
        idCardArrangement = "HORIZONTAL"

        initHistory()
        currentStep = 1
    }

    fun selectPreset(presetName: String, presetWidth: Float, presetHeight: Float) {
        widthCm = presetWidth.toString()
        heightCm = presetHeight.toString()
        if (!presetName.startsWith("Custom")) {
            val safeName = presetName.replace(Regex("[^a-zA-Z0-9]"), "")
            filename = safeName + "_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        }
    }

    fun getUnitSize(): UnitSizeCm {
        val wVal = widthCm.toFloatOrNull() ?: 3.5f
        val hVal = heightCm.toFloatOrNull() ?: 4.5f
        val s = spacingCm.toFloatOrNull() ?: 0.2f

        return if (mode == ProjectMode.ID_CARD) {
            if (idCardArrangement == "HORIZONTAL") {
                UnitSizeCm(wVal * 2f + s, hVal)
            } else {
                UnitSizeCm(wVal, hVal * 2f + s)
            }
        } else {
            UnitSizeCm(wVal, hVal)
        }
    }

    fun computeCurrentLayout(): Boolean {
        isLayoutTooLargeError = false
        layoutEngineErrorText = ""

        if (mode == ProjectMode.MULTI_PERSON) {
            syncMultiPersonQuantities()
        }

        val q = quantity.toIntOrNull() ?: 16
        val m = marginCm.toFloatOrNull() ?: 0.5f
        val s = spacingCm.toFloatOrNull() ?: 0.2f

        val finalAllowRotation = if (mode == ProjectMode.ID_CARD) false else allowRotation

        val pWidth = if (pageOrientation == PageOrientation.PORTRAIT) 21.0f else 29.7f
        val pHeight = if (pageOrientation == PageOrientation.PORTRAIT) 29.7f else 21.0f

        val topOff = topOffsetMm.toFloatOrNull() ?: 0.0f
        val leftOff = leftOffsetMm.toFloatOrNull() ?: 0.0f

        val unitSize = getUnitSize()
        val settings = LayoutSettings(
            pageWidthCm = pWidth,
            pageHeightCm = pHeight,
            marginCm = m,
            spacingCm = s,
            dpi = dpi,
            cuttingGuidesEnabled = cuttingGuidesEnabled,
            cuttingGuideThicknessPt = cuttingGuideThicknessPt,
            cuttingGuideStyle = cuttingGuideStyle,
            cuttingGuideColor = cuttingGuideColor,
            allowRotation = finalAllowRotation,
            topOffsetMm = topOff,
            leftOffsetMm = leftOff
        )

        try {
            if (mode == ProjectMode.BATCH_PAPER_SAVER) {
                if (batchItems.isEmpty()) {
                    // Pre-populate with default mixed set
                    batchItems.add(com.example.domain.model.BatchItem(label = "India Passport", widthCm = 3.5f, heightCm = 4.5f, quantity = 4))
                    batchItems.add(com.example.domain.model.BatchItem(label = "Stamp Size", widthCm = 2.0f, heightCm = 2.5f, quantity = 4))
                }
                computedPages = LayoutEngine.computeMixedBatchLayout(batchItems, settings)
            } else {
                computedPages = LayoutEngine.computeLayout(unitSize, q, settings)
            }
            return true
        } catch (e: LayoutException) {
            // Smart layout auto-switching fallback:
            if (pageOrientation == PageOrientation.PORTRAIT) {
                val altSettings = settings.copy(pageWidthCm = 29.7f, pageHeightCm = 21.0f)
                try {
                    computedPages = LayoutEngine.computeLayout(unitSize, q, altSettings)
                    pageOrientation = PageOrientation.LANDSCAPE
                    return true
                } catch (e2: LayoutException) {
                    // Both failed
                }
            } else {
                val altSettings = settings.copy(pageWidthCm = 21.0f, pageHeightCm = 29.7f)
                try {
                    computedPages = LayoutEngine.computeLayout(unitSize, q, altSettings)
                    pageOrientation = PageOrientation.PORTRAIT
                    return true
                } catch (e2: LayoutException) {
                    // Both failed
                }
            }

            isLayoutTooLargeError = true
            layoutEngineErrorText = e.message ?: "The configured layout exceeds available paper space."
            return false
        }
    }

    fun saveBitmapToLocalFile(context: Context, bitmap: Bitmap, prefix: String): Uri? {
        return try {
            val file = File(context.filesDir, "${prefix}_${System.currentTimeMillis()}.png")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun handlePhotoSelected(uri: Uri, isPhotoA: Boolean, context: Context, bitmap: Bitmap) {
        val savedUri = saveBitmapToLocalFile(context, bitmap, if (isPhotoA) "original_source_a" else "original_source_b")
        if (savedUri != null) {
            if (isPhotoA) {
                photoAUri = savedUri
            } else {
                photoBUri = savedUri
            }
        } else {
            if (isPhotoA) {
                photoAUri = uri
            } else {
                photoBUri = uri
            }
        }
    }

    fun handlePhotoCropped(bitmap: Bitmap, isPhotoA: Boolean) {
        if (isPhotoA) {
            cropABitmap = bitmap
            if (mode == ProjectMode.SINGLE || mode == ProjectMode.BATCH_PAPER_SAVER) {
                finalUnitBitmap = bitmap
                // Proceed directly to preview
                val success = computeCurrentLayout()
                if (success) {
                    currentStep = 6
                }
            } else {
                // Joint or Multi-Person or ID Card Mode -> choose photo B now
                currentStep = 4
            }
        } else {
            cropBBitmap = bitmap
            // Both picked! 
            if (mode == ProjectMode.MULTI_PERSON) {
                finalUnitBitmap = cropABitmap ?: cropBBitmap
                val success = computeCurrentLayout()
                if (success) {
                    currentStep = 6
                }
            } else if (mode == ProjectMode.ID_CARD) {
                generateJointComposite()
                val success = computeCurrentLayout()
                if (success) {
                    currentStep = 6
                }
            } else {
                generateJointComposite()
                currentStep = 5
            }
        }
    }

    fun generateJointComposite() {
        val bA = cropABitmap ?: return
        val bB = cropBBitmap ?: return
        val w = widthCm.toFloatOrNull() ?: 6.0f
        val h = heightCm.toFloatOrNull() ?: 4.5f

        if (mode == ProjectMode.ID_CARD) {
            val s = spacingCm.toFloatOrNull() ?: 0.2f
            finalUnitBitmap = BitmapUtils.createIdCardBitmap(
                bitmapA = bA,
                bitmapB = bB,
                cardWidthCm = w,
                cardHeightCm = h,
                arrangement = idCardArrangement,
                gapCm = s
            )
        } else {
            finalUnitBitmap = BitmapUtils.createJointBitmap(
                bitmapA = bA,
                bitmapB = bB,
                jointWidthCm = w,
                jointHeightCm = h,
                splitRatio = jointSplitRatio,
                dividerEnabled = jointDividerLinesEnabled,
                dividerWidthPt = 0.5f,
                dividerColor = jointDividerColor
            )
        }
    }

    fun onJointCompositeApproved() {
        val success = computeCurrentLayout()
        if (success) {
            currentStep = 6
        }
    }

    fun applyFormalAttire(isPhotoA: Boolean, attireType: String) {
        if (isPhotoA && cropABitmap != null) {
            cropABitmap = BitmapUtils.drawFormalAttireOverlay(cropABitmap!!, attireType)
            if (mode == ProjectMode.SINGLE || mode == ProjectMode.BATCH_PAPER_SAVER) {
                finalUnitBitmap = cropABitmap
            }
        } else if (!isPhotoA && cropBBitmap != null) {
            cropBBitmap = BitmapUtils.drawFormalAttireOverlay(cropBBitmap!!, attireType)
        }
        if (mode == ProjectMode.JOINT || mode == ProjectMode.ID_CARD) {
            generateJointComposite()
        }
        pushHistoryStateDebounced()
    }

    fun applyAutoRetouch(isPhotoA: Boolean) {
        if (isPhotoA && cropABitmap != null) {
            cropABitmap = BitmapUtils.applyAutoLightingAndRetouching(cropABitmap!!)
            if (mode == ProjectMode.SINGLE || mode == ProjectMode.BATCH_PAPER_SAVER) {
                finalUnitBitmap = cropABitmap
            }
        } else if (!isPhotoA && cropBBitmap != null) {
            cropBBitmap = BitmapUtils.applyAutoLightingAndRetouching(cropBBitmap!!)
        }
        if (mode == ProjectMode.JOINT || mode == ProjectMode.ID_CARD) {
            generateJointComposite()
        }
        pushHistoryStateDebounced()
    }

    fun applyBackdropColor(isPhotoA: Boolean, color: Int) {
        if (isPhotoA && cropABitmap != null) {
            cropABitmap = BitmapUtils.applyBackdropColor(cropABitmap!!, color)
            if (mode == ProjectMode.SINGLE || mode == ProjectMode.BATCH_PAPER_SAVER) {
                finalUnitBitmap = cropABitmap
            }
        } else if (!isPhotoA && cropBBitmap != null) {
            cropBBitmap = BitmapUtils.applyBackdropColor(cropBBitmap!!, color)
        }
        if (mode == ProjectMode.JOINT || mode == ProjectMode.ID_CARD) {
            generateJointComposite()
        }
        pushHistoryStateDebounced()
    }

    fun saveProjectPdf(context: Context, targetUri: Uri) {
        if (isSavingPdf) return
        isSavingPdf = true
        saveSuccessMessage = null

        viewModelScope.launch {
            try {
                val pages = computedPages
                val bitmap = finalUnitBitmap
                val w = widthCm.toFloatOrNull() ?: 3.5f
                val h = heightCm.toFloatOrNull() ?: 4.5f

                val q = quantity.toIntOrNull() ?: 16
                val m = marginCm.toFloatOrNull() ?: 0.5f
                val s = spacingCm.toFloatOrNull() ?: 0.2f

                if (pages.isNotEmpty() && bitmap != null) {
                    val pWidth = if (pageOrientation == PageOrientation.PORTRAIT) 21.0f else 29.7f
                    val pHeight = if (pageOrientation == PageOrientation.PORTRAIT) 29.7f else 21.0f

                    val unitSize = getUnitSize()
                    val settings = LayoutSettings(
                        pageWidthCm = pWidth,
                        pageHeightCm = pHeight,
                        marginCm = m,
                        spacingCm = s,
                        dpi = dpi,
                        cuttingGuidesEnabled = cuttingGuidesEnabled,
            cuttingGuideThicknessPt = cuttingGuideThicknessPt,
            cuttingGuideStyle = cuttingGuideStyle,
            cuttingGuideColor = cuttingGuideColor,
                        allowRotation = allowRotation
                    )

                    val success = PdfExporter.export(
                        context = context,
                        pages = pages,
                        croppedBitmap = bitmap,
                        unitSize = unitSize,
                        settings = settings,
                        outputUri = targetUri,
                        mode = mode.name,
                        croppedBitmapA = cropABitmap,
                        croppedBitmapB = cropBBitmap,
                        quantityA = quantityA
                    )

                    if (success) {
                        generatedPdfUri = targetUri

                        // Securely preserve cropped portrait photos in clean internal app storage
                        val savedPhotoAUri = cropABitmap?.let { bmp ->
                            saveBitmapToLocalFile(context, bmp, "cropped_a")?.toString()
                        } ?: photoAUri?.toString()

                        val savedPhotoBUri = if (mode == ProjectMode.JOINT || mode == ProjectMode.ID_CARD || mode == ProjectMode.MULTI_PERSON) {
                            cropBBitmap?.let { bmp ->
                                saveBitmapToLocalFile(context, bmp, "cropped_b")?.toString()
                            } ?: photoBUri?.toString()
                        } else {
                            photoBUri?.toString()
                        }

                        // Create project record for history
                        val project = Project(
                            name = if (filename.isNotBlank()) filename else "PassportPhotos",
                            mode = mode.name,
                            unitWidthCm = w,
                            unitHeightCm = h,
                            quantity = q,
                            photoAUri = savedPhotoAUri,
                            photoBUri = savedPhotoBUri,
                            splitRatio = jointSplitRatio,
                            dividerEnabled = jointDividerLinesEnabled,
                            dividerColor = jointDividerColor,
                            marginCm = m,
                            spacingCm = s,
                            dpi = dpi,
                            cuttingGuidesEnabled = cuttingGuidesEnabled,
            cuttingGuideThicknessPt = cuttingGuideThicknessPt,
            cuttingGuideStyle = cuttingGuideStyle,
            cuttingGuideColor = cuttingGuideColor,
                            allowRotation = allowRotation,
                            pdfFilePath = targetUri.toString(),
                            pageOrientation = pageOrientation.name
                        )
                        repository.insertProject(project)

                        saveSuccessMessage = "PDF exported and project saved to history successfully!"
                    } else {
                        saveSuccessMessage = "Failed to compile the PDF document."
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                saveSuccessMessage = "Error saving PDF: ${e.message}"
            } finally {
                isSavingPdf = false
            }
        }
    }

    fun loadProjectFromHistory(project: Project, context: Context) {
        viewModelScope.launch {
            try {
                filename = project.name
                mode = ProjectMode.valueOf(project.mode)
                widthCm = project.unitWidthCm.toString()
                heightCm = project.unitHeightCm.toString()
                quantity = project.quantity.toString()
                marginCm = project.marginCm.toString()
                spacingCm = project.spacingCm.toString()
                dpi = project.dpi
                cuttingGuidesEnabled = project.cuttingGuidesEnabled
                cuttingGuideThicknessPt = project.cuttingGuideThicknessPt
                cuttingGuideStyle = project.cuttingGuideStyle
                cuttingGuideColor = project.cuttingGuideColor
                allowRotation = project.allowRotation
                pageOrientation = try {
                    PageOrientation.valueOf(project.pageOrientation)
                } catch (e: Exception) {
                    PageOrientation.PORTRAIT
                }

                jointSplitRatio = project.splitRatio
                jointDividerLinesEnabled = project.dividerEnabled
                jointDividerColor = project.dividerColor

                if (!project.pdfFilePath.isNullOrBlank()) {
                    generatedPdfUri = Uri.parse(project.pdfFilePath)
                } else {
                    generatedPdfUri = null
                }

                // Load cached bitmaps back dynamically
                photoAUri = project.photoAUri?.let { Uri.parse(it) }
                photoBUri = project.photoBUri?.let { Uri.parse(it) }

                cropABitmap = photoAUri?.let { uri ->
                    BitmapUtils.loadScaledBitmap(context, uri)
                }
                cropBBitmap = photoBUri?.let { uri ->
                    BitmapUtils.loadScaledBitmap(context, uri)
                }

                // Restore finalUnitBitmap
                if (mode == ProjectMode.SINGLE || mode == ProjectMode.BATCH_PAPER_SAVER) {
                    finalUnitBitmap = cropABitmap
                } else if (mode == ProjectMode.MULTI_PERSON) {
                    finalUnitBitmap = cropABitmap ?: cropBBitmap
                } else {
                    generateJointComposite()
                }

                computeCurrentLayout()
                initHistory()
                currentStep = 7 // Go directly to export screen with fully hydrated and valid states
            } catch (e: Exception) {
                e.printStackTrace()
                startNewProject()
            }
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            repository.deleteProject(project)
        }
    }
}

data class HistoryState(
    val mode: ProjectMode,
    val widthCm: String,
    val heightCm: String,
    val quantity: String,
    val marginCm: String,
    val spacingCm: String,
    val dpi: Int,
    val cuttingGuidesEnabled: Boolean,
    val cuttingGuideThicknessPt: Float,
    val cuttingGuideStyle: String,
    val cuttingGuideColor: Int,
    val allowRotation: Boolean,
    val jointSplitRatio: Float,
    val jointDividerLinesEnabled: Boolean,
    val jointDividerColor: Int,
    val pageOrientation: PageOrientation,
    val idCardArrangement: String
)

class ProjectViewModelFactory(private val repository: ProjectRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProjectViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProjectViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
