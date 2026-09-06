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
        // Row 0 should contain 5 Passports (usable width 20cm fits 5 * 3.5 + 4 * 0.2 = 18.3cm)
        val row0 = placements.filter { it.rowIndex == 0 }
        assertEquals("Row 0 must contain 5 Passports", 5, row0.size)
        val yRow0 = row0[0].yCm
        assertTrue("All items in Row 0 must share identical Y coordinate for straight-line cutting",
            row0.all { Math.abs(it.yCm - yRow0) < 0.001f })

        // Row 1 should contain remaining 3 Passports
        val row1 = placements.filter { it.rowIndex == 1 }
        assertEquals("Row 1 must contain 3 Passports", 3, row1.size)
        val yRow1 = row1[0].yCm
        assertTrue("Row 1 must be below Row 0", yRow1 > yRow0 + 4.5f)
        assertTrue("All items in Row 1 must share identical Y coordinate for straight-line cutting",
            row1.all { Math.abs(it.yCm - yRow1) < 0.001f })

        // Row 2 should contain 4 Stamps on a fresh shelf
        val row2 = placements.filter { it.rowIndex == 2 }
        assertEquals("Row 2 must contain 4 Stamps", 4, row2.size)
        val yRow2 = row2[0].yCm
        assertTrue("Row 2 must be below Row 1", yRow2 > yRow1 + 4.5f)
        assertTrue("All items in Row 2 must share identical Y coordinate for straight-line cutting",
            row2.all { Math.abs(it.yCm - yRow2) < 0.001f })

        // 6. Verify all items stay strictly within margins
        for (p in placements) {
            assertTrue("Placement X (${p.xCm}) exceeds margin start", p.xCm >= 0.5f - 0.001f)
            assertTrue("Placement right (${p.xCm + p.widthCm}) exceeds usable page width", p.xCm + p.widthCm <= 20.5f + 0.001f)
            assertTrue("Placement Y (${p.yCm}) exceeds margin top", p.yCm >= 0.5f - 0.001f)
            assertTrue("Placement bottom (${p.yCm + p.heightCm}) exceeds usable page height", p.yCm + p.heightCm <= 29.2f + 0.001f)
        }
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
    fun testEmptyBatchReturnsEmpty() {
        val settings = LayoutSettings()
        val pages = LayoutEngine.computeMixedBatchLayout(emptyList(), settings)
        assertTrue("Empty batch should return empty list", pages.isEmpty())
    }
}
