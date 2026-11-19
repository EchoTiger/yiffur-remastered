package com.ugf.yiffur1

import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.PostTags
import com.ugf.yiffur1.domain.model.Rating
import com.ugf.yiffur1.domain.model.VideoQualityPreference
import com.ugf.yiffur1.domain.model.VideoVariant
import com.ugf.yiffur1.domain.perf.AdaptiveBudget
import com.ugf.yiffur1.domain.perf.AdaptiveTierCalculator
import com.ugf.yiffur1.domain.perf.DeviceHardwareProfile
import com.ugf.yiffur1.domain.perf.JankPressure
import com.ugf.yiffur1.domain.perf.MemoryPressure
import com.ugf.yiffur1.domain.perf.PerformanceMode
import com.ugf.yiffur1.domain.perf.PerformanceTier
import com.ugf.yiffur1.domain.perf.ThermalPressure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePerformanceEngineTest {

    private val flagshipHw = DeviceHardwareProfile(
        totalRamMb = 12288,
        memoryClassMb = 512,
        cpuCores = 8,
        apiLevel = 35,
        isLowRamDevice = false,
        hasHwAv1 = true,
        hasHwVp9 = true,
        hasHwH264 = true
    )

    private val midRangeHw = DeviceHardwareProfile(
        totalRamMb = 4096,
        memoryClassMb = 256,
        cpuCores = 6,
        apiLevel = 31,
        isLowRamDevice = false,
        hasHwAv1 = false,
        hasHwVp9 = true,
        hasHwH264 = true
    )

    private val legacyLowRamHw = DeviceHardwareProfile(
        totalRamMb = 2048,
        memoryClassMb = 128,
        cpuCores = 4,
        apiLevel = 24,
        isLowRamDevice = true,
        hasHwAv1 = false,
        hasHwVp9 = false,
        hasHwH264 = true
    )

    @Test
    fun hardwareBaselineClassificationScalesByDeviceClass() {
        assertEquals(PerformanceTier.ULTRA, flagshipHw.baselineTier)
        assertEquals(4, flagshipHw.initialDecodeThreads)

        assertEquals(PerformanceTier.BALANCED, midRangeHw.baselineTier)
        assertEquals(3, midRangeHw.initialDecodeThreads)

        assertEquals(PerformanceTier.POWER_SAVER, legacyLowRamHw.baselineTier)
        assertEquals(2, legacyLowRamHw.initialDecodeThreads)
        assertTrue(legacyLowRamHw.memoryCachePercent < flagshipHw.memoryCachePercent)
    }

    @Test
    fun adaptiveAutoStepsDownUnderThermalMemoryJankAndBatterySaver() {
        // Cool flagship stays Ultra
        val nominal = AdaptiveTierCalculator.compute(
            mode = PerformanceMode.ADAPTIVE_AUTO,
            hardware = flagshipHw,
            thermal = ThermalPressure.NOMINAL,
            memory = MemoryPressure.NORMAL,
            jank = JankPressure.SMOOTH,
            isPowerSaveMode = false,
            isMeteredDataSaver = false
        )
        assertEquals(PerformanceTier.ULTRA, nominal.tier)

        // Moderate thermal forces Power Saver
        val warm = AdaptiveTierCalculator.compute(
            mode = PerformanceMode.ADAPTIVE_AUTO,
            hardware = flagshipHw,
            thermal = ThermalPressure.MODERATE,
            memory = MemoryPressure.NORMAL,
            jank = JankPressure.SMOOTH,
            isPowerSaveMode = false,
            isMeteredDataSaver = false
        )
        assertEquals(PerformanceTier.POWER_SAVER, warm.tier)

        // Severe thermal or critical memory forces Survival
        val hot = AdaptiveTierCalculator.compute(
            mode = PerformanceMode.ADAPTIVE_AUTO,
            hardware = flagshipHw,
            thermal = ThermalPressure.SEVERE,
            memory = MemoryPressure.NORMAL,
            jank = JankPressure.SMOOTH,
            isPowerSaveMode = false,
            isMeteredDataSaver = false
        )
        assertEquals(PerformanceTier.SURVIVAL, hot.tier)

        val oom = AdaptiveTierCalculator.compute(
            mode = PerformanceMode.ADAPTIVE_AUTO,
            hardware = midRangeHw,
            thermal = ThermalPressure.NOMINAL,
            memory = MemoryPressure.CRITICAL,
            jank = JankPressure.SMOOTH,
            isPowerSaveMode = false,
            isMeteredDataSaver = false
        )
        assertEquals(PerformanceTier.SURVIVAL, oom.tier)

        // Heavy frame jank steps mid-range down to Survival
        val janky = AdaptiveTierCalculator.compute(
            mode = PerformanceMode.ADAPTIVE_AUTO,
            hardware = midRangeHw,
            thermal = ThermalPressure.NOMINAL,
            memory = MemoryPressure.NORMAL,
            jank = JankPressure.HEAVY,
            isPowerSaveMode = false,
            isMeteredDataSaver = false
        )
        assertEquals(PerformanceTier.SURVIVAL, janky.tier)

        // Battery Saver caps to Power Saver
        val battery = AdaptiveTierCalculator.compute(
            mode = PerformanceMode.ADAPTIVE_AUTO,
            hardware = flagshipHw,
            thermal = ThermalPressure.NOMINAL,
            memory = MemoryPressure.NORMAL,
            jank = JankPressure.SMOOTH,
            isPowerSaveMode = true,
            isMeteredDataSaver = false
        )
        assertEquals(PerformanceTier.POWER_SAVER, battery.tier)
    }

    @Test
    fun manualModesProtectAgainstCriticalOomAndHonorEcoLegacy() {
        val maxQualityCriticalMem = AdaptiveTierCalculator.compute(
            mode = PerformanceMode.MAX_QUALITY,
            hardware = flagshipHw,
            thermal = ThermalPressure.NOMINAL,
            memory = MemoryPressure.CRITICAL,
            jank = JankPressure.SMOOTH,
            isPowerSaveMode = false,
            isMeteredDataSaver = false
        )
        assertEquals(PerformanceTier.POWER_SAVER, maxQualityCriticalMem.tier)

        val eco = AdaptiveTierCalculator.compute(
            mode = PerformanceMode.ECO_LEGACY,
            hardware = flagshipHw,
            thermal = ThermalPressure.NOMINAL,
            memory = MemoryPressure.NORMAL,
            jank = JankPressure.SMOOTH,
            isPowerSaveMode = false,
            isMeteredDataSaver = false
        )
        assertEquals(PerformanceTier.POWER_SAVER, eco.tier)
    }

    @Test
    fun codecAwareVideoSelectionAvoidsSoftwareAv1AndStepsDownByTier() {
        val videoPost = samplePost(
            ext = "mp4",
            videoVariants = listOf(
                VideoVariant("original", "Original (1080p AV1)", "https://static1.e621.net/data/orig.mp4", 1920, 1080, 12_000_000L, 60.0, "av01.0.08M.08"),
                VideoVariant("mp4", "1080p H.264", "https://static1.e621.net/data/h264.mp4", 1920, 1080, 18_000_000L, 60.0, "avc1.640028"),
                VideoVariant("720p", "720p Sample", "https://static1.e621.net/data/720p.mp4", 1280, 720, 6_000_000L, 30.0, "avc1.4D401F"),
                VideoVariant("480p", "480p Sample", "https://static1.e621.net/data/480p.mp4", 854, 480, 2_500_000L, 30.0, "avc1.4D401E")
            )
        )

        // Mid-range phone WITHOUT HW AV1 must NEVER pick AV1 original — picks HW H.264 mp4 instead!
        val midChoice = videoPost.selectOptimalVideoVariant(
            preference = VideoQualityPreference.ORIGINAL,
            hardware = midRangeHw,
            budget = AdaptiveBudget.forTier(PerformanceTier.BALANCED, false)
        )
        assertEquals("mp4", midChoice?.key)
        assertTrue(midChoice!!.isHardwareAcceleratedOn(midRangeHw))

        // Flagship phone WITH HW AV1 on Ultra + Original preference picks crisp HW AV1 original!
        val flagshipChoice = videoPost.selectOptimalVideoVariant(
            preference = VideoQualityPreference.ORIGINAL,
            hardware = flagshipHw,
            budget = AdaptiveBudget.forTier(PerformanceTier.ULTRA, false)
        )
        assertEquals("original", flagshipChoice?.key)
        assertTrue(flagshipChoice!!.isHardwareAcceleratedOn(flagshipHw))

        // Power Saver steps down to 720p HW H.264
        val powerSaverChoice = videoPost.selectOptimalVideoVariant(
            preference = VideoQualityPreference.MP4_COMPATIBLE,
            hardware = flagshipHw,
            budget = AdaptiveBudget.forTier(PerformanceTier.POWER_SAVER, false)
        )
        assertEquals("720p", powerSaverChoice?.key)

        // Survival steps down to 480p HW H.264 and sets tapToPlayAnimatedMedia = true
        val survivalBudget = AdaptiveBudget.forTier(PerformanceTier.SURVIVAL, false)
        assertTrue(survivalBudget.tapToPlayAnimatedMedia)
        val survivalChoice = videoPost.selectOptimalVideoVariant(
            preference = VideoQualityPreference.MP4_COMPATIBLE,
            hardware = legacyLowRamHw,
            budget = survivalBudget
        )
        assertEquals("480p", survivalChoice?.key)
    }

    @Test
    fun adaptiveThumbnailsFallBackTo256pxWebpUnderPressure() {
        val post = samplePost(ext = "png")

        val ultraUrl = post.bestAdaptiveThumbnailUrl(
            hdSetting = true,
            budget = AdaptiveBudget.forTier(PerformanceTier.ULTRA, false),
            isLargeCard = false
        )
        assertEquals("https://static1.e621.net/data/sample/sample.webp", ultraUrl)

        val powerSaverUrl = post.bestAdaptiveThumbnailUrl(
            hdSetting = true,
            budget = AdaptiveBudget.forTier(PerformanceTier.POWER_SAVER, false),
            isLargeCard = false
        )
        assertEquals("https://static1.e621.net/data/preview/prev.webp", powerSaverUrl)

        val survivalLargeUrl = post.bestAdaptiveThumbnailUrl(
            hdSetting = true,
            budget = AdaptiveBudget.forTier(PerformanceTier.SURVIVAL, false),
            isLargeCard = true
        )
        assertEquals("https://static1.e621.net/data/preview/prev.webp", survivalLargeUrl)

        val meteredBudget = AdaptiveBudget.forTier(PerformanceTier.ULTRA, conserveBandwidth = true)
        assertFalse(meteredBudget.allowImmediateHdFullscreen)
        assertEquals(720, meteredBudget.recommendedVideoMaxHeight)
    }

    private fun samplePost(
        ext: String,
        videoVariants: List<VideoVariant> = emptyList()
    ): Post = Post(
        id = 99999,
        createdAt = "2026-11-19T12:00:00Z",
        updatedAt = null,
        md5 = "abc123",
        ext = ext,
        fileSize = 8_000_000L,
        duration = if (ext == "mp4") 18.0 else null,
        width = 4000,
        height = 3000,
        originalUrl = "https://static1.e621.net/data/orig.$ext",
        previewUrl = "https://static1.e621.net/data/preview/prev.webp",
        previewFallbackUrl = "https://static1.e621.net/data/preview/prev.jpg",
        previewWidth = 256,
        previewHeight = 192,
        sampleUrl = "https://static1.e621.net/data/sample/sample.webp",
        sampleFallbackUrl = "https://static1.e621.net/data/sample/sample.jpg",
        sampleWidth = 850,
        sampleHeight = 638,
        hasSample = true,
        videoVariants = videoVariants,
        uploaderId = 1,
        uploaderName = "echo",
        approverId = 2,
        scoreUp = 100,
        scoreDown = 2,
        scoreTotal = 98,
        favCount = 150,
        isFavorited = false,
        userVote = 0,
        commentCount = 4,
        rating = Rating.SAFE,
        isPending = false,
        isFlagged = false,
        isDeleted = false,
        hasNotes = false,
        parentId = null,
        childrenIds = emptyList(),
        hasChildren = false,
        pools = emptyList(),
        sources = emptyList(),
        description = "",
        tags = PostTags(artist = listOf("test_artist"), general = listOf("wolf"))
    )
}
