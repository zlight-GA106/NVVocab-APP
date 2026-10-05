package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.update.EasyUpdateRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EasyUpdatePolicyTest {
    private val release = EasyUpdateRelease("1.5.1", 16, "更新说明", "http://192.168.95.55:19910/update.apk", 100, "a".repeat(64), false)

    @Test
    fun acceptsCustomServerAndHelpLinkButRejectsInvalidSourceSchemes() {
        assertEquals("http://192.168.95.55:19910", EasyUpdatePolicy.normalizeServerUrl(" http://192.168.95.55:19910/admin/help/ "))
        assertEquals("https://updates.example.com/prefix", EasyUpdatePolicy.normalizeServerUrl("https://updates.example.com/prefix/"))
        assertEquals("https://updates.example.com/prefix/update.apk", EasyUpdatePolicy.resolveDownloadUrl("https://updates.example.com/prefix", "update.apk"))
        listOf("file:///tmp", "192.168.95.55:19910", "https://user:password@updates.example.com", "https://updates.example.com?key=secret").forEach {
            assertTrue(runCatching { EasyUpdatePolicy.normalizeServerUrl(it) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals("https://updates.example.com/update.apk", EasyUpdatePolicy.resolveRedirectUrl("https://updates.example.com/api/latest?version_code=15", "/update.apk"))
        listOf("http://updates.example.com/update.apk", "https://other.example.com/update.apk", "file:///tmp/update.apk").forEach {
            assertTrue(runCatching { EasyUpdatePolicy.resolveDownloadUrl("https://updates.example.com", it) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { EasyUpdatePolicy.resolveRedirectUrl("https://updates.example.com/api/latest", it) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun rejectsDowngradesAndMissingOrExcessiveFileMetadata() {
        EasyUpdatePolicy.validateRelease(release, 15)
        listOf(release.copy(versionCode = 15), release.copy(sha256 = "bad"), release.copy(size = 0), release.copy(size = EasyUpdatePolicy.MAX_APK_SIZE + 1)).forEach {
            assertTrue(runCatching { EasyUpdatePolicy.validateRelease(it, 15) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun rejectsTruncatedOrCorruptDownloads() {
        EasyUpdatePolicy.verifyDownload(release, 100, "A".repeat(64))
        assertTrue(runCatching { EasyUpdatePolicy.verifyDownload(release, 99, release.sha256) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { EasyUpdatePolicy.verifyDownload(release, 100, "b".repeat(64)) }.exceptionOrNull() is IllegalArgumentException)
    }
}
