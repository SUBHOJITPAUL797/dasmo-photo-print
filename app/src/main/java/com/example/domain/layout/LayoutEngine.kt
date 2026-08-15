package com.example.domain.layout

import com.example.domain.model.*
import kotlin.math.floor

class LayoutException(message: String) : Exception(message)

object LayoutEngine {
    fun computeLayout(
        unitSize: UnitSizeCm,
        quantity: Int,
        settings: LayoutSettings
    ): List<PageLayout> {
        if (quantity <= 0) {
            return emptyList()
        }

        val usableW = settings.pageWidthCm - 2 * settings.marginCm
        val usableH = settings.pageHeightCm - 2 * settings.marginCm

        if (usableW <= 0f || usableH <= 0f) {
            throw LayoutException("Page margins are too large. Usable space is zero.")
        }

        // 1. Calculate dimensions for normal and rotated cells
        val cellWNormal = unitSize.widthCm
        val cellHNormal = unitSize.heightCm
        val cellWRotated = unitSize.heightCm
        val cellHRotated = unitSize.widthCm

        val colsNormal = floor((usableW + settings.spacingCm) / (cellWNormal + settings.spacingCm)).toInt()
        val colsRotated = floor((usableW + settings.spacingCm) / (cellWRotated + settings.spacingCm)).toInt()

        var bestN1 = 0
        var bestN2 = 0
        var maxCapacity = 0

        // Find the optimal row mix that maximizes page capacity
        val maxPossibleNormalRows = floor((usableH + settings.spacingCm) / (cellHNormal + settings.spacingCm)).toInt()
        val maxPossibleRotatedRows = if (settings.allowRotation) {
            floor((usableH + settings.spacingCm) / (cellHRotated + settings.spacingCm)).toInt()
        } else {
            0
        }

        for (n1 in 0..maxPossibleNormalRows) {
            val maxN2 = if (settings.allowRotation) maxPossibleRotatedRows else 0
            for (n2 in 0..maxN2) {
                if (n1 == 0 && n2 == 0) continue

                // Check total height of this mixed row combination
                val totalH = n1 * cellHNormal + n2 * cellHRotated + (n1 + n2 - 1) * settings.spacingCm
                if (totalH <= usableH) {
                    val capacity = n1 * colsNormal + n2 * colsRotated
                    if (capacity > maxCapacity) {
                        maxCapacity = capacity
                        bestN1 = n1
                        bestN2 = n2
                    } else if (capacity == maxCapacity && capacity > 0) {
                        // Prefer the layout with fewer total rows of rotation if capacities match, or the layout that takes less height
                        val currentHeight = n1 * cellHNormal + n2 * cellHRotated + (n1 + n2 - 1) * settings.spacingCm
                        val bestHeight = bestN1 * cellHNormal + bestN2 * cellHRotated + (bestN1 + bestN2 - 1) * settings.spacingCm
                        if (currentHeight < bestHeight) {
                            bestN1 = n1
                            bestN2 = n2
                        }
                    }
                }
            }
        }

        if (maxCapacity == 0) {
            throw LayoutException("This size is too large to fit on an A4 page with the current margins. Please reduce the size or margins.")
        }

        val pagesNeeded = kotlin.math.ceil(quantity.toDouble() / maxCapacity).toInt()
        val pages = mutableListOf<PageLayout>()

        val startX = settings.marginCm + (settings.leftOffsetMm / 10f)
        val startY = settings.marginCm + (settings.topOffsetMm / 10f)

        for (p in 0 until pagesNeeded) {
            val placements = mutableListOf<UnitPlacement>()
            var remainingItemsOnPage = minOf(maxCapacity, quantity - p * maxCapacity)

            var currentY = startY
            var currentPlacementRowIndex = 0

            // 1. Pack Normal Rows
            for (r in 0 until bestN1) {
                for (c in 0 until colsNormal) {
                    if (remainingItemsOnPage > 0) {
                        val xCm = startX + c * (cellWNormal + settings.spacingCm)
                        placements.add(
                            UnitPlacement(
                                colIndex = c,
                                rowIndex = currentPlacementRowIndex,
                                xCm = xCm,
                                yCm = currentY,
                                isRotated = false,
                                widthCm = cellWNormal,
                                heightCm = cellHNormal
                            )
                        )
                        remainingItemsOnPage--
                    }
                }
                currentY += cellHNormal + settings.spacingCm
                currentPlacementRowIndex++
            }

            // 2. Pack Rotated Rows
            for (r in 0 until bestN2) {
                for (c in 0 until colsRotated) {
                    if (remainingItemsOnPage > 0) {
                        val xCm = startX + c * (cellWRotated + settings.spacingCm)
                        placements.add(
                            UnitPlacement(
                                colIndex = c,
                                rowIndex = currentPlacementRowIndex,
                                xCm = xCm,
                                yCm = currentY,
                                isRotated = true,
                                widthCm = cellWRotated,
                                heightCm = cellHRotated
                            )
                        )
                        remainingItemsOnPage--
                    }
                }
                currentY += cellHRotated + settings.spacingCm
                currentPlacementRowIndex++
            }

            pages.add(
                PageLayout(
                    pageIndex = p,
                    placements = placements,
                    isRotated = bestN2 > 0 && bestN1 == 0,
                    cols = maxOf(colsNormal, colsRotated),
                    rows = bestN1 + bestN2,
                    cellWidthCm = if (bestN2 > 0 && bestN1 == 0) cellWRotated else cellWNormal,
                    cellHeightCm = if (bestN2 > 0 && bestN1 == 0) cellHRotated else cellHNormal
                )
            )
        }

        return pages
    }

    /**
     * Compute multi-photo mixed batch layout packing (Paper Saver Mode).
     * Places different sized photo items (e.g. 4 Passport + 4 Stamp + 2 Joint) on 1 page efficiently.
     */
    fun computeMixedBatchLayout(
        batchItems: List<BatchItem>,
        settings: LayoutSettings
    ): List<PageLayout> {
        val usableW = settings.pageWidthCm - 2 * settings.marginCm
        val usableH = settings.pageHeightCm - 2 * settings.marginCm

        if (usableW <= 0f || usableH <= 0f || batchItems.isEmpty()) {
            return emptyList()
        }

        val startX = settings.marginCm + (settings.leftOffsetMm / 10f)
        val startY = settings.marginCm + (settings.topOffsetMm / 10f)

        val pages = mutableListOf<PageLayout>()
        var currentPagePlacements = mutableListOf<UnitPlacement>()
        var currentPageIndex = 0

        var curX = startX
        var curY = startY
        var rowMaxH = 0f
        var colIndex = 0
        var rowIndex = 0

        // Expand all items to individual placements
        for (item in batchItems) {
            for (i in 0 until item.quantity) {
                val itemW = item.widthCm
                val itemH = item.heightCm

                // Check if fits horizontally in current row
                if (curX + itemW > startX + usableW && curX > startX) {
                    // Move to next row
                    curX = startX
                    curY += rowMaxH + settings.spacingCm
                    rowMaxH = 0f
                    colIndex = 0
                    rowIndex++
                }

                // Check if fits vertically on current page
                if (curY + itemH > startY + usableH) {
                    // Save current page layout and start new page
                    if (currentPagePlacements.isNotEmpty()) {
                        pages.add(
                            PageLayout(
                                pageIndex = currentPageIndex++,
                                placements = currentPagePlacements,
                                isRotated = false,
                                cols = colIndex + 1,
                                rows = rowIndex + 1,
                                cellWidthCm = itemW,
                                cellHeightCm = itemH
                            )
                        )
                        currentPagePlacements = mutableListOf()
                    }
                    curX = startX
                    curY = startY
                    rowMaxH = 0f
                    colIndex = 0
                    rowIndex = 0
                }

                currentPagePlacements.add(
                    UnitPlacement(
                        colIndex = colIndex,
                        rowIndex = rowIndex,
                        xCm = curX,
                        yCm = curY,
                        isRotated = false,
                        widthCm = itemW,
                        heightCm = itemH
                    )
                )

                curX += itemW + settings.spacingCm
                if (itemH > rowMaxH) {
                    rowMaxH = itemH
                }
                colIndex++
            }
        }

        if (currentPagePlacements.isNotEmpty()) {
            pages.add(
                PageLayout(
                    pageIndex = currentPageIndex,
                    placements = currentPagePlacements,
                    isRotated = false,
                    cols = maxOf(1, colIndex),
                    rows = maxOf(1, rowIndex + 1),
                    cellWidthCm = batchItems.firstOrNull()?.widthCm ?: 3.5f,
                    cellHeightCm = batchItems.firstOrNull()?.heightCm ?: 4.5f
                )
            )
        }

        return pages
    }
}
