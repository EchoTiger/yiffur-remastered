package com.ugf.yiffur1

import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.PostTags
import com.ugf.yiffur1.domain.model.Rating
import com.ugf.yiffur1.ui.viewer.CollectionUiState
import com.ugf.yiffur1.ui.viewer.ViewerSessionStore
import com.ugf.yiffur1.ui.viewer.putBoundedLru
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerSessionStoreTest {

    private fun fakePost(id: Int, isFavorited: Boolean = false): Post = Post(
        id = id,
        createdAt = "2026-11-21T00:00:00Z",
        updatedAt = null,
        md5 = "md5_$id",
        ext = "jpg",
        fileSize = 512_000L,
        duration = null,
        width = 1200,
        height = 900,
        originalUrl = "https://static1.e621.net/data/$id.jpg",
        previewUrl = "https://static1.e621.net/data/preview/$id.webp",
        previewFallbackUrl = null,
        previewWidth = 256,
        previewHeight = 192,
        sampleUrl = "https://static1.e621.net/data/sample/$id.webp",
        sampleFallbackUrl = null,
        sampleWidth = 850,
        sampleHeight = 638,
        hasSample = true,
        videoVariants = emptyList(),
        uploaderId = 1,
        uploaderName = "echo",
        approverId = 2,
        scoreUp = 10,
        scoreDown = 0,
        scoreTotal = 10,
        favCount = 5,
        isFavorited = isFavorited,
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
        tags = PostTags(general = listOf("fox"))
    )

    @Test
    fun nestedParentChildrenBackNavigationPreservesOriginalSessionWithoutCrash() {
        val store = ViewerSessionStore()
        val mainFeed = (1..75).map { fakePost(1000 + it) }
        val familyPosts = listOf(fakePost(1036), fakePost(9001), fakePost(9002))

        // 1. Open main Browse feed at index 35 (post #1036)
        val mainViewerId = store.openPostsSession(mainFeed, startIndex = 35, sourceFeedKey = "browse:1")

        // 2. Open "View Parent / Children" collection
        val familyCollectionId = store.openLoadingCollectionSession()
        store.resolveCollectionSession(
            familyCollectionId,
            CollectionUiState(title = "Related Posts", posts = familyPosts, isLoading = false)
        )

        // 3. Open child post #9001 from the family collection (index 1 of 3)
        val childViewerId = store.openPostsSession(familyPosts, startIndex = 1, sourceFeedKey = "collection:$familyCollectionId")

        // 4. First Back: closes child viewer session
        store.closeViewerSession(childViewerId)
        assertNull(store.viewerSessions.value[childViewerId])
        assertEquals(3, store.collectionSessions.value[familyCollectionId]?.posts?.size)

        // 5. Second Back: closes family collection -> resumes main viewer session at page 35 of 75!
        store.closeCollectionSession(familyCollectionId)
        assertNull(store.collectionSessions.value[familyCollectionId])

        val resumed = store.viewerSessions.value[mainViewerId]!!
        assertEquals(75, resumed.posts.size)
        assertEquals(35, resumed.currentIndex)
        assertEquals(1036, resumed.posts[resumed.currentIndex].id)
    }

    @Test
    fun scrollSyncEmitsOnlyAfterSwipingAndConsumesOnce() {
        val store = ViewerSessionStore()
        val feed = (1..40).map { fakePost(2000 + it) }

        val sessionId = store.openPostsSession(feed, startIndex = 2, sourceFeedKey = "browse:7")

        // Opening and staying on the initial post should NOT force a gallery jump
        store.reportViewerPosition(sessionId, pageIndex = 2, postId = 2003)
        assertNull(store.scrollSyncTargets.value["browse:7"])

        // Swiping 25 posts forward records the target post ID for gallery scroll sync
        store.reportViewerPosition(sessionId, pageIndex = 27, postId = 2028)
        assertEquals(2028, store.scrollSyncTargets.value["browse:7"])

        // Consuming clears the one-shot target cleanly
        store.consumeScrollSyncTarget("browse:7")
        assertNull(store.scrollSyncTargets.value["browse:7"])
    }

    @Test
    fun boundedLruCacheCapsMemoryAndEvictsOldestEntries() {
        var map = emptyMap<Int, String>()
        for (i in 1..40) {
            map = putBoundedLru(map, i, "val_$i", maxEntries = 24)
        }
        assertEquals(24, map.size)
        assertFalse(1 in map)
        assertFalse(16 in map)
        assertTrue(17 in map)
        assertTrue(40 in map)

        // Touching key 17 refreshes its recency so key 18 gets evicted next
        map = putBoundedLru(map, 17, "updated_17", maxEntries = 24)
        map = putBoundedLru(map, 41, "val_41", maxEntries = 24)
        assertEquals(24, map.size)
        assertTrue(17 in map)
        assertEquals("updated_17", map[17])
        assertFalse(18 in map)
    }

    @Test
    fun postUpdatesPropagateAcrossAllActiveNestedSessions() {
        val store = ViewerSessionStore()
        val post = fakePost(555, isFavorited = false)
        val s1 = store.openPostsSession(listOf(post, fakePost(556)), startIndex = 0)
        val c1 = store.openLoadingCollectionSession()
        store.resolveCollectionSession(c1, CollectionUiState(posts = listOf(post)))

        store.updatePostAcrossSessions(post.copy(isFavorited = true, favCount = 6))

        assertTrue(store.viewerSessions.value[s1]!!.posts.first().isFavorited)
        assertEquals(6, store.collectionSessions.value[c1]!!.posts.first().favCount)
    }
}
