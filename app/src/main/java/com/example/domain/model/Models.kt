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
    MULTI_PERSON
}

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
    val allowRotation: Boolean = true
) : Serializable

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mode: String, // "SINGLE" or "JOINT"
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
    val allowRotation: Boolean = true,
    val pdfFilePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val pageOrientation: String = "PORTRAIT"
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
