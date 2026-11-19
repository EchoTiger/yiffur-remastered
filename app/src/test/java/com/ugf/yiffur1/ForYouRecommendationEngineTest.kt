package com.ugf.yiffur1

import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.PostTags
import com.ugf.yiffur1.domain.model.Rating
import com.ugf.yiffur1.domain.recommend.DiscoveryMixMode
import com.ugf.yiffur1.domain.recommend.ForYouRecommendationEngine
import com.ugf.yiffur1.domain.recommend.SeenPostSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForYouRecommendationEngineTest {

    private fun post(
        id: Int,
        artist: List<String> = emptyList(),
        species: List<String> = emptyList(),
        general: List<String> = emptyList(),
        score: Int = 50,
        favs: Int = 40
    ) = Post(
        id = id,
        createdAt = "2026-11-19T12:00:00Z",
        updatedAt = null,
        md5 = "m$id",
        ext = "png",
        fileSize = 500_000L,
        duration = null,
        width = 1200,
        height = 1200,
        originalUrl = "https://static1.e621.net/data/$id.png",
        previewUrl = "https://static1.e621.net/data/preview/$id.webp",
        previewFallbackUrl = null,
        previewWidth = 256,
        previewHeight = 256,
        sampleUrl = "https://static1.e621.net/data/sample/$id.webp",
        sampleFallbackUrl = null,
        sampleWidth = 850,
        sampleHeight = 850,
        hasSample = true,
        uploaderId = 1,
        uploaderName = "echo",
        approverId = null,
        scoreUp = score,
        scoreDown = 0,
        scoreTotal = score,
        favCount = favs,
        isFavorited = false,
        userVote = 0,
        commentCount = 0,
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
        tags = PostTags(artist = artist, species = species, general = general)
    )

    @Test
    fun tasteProfileExtractsWeightedPreferencesAndFiltersStopMutedAndBlacklistedTags() {
        val favs = listOf(
            post(1, artist = listOf("cool_artist"), species = listOf("cyber_fox"), general = listOf("anthro", "hi_res", "neon_lights", "male")),
            post(2, artist = listOf("cool_artist"), species = listOf("cyber_fox"), general = listOf("cyberpunk", "solo", "2026"))
        )

        val profile = ForYouRecommendationEngine.buildTasteProfile(
            localFavorites = favs,
            savedSearchQueries = listOf("synthwave -gore order:score"),
            recentSearchQueries = listOf("space_wolf rating:s"),
            mutedTags = setOf("neon_lights"),
            singleBlacklistedTags = setOf("gore")
        )

        assertTrue(profile.isPersonalized)
        val topNames = profile.topTags.map { it.tag }.toSet()

        assertTrue("cool_artist" in topNames)
        assertTrue("cyber_fox" in topNames)
        assertTrue("synthwave" in topNames)
        assertTrue("space_wolf" in topNames)

        // Generic boilerplate, muted tags, negated query tags, and metatags must NEVER leak in
        assertFalse("anthro" in topNames)
        assertFalse("hi_res" in topNames)
        assertFalse("2026" in topNames)
        assertFalse("neon_lights" in topNames)
        assertFalse("gore" in topNames)
        assertFalse("order:score" in topNames)
        assertFalse("rating:s" in topNames)
    }

    @Test
    fun queryPlannerHandlesBothPersonalizedAndColdStartUsers() {
        val coldProfile = ForYouRecommendationEngine.buildTasteProfile(localFavorites = emptyList())
        assertFalse(coldProfile.isPersonalized)
        val coldQueries = ForYouRecommendationEngine.buildDiscoveryQueries(
            profile = coldProfile,
            mixMode = DiscoveryMixMode.BALANCED_MIX,
            randomSeed = 42L
        )
        assertTrue(coldQueries.size >= 2)
        assertTrue(coldQueries.any { it.tags.contains("order:hot") })

        val warmProfile = ForYouRecommendationEngine.buildTasteProfile(
            localFavorites = listOf(post(10, artist = listOf("star_painter"), species = listOf("dragon"), general = listOf("magic"))),
            savedSearchQueries = listOf("dragon starlight")
        )
        val warmQueries = ForYouRecommendationEngine.buildDiscoveryQueries(
            profile = warmProfile,
            mixMode = DiscoveryMixMode.BALANCED_MIX,
            randomSeed = 42L
        )
        assertTrue(warmQueries.size >= 3)
        assertTrue(warmQueries.any { it.tags.contains("dragon") || it.tags.contains("starlight") || it.tags.contains("star_painter") })
    }

    @Test
    fun rankerPrioritizesUnseenPreferredPostsSuppressesDismissedAndDiversifiesArtists() {
        val profile = ForYouRecommendationEngine.buildTasteProfile(
            localFavorites = listOf(
                post(1, artist = listOf("fav_artist"), species = listOf("wolf"), general = listOf("aurora")),
                post(2, artist = listOf("other_artist"), species = listOf("wolf"), general = listOf("aurora"))
            )
        )

        val candidates = listOf(
            post(101, artist = listOf("fav_artist"), species = listOf("wolf"), general = listOf("aurora"), score = 100),
            post(102, artist = listOf("fav_artist"), species = listOf("wolf"), general = listOf("aurora"), score = 99),
            post(103, artist = listOf("second_artist"), species = listOf("wolf"), general = listOf("aurora"), score = 95),
            post(104, artist = listOf("third_artist"), species = listOf("wolf"), general = listOf("aurora"), score = 120), // dismissed
            post(105, artist = listOf("fourth_artist"), species = listOf("wolf"), general = listOf("aurora"), score = 110) // already seen
        )

        val now = 1_700_000_000_000L
        val seen = mapOf(
            104 to SeenPostSignal(104, now - 60_000L, engagementScore = -5),
            105 to SeenPostSignal(105, now - 60_000L, engagementScore = 1)
        )

        // In Unseen Only mode, both dismissed (#104) and viewed (#105) must be excluded
        val unseenOnly = ForYouRecommendationEngine.rankAndDiversify(
            candidates = candidates,
            profile = profile,
            seenSignals = seen,
            favoritePostIds = emptySet(),
            mixMode = DiscoveryMixMode.UNSEEN_ONLY,
            alreadyDisplayedIds = emptySet(),
            nowMs = now,
            randomSeed = 7L
        )
        val ids = unseenOnly.map { it.id }
        assertFalse(104 in ids)
        assertFalse(105 in ids)
        assertEquals(3, ids.size)

        // Artist diversity: two posts from `fav_artist` (#101 and #102) should NOT appear back-to-back ahead of `second_artist` (#103)
        assertEquals("fav_artist", unseenOnly[0].tags.artist.first())
        assertEquals("second_artist", unseenOnly[1].tags.artist.first())

        // In Balanced Mix mode, dismissed (#104) is STILL excluded, while seen (#105) is allowed to appear ranked below fresh ones
        val balanced = ForYouRecommendationEngine.rankAndDiversify(
            candidates = candidates,
            profile = profile,
            seenSignals = seen,
            favoritePostIds = emptySet(),
            mixMode = DiscoveryMixMode.BALANCED_MIX,
            alreadyDisplayedIds = emptySet(),
            nowMs = now,
            randomSeed = 7L
        )
        assertFalse(104 in balanced.map { it.id })
        assertTrue(105 in balanced.map { it.id })
        assertEquals(105, balanced.last().id)
    }
}
