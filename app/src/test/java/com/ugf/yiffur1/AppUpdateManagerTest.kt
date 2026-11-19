package com.ugf.yiffur1

import com.ugf.yiffur1.data.update.AppVersionComparator
import com.ugf.yiffur1.data.update.GitHubReleaseAssetDto
import com.ugf.yiffur1.data.update.GitHubReleaseDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun semanticVersionComparisonHandlesVPrefixLegacyAndPrereleases() {
        assertTrue(AppVersionComparator.isNewer("v2.2.0", "2.1.0"))
        assertTrue(AppVersionComparator.isNewer("2.1.1", "2.1.0"))
        assertTrue(AppVersionComparator.isNewer("v2.1.0", "2.0.0"))
        assertTrue(AppVersionComparator.isNewer("v2.1.0", "1.303"))
        assertTrue(AppVersionComparator.isNewer("v2.1.0", "1.001"))
        assertTrue(AppVersionComparator.isNewer("v2.2.0", "2.2.0-beta.1"))

        assertFalse(AppVersionComparator.isNewer("v2.1.0", "2.1.0"))
        assertFalse(AppVersionComparator.isNewer("v2.0.0", "2.1.0"))
        assertFalse(AppVersionComparator.isNewer("2.1.0-beta.1", "2.1.0"))
    }

    @Test
    fun apkAssetPickerSelectsBestReleaseAndMatchesPackageVariant() {
        val assets = listOf(
            GitHubReleaseAssetDto("Yiffur-v2.2.0-debug.apk", "https://example.com/debug.apk", 22_000_000L),
            GitHubReleaseAssetDto("Yiffur-v2.2.0-release-universal.apk", "https://example.com/release.apk", 14_000_000L),
            GitHubReleaseAssetDto("Yiffur-v2.2.0-legacy-ugf0-release.apk", "https://example.com/legacy.apk", 14_000_000L),
            GitHubReleaseAssetDto("checksums.txt", "https://example.com/checksums.txt", 128L)
        )

        val standard = AppVersionComparator.pickBestApkAsset(
            assets = assets,
            packageName = "com.ugf.yiffur1",
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a")
        )
        assertEquals("Yiffur-v2.2.0-release-universal.apk", standard?.name)

        val legacy = AppVersionComparator.pickBestApkAsset(
            assets = assets,
            packageName = "com.ugf0.yiffur",
            supportedAbis = listOf("arm64-v8a")
        )
        assertEquals("Yiffur-v2.2.0-legacy-ugf0-release.apk", legacy?.name)
    }

    @Test
    fun sha256ChecksumExtractionParsesReleaseNotesAccurately() {
        val expectedHash = "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0"
        val notes = """
            ## Yiffur v2.2.0
            - Adaptive engine & push notifications
            - SHA-256 (`Yiffur-v2.2.0-release.apk`): `$expectedHash`
        """.trimIndent()

        assertEquals(
            expectedHash,
            AppVersionComparator.extractSha256FromNotes(notes, "Yiffur-v2.2.0-release.apk")
        )
        assertNull(AppVersionComparator.extractSha256FromNotes("No checksums here"))
    }

    @Test
    fun githubReleaseDtoDeserializesLiveApiPayload() {
        val raw = """
            {
              "tag_name": "v2.2.0",
              "name": "Yiffur v2.2.0",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/EchoTiger/yiffur-remastered/releases/tag/v2.2.0",
              "body": "Performance & updater release",
              "assets": [
                {
                  "name": "Yiffur-v2.2.0-release.apk",
                  "browser_download_url": "https://github.com/EchoTiger/yiffur-remastered/releases/download/v2.2.0/Yiffur-v2.2.0-release.apk",
                  "size": 15400000
                }
              ]
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val dto = json.decodeFromString<GitHubReleaseDto>(raw)
        assertEquals("v2.2.0", dto.tagName)
        assertEquals(1, dto.assets.size)
        assertEquals("Yiffur-v2.2.0-release.apk", dto.assets.first().name)
    }
}
