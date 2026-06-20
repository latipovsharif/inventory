package io.proffi.inventory.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDebouncerTest {

    @Test
    fun firstScanIsNotDuplicate() {
        assertFalse(ScanDebouncer().isDuplicate("A"))
    }

    @Test
    fun sameBarcodeImmediatelyIsDuplicate() {
        val d = ScanDebouncer()
        d.isDuplicate("A")
        assertTrue(d.isDuplicate("A"))
    }

    @Test
    fun differentBarcodeIsNotDuplicate() {
        val d = ScanDebouncer()
        d.isDuplicate("A")
        assertFalse(d.isDuplicate("B"))
    }

    @Test
    fun resetClearsState() {
        val d = ScanDebouncer()
        d.isDuplicate("A")
        d.reset()
        assertFalse(d.isDuplicate("A"))
    }

    @Test
    fun sameBarcodeAfterWindowIsAllowed() {
        val d = ScanDebouncer(windowMs = 1)
        d.isDuplicate("A")
        Thread.sleep(5)
        assertFalse(d.isDuplicate("A"))
    }
}
