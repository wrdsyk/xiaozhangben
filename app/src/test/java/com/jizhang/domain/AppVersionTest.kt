package com.jizhang.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun `higher numbers are newer`() {
        assertTrue(AppVersion.isNewer("0.5.1", "0.5.0"))
        assertTrue(AppVersion.isNewer("0.6.0", "0.5.9"))
        assertTrue(AppVersion.isNewer("1.0.0", "0.99.99"))
    }

    @Test
    fun `segments compare as numbers, not text`() {
        assertTrue(AppVersion.isNewer("0.10.0", "0.9.0"))
        assertFalse(AppVersion.isNewer("0.9.0", "0.10.0"))
        assertTrue(AppVersion.isNewer("0.5.12", "0.5.2"))
    }

    @Test
    fun `same or lower is not newer`() {
        assertFalse(AppVersion.isNewer("0.5.0", "0.5.0"))
        assertFalse(AppVersion.isNewer("0.4.9", "0.5.0"))
    }

    @Test
    fun `the v prefix and missing segments are tolerated`() {
        assertFalse(AppVersion.isNewer("v0.5.0", "0.5.0"))
        assertTrue(AppVersion.isNewer("v0.5.1", "0.5.0"))
        assertTrue(AppVersion.isNewer("V1.0", "0.9.9"))
        assertFalse(AppVersion.isNewer("0.5", "0.5.0"))
        assertTrue(AppVersion.isNewer("0.5.0.1", "0.5.0"))
        assertEquals("0.5.1", AppVersion.clean(" v0.5.1 "))
    }

    @Test
    fun `a pre-release sits below its release`() {
        assertTrue(AppVersion.isNewer("0.6.0", "0.6.0-beta.1"))
        assertFalse(AppVersion.isNewer("0.6.0-beta.1", "0.6.0"))
        assertTrue(AppVersion.isNewer("0.6.0-beta.2", "0.6.0-beta.1"))
        assertTrue(AppVersion.isNewer("0.6.0-beta.10", "0.6.0-beta.9"))
        assertTrue(AppVersion.isNewer("0.6.0-rc.1", "0.6.0-beta.9"))
        assertTrue(AppVersion.isNewer("0.6.0-beta.1", "0.5.9"))
    }

    @Test
    fun `build metadata is ignored`() {
        assertFalse(AppVersion.isNewer("0.5.0+7", "0.5.0"))
    }

    @Test
    fun `unreadable versions are never newer`() {
        assertFalse(AppVersion.isNewer("", "0.5.0"))
        assertFalse(AppVersion.isNewer("nightly", "0.5.0"))
        assertFalse(AppVersion.isNewer("0.5.x", "0.5.0"))
        assertFalse(AppVersion.isNewer("0.6.0", "garbage"))
        assertFalse(AppVersion.isNewer("0..1", "0.5.0"))
    }

    @Test
    fun `sizes read as KB or MB`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2 KB", formatBytes(2048))
        assertEquals("12.3 MB", formatBytes(12_900_000))
    }
}
