package com.example

import com.example.domain.layout.LayoutEngine
import com.example.domain.model.BatchItem
import com.example.domain.model.LayoutSettings
import org.junit.Assert.*
import org.junit.Test

class MixedBatchLayoutTest {

    @Test
    fun testCustomerScenario_8PassportsAnd4Stamps_FitOnSingleA4WithStraightCuttingLines() {
        // Customer request: 12 photos total: 8 Passports (3.5x4.5cm) and 4 Stamps (2.0x2.5cm)
        val batchItems = listOf(
            BatchItem(label = "India Passport", widthCm = 3.5f, heightCm = 4.5f, quantity = 8),
            BatchItem(label = "Stamp Size", widthCm = 2.0f, heightCm = 2.5f, quantity = 4)
        )

        val settings = LayoutSettings(
            pageWidthCm = 21.0f,
            pageHeightCm = 29.7f,
            marginCm = 0.5f,
            spacingCm = 0.2f
        )

        val pages = LayoutEngine.computeMixedBatchLayout(batchItems, settings)

        // 1. Must fit on a single A4 page (Paper Saver!)
        assertEquals("Should only require 1 sheet of paper", 1, pages.size)

        val page = pages[0]
        val placements = page.placements

        // 2. Total placements must equal 12
        assertEquals("Total photos must be exactly 12", 12, placements.size)

        // 3. First 8 placements must be Passports (3.5 x 4.5 cm)
        for (i in 0 until 8) {
            assertEquals("Passport width mismatch at index $i", 3.5f, placements[i].widthCm, 0.001f)
            assertEquals("Passport height mismatch at index $i", 4.5f, placements[i].heightCm, 0.001f)
        }

        // 4. Remaining 4 placements must be Stamps (2.0 x 2.5 cm)
        for (i in 8 until 12) {
            assertEquals("Stamp width mismatch at index $i", 2.0f, placements[i].widthCm, 0.001f)
            assertEquals("Stamp height mismatch at index $i", 2.5f, placements[i].heightCm, 0.001f)
        }

        // 5. Verify row distribution and straight cutting lines
        // 5. Verify intelligent compact row distribution:
        // Row 0 contains 5 Passports (usable width 20cm fits 5 * 3.5 + 4 * 0.2 = 18.3cm)
        val row0 = placements.filter { it.rowIndex == 0 }
        assertEquals("Row 0 must contain 5 Passports", 5, row0.size)
        val yRow0 = row0[0].yCm
        assertTrue("All items in Row 0 must share identical Y coordinate for straight-line cutting",
            row0.all { Math.abs(it.yCm - yRow0) < 0.001f })

        // Row 1 intelligently fills remaining space: contains remaining 3 Passports + all 4 Stamps = 7 photos!
        val row1 = placements.filter { it.rowIndex == 1 }
        assertEquals("Row 1 must intelligently pack remaining 3 Passports + 4 Stamps (7 photos)", 7, row1.size)
        val yRow1 = row1[0].yCm
        assertTrue("Row 1 must be below Row 0", yRow1 > yRow0 + 4.5f)
        assertTrue("All items in Row 1 must share identical top Y coordinate for straight cutting",
            row1.all { Math.abs(it.yCm - yRow1) < 0.001f })

        // Total rows used must be ONLY 2 ROWS (saves maximum paper!)
        val totalRows = placements.map { it.rowIndex }.distinct().size
        assertEquals("Intelligent compact packing must only use 2 rows", 2, totalRows)

        // 6. Verify all items stay strictly within margins
        for (p in placements) {
            assertTrue("Placement X (${p.xCm}) exceeds margin start", p.xCm >= 0.5f - 0.001f)
            assertTrue("Placement right (${p.xCm + p.widthCm}) exceeds usable page width", p.xCm + p.widthCm <= 20.5f + 0.001f)
            assertTrue("Placement Y (${p.yCm}) exceeds margin top", p.yCm >= 0.5f - 0.001f)
            assertTrue("Placement bottom (${p.yCm + p.heightCm}) exceeds usable page height", p.yCm + p.heightCm <= 29.2f + 0.001f)
        }
    }

    @Test
    fun testSeparatedShelvesWhenCompactPackingDisabled() {
        val batchItems = listOf(
            BatchItem(label = "India Passport", widthCm = 3.5f, heightCm = 4.5f, quantity = 8),
            BatchItem(label = "Stamp Size", widthCm = 2.0f, heightCm = 2.5f, quantity = 4)
        )

        val settings = LayoutSettings(
            pageWidthCm = 21.0f,
            pageHeightCm = 29.7f,
            marginCm = 0.5f,
            spacingCm = 0.2f,
            compactPacking = false // Disable compact packing
        )

        val pages = LayoutEngine.computeMixedBatchLayout(batchItems, settings)
        assertEquals(1, pages.size)
        val placements = pages[0].placements
        assertEquals(12, placements.size)

        // With compact packing disabled, it should use 3 separate rows
        val totalRows = placements.map { it.rowIndex }.distinct().size
        assertEquals("Disabled compact packing must use 3 separate shelves", 3, totalRows)
    }

    @Test
    fun testCombo16PassportsAnd8Stamps() {
        val batchItems = listOf(
            BatchItem(label = "India Passport", widthCm = 3.5f, heightCm = 4.5f, quantity = 16),
            BatchItem(label = "Stamp Size", widthCm = 2.0f, heightCm = 2.5f, quantity = 8)
        )

        val settings = LayoutSettings(
            pageWidthCm = 21.0f,
            pageHeightCm = 29.7f,
            marginCm = 0.5f,
            spacingCm = 0.2f
        )

        val pages = LayoutEngine.computeMixedBatchLayout(batchItems, settings)
        assertEquals("Should fit on 1 sheet", 1, pages.size)
        assertEquals("Total photos must be 24", 24, pages[0].placements.size)
    }

    @Test
    fun testAudit_NoClashing_ExactGaps_And19PassportsPlus2Stamps() {
        // Test custom scenario: 19 Passports (3.5x4.5cm) + 2 Stamps (2.0x2.5cm)
        // With custom margins = 0.6cm and custom gap spacing = 0.3cm
        val batchItems = listOf(
            BatchItem(label = "India Passport", widthCm = 3.5f, heightCm = 4.5f, quantity = 19),
            BatchItem(label = "Stamp Size", widthCm = 2.0f, heightCm = 2.5f, quantity = 2)
        )

        val spacing = 0.3f
        val margin = 0.6f
        val settings = LayoutSettings(
            pageWidthCm = 21.0f,
            pageHeightCm = 29.7f,
            marginCm = margin,
            spacingCm = spacing
        )

        val pages = LayoutEngine.computeMixedBatchLayout(batchItems, settings)

        // 1. Total photos must be exactly 21 (19 + 2)
        val allPlacements = pages.flatMap { it.placements }
        assertEquals("Total photos across all pages must be 21", 21, allPlacements.size)

        // 2. Comprehensive Anti-Collision & Gap Audit per page
        for (page in pages) {
            val placements = page.placements

            // Verify bounds within page margins
            for (p in placements) {
                assertTrue("Left margin violation", p.xCm >= margin - 0.001f)
                assertTrue("Top margin violation", p.yCm >= margin - 0.001f)
                assertTrue("Right margin violation", p.xCm + p.widthCm <= settings.pageWidthCm - margin + 0.001f)
                assertTrue("Bottom margin violation", p.yCm + p.heightCm <= settings.pageHeightCm - margin + 0.001f)
            }

            // Pairwise Collision Audit: Verify NO TWO PHOTOS OVERLAP
            for (i in 0 until placements.size) {
                for (j in i + 1 until placements.size) {
                    val a = placements[i]
                    val b = placements[j]

                    val aRight = a.xCm + a.widthCm
                    val aBottom = a.yCm + a.heightCm
                    val bRight = b.xCm + b.widthCm
                    val bBottom = b.yCm + b.heightCm

                    // They must not intersect
                    val horizontalOverlap = (a.xCm < bRight - 0.001f) && (aRight > b.xCm + 0.001f)
                    val verticalOverlap = (a.yCm < bBottom - 0.001f) && (aBottom > b.yCm + 0.001f)
                    val collides = horizontalOverlap && verticalOverlap

                    assertFalse("Photos at index $i and $j COLLIDE! A: [${a.xCm}, ${a.yCm}, $aRight, $aBottom] vs B: [${b.xCm}, ${b.yCm}, $bRight, $bBottom]", collides)
                }
            }

            // Horizontal Gap Audit: Adjacent photos in same row must have AT LEAST spacingCm gap
            val rows = placements.groupBy { it.rowIndex }
            for ((rowIndex, rowPlacements) in rows) {
                val sorted = rowPlacements.sortedBy { it.xCm }
                for (k in 0 until sorted.size - 1) {
                    val currentRight = sorted[k].xCm + sorted[k].widthCm
                    val nextLeft = sorted[k + 1].xCm
                    val actualGap = nextLeft - currentRight
                    assertTrue("Horizontal gap in row $rowIndex is smaller than requested ($actualGap < $spacing)", actualGap >= spacing - 0.001f)
                }
            }

            // Vertical Shelf Gap Audit: Adjacent rows must have AT LEAST spacingCm vertical gap
            val rowIndices = rows.keys.sorted()
            for (r in 0 until rowIndices.size - 1) {
                val rowA = rows[rowIndices[r]]!!
                val rowB = rows[rowIndices[r + 1]]!!

                val rowABottom = rowA.maxOf { it.yCm + it.heightCm }
                val rowBTop = rowB.minOf { it.yCm }
                val verticalGap = rowBTop - rowABottom

                assertTrue("Vertical gap between row ${rowIndices[r]} and ${rowIndices[r+1]} is smaller than requested ($verticalGap < $spacing)", verticalGap >= spacing - 0.001f)
            }
        }
    }

    @Test
    fun testEmptyBatchReturnsEmpty() {
        val settings = LayoutSettings()
        val pages = LayoutEngine.computeMixedBatchLayout(emptyList(), settings)
        assertTrue("Empty batch should return empty list", pages.isEmpty())
    }
}
