package com.example.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

data class UnitSizeCm(
    val widthCm: Float,
    val heightCm: Float
) : Serializable

enum class ProjectMode {
    SINGLE,
    JOINT,
    ID_CARD,
    MULTI_PERSON,
    BATCH_PAPER_SAVER
}

data class PassportPresetSpec(
    val id: String,
    val title: String,
    val widthCm: Float,
    val heightCm: Float,
    val category: String, // "India Standards", "US & Global Visas", "Cards & Stamps"
    val hintText: String,
    val defaultQty: Int = 16
) : Serializable

data class BatchItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String,
    val widthCm: Float,
    val heightCm: Float,
    val quantity: Int,
    val photoUri: String? = null
) : Serializable

data class JointConfig(
    val photoAUri: String,
    val photoBUri: String,
    val splitRatio: Float = 0.5f,      // fraction of joint width given to Photo A
    val dividerEnabled: Boolean = false,
    val dividerWidthPt: Float = 0.5f,
    val dividerColor: Int = 0xFF000000.toInt()
) : Serializable

data class LayoutSettings(
    val pageWidthCm: Float = 21.0f,
    val pageHeightCm: Float = 29.7f,
    val marginCm: Float = 0.5f,
    val spacingCm: Float = 0.2f,
    val dpi: Int = 300,
    val cuttingGuidesEnabled: Boolean = true,
    val cuttingGuideThicknessPt: Float = 1.0f,
    val cuttingGuideStyle: String = "dashed",
    val cuttingGuideColor: Int = 0xFF000000.toInt(),
    val allowRotation: Boolean = true,
    val topOffsetMm: Float = 0.0f,
    val leftOffsetMm: Float = 0.0f
) : Serializable

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mode: String, // "SINGLE", "JOINT", "BATCH_PAPER_SAVER", etc.
    val unitWidthCm: Float,
    val unitHeightCm: Float,
    val quantity: Int,
    val photoAUri: String?, // also sourcePhotoUri for SINGLE mode
    val photoBUri: String?, // only for JOINT mode
    val splitRatio: Float = 0.5f,
    val dividerEnabled: Boolean = false,
    val dividerWidthPt: Float = 0.5f,
    val dividerColor: Int = 0xFF000000.toInt(),
    val marginCm: Float = 0.5f,
    val spacingCm: Float = 0.2f,
    val dpi: Int = 300,
    val cuttingGuidesEnabled: Boolean = true,
    val cuttingGuideThicknessPt: Float = 1.0f,
    val cuttingGuideStyle: String = "dashed",
    val cuttingGuideColor: Int = 0xFF000000.toInt(),
    val allowRotation: Boolean = true,
    val pdfFilePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val pageOrientation: String = "PORTRAIT",
    val customerName: String? = null,
    val customerPhone: String? = null,
    val billingAmount: Double? = null
) : Serializable

enum class PageOrientation {
    PORTRAIT,
    LANDSCAPE
}

data class UnitPlacement(
    val colIndex: Int,
    val rowIndex: Int,
    val xCm: Float,
    val yCm: Float,
    val isRotated: Boolean = false,
    val widthCm: Float = 0f,
    val heightCm: Float = 0f
) : Serializable

data class PageLayout(
    val pageIndex: Int,
    val placements: List<UnitPlacement>,
    val isRotated: Boolean,
    val cols: Int,
    val rows: Int,
    val cellWidthCm: Float,
    val cellHeightCm: Float
) : Serializable
