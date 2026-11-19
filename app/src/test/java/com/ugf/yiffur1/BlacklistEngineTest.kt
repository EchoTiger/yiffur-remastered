package com.ugf.yiffur1

import com.ugf.yiffur1.domain.blacklist.BlacklistEngine
import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.PostTags
import com.ugf.yiffur1.domain.model.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlacklistEngineTest {

    private fun samplePost(
        id: Int = 100,
        rating: Rating = Rating.EXPLICIT,
        ext: String = "png",
        score: Int = 25,
        uploader: String = "Echo",
        pools: List<Int> = emptyList(),
        general: List<String> = emptyList(),
        species: List<String> = listOf("fox")
    ) = Post(
        id = id,
        createdAt = "",
        updatedAt = null,
        md5 = "abc",
        ext = ext,
        fileSize = 1000L,
        duration = null,
        width = 800,
        height = 600,
        originalUrl = "https://example.com/1.png",
        previewUrl = "https://example.com/p.webp",
        previewFallbackUrl = null,
        previewWidth = 256,
        previewHeight = 192,
        sampleUrl = null,
        sampleFallbackUrl = null,
        sampleWidth = 0,
        sampleHeight = 0,
        hasSample = false,
        uploaderId = 1,
        uploaderName = uploader,
        approverId = null,
        scoreUp = score,
        scoreDown = 0,
        scoreTotal = score,
        favCount = 10,
        isFavorited = false,
        userVote = 0,
        commentCount = 0,
        rating = rating,
        isPending = false,
        isFlagged = false,
        isDeleted = false,
        hasNotes = false,
        parentId = null,
        childrenIds = emptyList(),
        hasChildren = false,
        pools = pools,
        sources = emptyList(),
        description = "",
        tags = PostTags(general = general, species = species)
    )

    @Test
    fun `parses e621 default blacklist block ignoring comments and blank lines`() {
        val raw = """
            # Violence
            gore
            snuff

            # Compound
            young -rating:s
            realistic_feral rating:e
        """.trimIndent()
        val lines = BlacklistEngine.parseLines(raw)
        assertEquals(listOf("gore", "snuff", "young -rating:s", "realistic_feral rating:e"), lines)
    }

    @Test
    fun `evaluates compound rules, negations, wildcards, OR terms, and metatags`() {
        val rules = BlacklistEngine.compile(
            listOf(
                "gore",
                "young -rating:s",
                "*_vore",
                "score:<0",
                "type:webm",
                "user:spammer",
                "~bad_a ~bad_b"
            )
        )

        // Single tag
        assertTrue(BlacklistEngine.isBlacklisted(samplePost(general = listOf("gore")), rules))
        assertFalse(BlacklistEngine.isBlacklisted(samplePost(general = listOf("smile")), rules))

        // Compound with negation: young allowed when rating:s, blocked when rating:e or rating:q
        assertFalse(BlacklistEngine.isBlacklisted(samplePost(rating = Rating.SAFE, general = listOf("young")), rules))
        assertTrue(BlacklistEngine.isBlacklisted(samplePost(rating = Rating.EXPLICIT, general = listOf("young")), rules))
        assertTrue(BlacklistEngine.isBlacklisted(samplePost(rating = Rating.QUESTIONABLE, general = listOf("young")), rules))

        // Wildcard
        assertTrue(BlacklistEngine.isBlacklisted(samplePost(general = listOf("soft_vore")), rules))

        // Score metatag
        assertTrue(BlacklistEngine.isBlacklisted(samplePost(score = -5), rules))
        assertFalse(BlacklistEngine.isBlacklisted(samplePost(score = 0), rules))

        // Type & uploader
        assertTrue(BlacklistEngine.isBlacklisted(samplePost(ext = "webm"), rules))
        assertTrue(BlacklistEngine.isBlacklisted(samplePost(uploader = "Spammer"), rules))

        // OR group
        assertTrue(BlacklistEngine.isBlacklisted(samplePost(general = listOf("bad_b")), rules))
    }
}
