package com.ugf.yiffur1

import com.ugf.yiffur1.data.api.CommentDto
import com.ugf.yiffur1.data.api.ExtendedPostDto
import com.ugf.yiffur1.data.api.FullCurrentUserDto
import com.ugf.yiffur1.data.api.NoteDto
import com.ugf.yiffur1.data.api.PoolDto
import com.ugf.yiffur1.data.api.TagAutocompleteDto
import com.ugf.yiffur1.data.api.toDomain
import com.ugf.yiffur1.domain.model.MediaType
import com.ugf.yiffur1.domain.model.Rating
import com.ugf.yiffur1.domain.model.TagCategory
import com.ugf.yiffur1.domain.model.VideoQualityPreference
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class E621ApiV2DeserializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun `parses Nov 2026 extended image post payload`() {
        val raw = """
            [
              {
                "id": 6869175,
                "created_at": "2026-11-19T14:48:13.832-05:00",
                "updated_at": "2026-11-19T14:51:00.280-05:00",
                "change_seq": 83758774,
                "files": {
                  "meta": {"md5": "34d8551213c3e12d2bce7c5b82b67522", "ext": "png", "size": 1636588, "duration": null, "has_sample": true},
                  "original": {"width": 2000, "height": 1359, "url": "https://static1.e926.net/data/34/d8/34d8551213c3e12d2bce7c5b82b67522.png"},
                  "preview": {"width": 377, "height": 256, "jpg": "https://static1.e926.net/data/preview/34/d8/34d8551213c3e12d2bce7c5b82b67522.jpg", "webp": "https://static1.e926.net/data/preview/34/d8/34d8551213c3e12d2bce7c5b82b67522.webp"},
                  "sample": {"width": 1251, "height": 850, "jpg": "https://static1.e926.net/data/sample/34/d8/34d8551213c3e12d2bce7c5b82b67522.jpg", "webp": "https://static1.e926.net/data/sample/34/d8/34d8551213c3e12d2bce7c5b82b67522.webp"}
                },
                "uploader_id": 2840429,
                "uploader_name": "andraspolyporous",
                "approver_id": null,
                "stats": {"score": {"up": 10, "down": -2, "total": 8}, "fav_count": 15, "is_favorited": true, "vote": 1, "comment_count": 3, "hotness": 4155.36},
                "flags": {"pending": true, "flagged": false, "note_locked": false, "status_locked": false, "rating_locked": false, "deleted": false},
                "has": {"parent": false, "children": true, "active_children": true, "notes": true, "sample": true},
                "relationships": {"parent_id": null, "children": [6869176]},
                "pools": [60555],
                "rating": "s",
                "locked_tags": [],
                "sources": ["https://www.furaffinity.net/view/67088431/"],
                "description": "Test desc",
                "tags": {
                  "general": ["anthro", "dragon"],
                  "artist": ["andraspolyporous"],
                  "contributor": ["voice_actor_x"],
                  "copyright": ["mythology"],
                  "character": [],
                  "species": ["dragon", "scalie"],
                  "invalid": [],
                  "meta": ["2026", "hi_res"],
                  "lore": []
                }
              }
            ]
        """.trimIndent()

        val posts = json.decodeFromString<List<ExtendedPostDto>>(raw).map { it.toDomain() }
        assertEquals(1, posts.size)
        val post = posts.first()
        assertEquals(6869175, post.id)
        assertEquals(MediaType.IMAGE, post.mediaType)
        assertEquals(Rating.SAFE, post.rating)
        assertEquals("andraspolyporous", post.uploaderName)
        assertEquals(8, post.scoreTotal)
        assertEquals(1, post.userVote)
        assertTrue(post.isFavorited)
        assertTrue(post.hasNotes)
        assertTrue(post.hasChildren)
        assertEquals(listOf(60555), post.pools)
        assertEquals(listOf("voice_actor_x"), post.tags.contributor)
        assertEquals(
            "https://static1.e926.net/data/sample/34/d8/34d8551213c3e12d2bce7c5b82b67522.webp",
            post.bestThumbnailUrl(highQuality = true)
        )
        assertEquals(
            "https://static1.e926.net/data/preview/34/d8/34d8551213c3e12d2bce7c5b82b67522.webp",
            post.bestThumbnailUrl(highQuality = false)
        )
    }

    @Test
    fun `parses Nov 2026 video post with variants and samples`() {
        val raw = """
            {
              "id": 6869128,
              "created_at": "2026-11-19T14:21:44.068-05:00",
              "files": {
                "meta": {"md5": "b1578f0d924a7fae0738ce3c059fe2b6", "ext": "mp4", "size": 1597187, "duration": 12.7735, "has_sample": true},
                "original": {"width": 1080, "height": 1920, "url": "https://static1.e926.net/data/b1/57/b1578f0d924a7fae0738ce3c059fe2b6.mp4"},
                "preview": {"width": 256, "height": 455, "jpg": "https://static1.e926.net/data/preview/b1/57/test.jpg", "webp": "https://static1.e926.net/data/preview/b1/57/test.webp"},
                "sample": {"width": 850, "height": 1511, "jpg": "https://static1.e926.net/data/sample/b1/57/test.jpg", "webp": "https://static1.e926.net/data/sample/b1/57/test.webp"},
                "video": {
                  "has": true,
                  "original": {"fps": 24.0, "codec": "av01.0.00M.08", "size": 1597187, "width": 1080, "height": 1920, "url": "https://static1.e926.net/data/b1/57/orig.mp4"},
                  "variants": {"mp4": {"codec": "avc1.4D401E", "fps": 24.0, "size": 1471307, "width": 1080, "height": 1920, "url": "https://static1.e926.net/data/sample/b1/57/alt.mp4"}},
                  "samples": {
                    "480p": {"fps": 24.0, "size": 724036, "codec": "avc1.4D401E", "width": 480, "height": 854, "url": "https://static1.e926.net/data/sample/b1/57/480p.mp4"},
                    "720p": {"fps": 24.0, "size": 1005106, "codec": "avc1.4D401E", "width": 720, "height": 1280, "url": "https://static1.e926.net/data/sample/b1/57/720p.mp4"}
                  }
                }
              },
              "uploader_id": 43919,
              "uploader_name": "PhilosophicalMind",
              "stats": {"score": {"up": 1, "down": -2, "total": -1}, "fav_count": 0, "is_favorited": false, "vote": 0, "comment_count": 0},
              "flags": {"pending": true, "flagged": false, "note_locked": false, "status_locked": false, "rating_locked": false, "deleted": false},
              "has": {"parent": false, "children": false, "active_children": false, "notes": false, "sample": true},
              "relationships": {"parent_id": null, "children": []},
              "pools": [],
              "rating": "s",
              "sources": [],
              "description": "",
              "tags": {"general": ["fox"], "meta": ["sound", "video"]}
            }
        """.trimIndent()

        val post = json.decodeFromString<ExtendedPostDto>(raw).toDomain()
        assertEquals(MediaType.VIDEO, post.mediaType)
        assertTrue(post.hasSound)
        assertEquals(4, post.videoVariants.size)
        assertEquals("https://static1.e926.net/data/sample/b1/57/alt.mp4", post.bestVideoVariant(VideoQualityPreference.MP4_COMPATIBLE)?.url)
        assertEquals("https://static1.e926.net/data/sample/b1/57/720p.mp4", post.bestVideoVariant(VideoQualityPreference.SD_720P)?.url)
        assertEquals("https://static1.e926.net/data/sample/b1/57/480p.mp4", post.bestVideoVariant(VideoQualityPreference.LOW_480P)?.url)
        assertEquals("https://static1.e926.net/data/b1/57/orig.mp4", post.bestVideoVariant(VideoQualityPreference.ORIGINAL)?.url)
    }

    @Test
    fun `handles deleted or hidden post with null URLs cleanly`() {
        val raw = """
            {
              "id": 6868475,
              "files": {
                "meta": {"md5": "baa5f12650cb1ba088230c8b277fe312", "ext": "png", "size": 2478089, "duration": null, "has_sample": true},
                "original": {"width": 1000, "height": 1400, "url": null},
                "preview": {"width": 256, "height": 358, "jpg": null, "webp": null},
                "sample": {"width": 850, "height": 1190, "jpg": null, "webp": null}
              },
              "flags": {"deleted": true},
              "rating": "s"
            }
        """.trimIndent()

        val post = json.decodeFromString<ExtendedPostDto>(raw).toDomain()
        assertTrue(post.isHiddenOrDeleted)
        assertNull(post.originalUrl)
        assertNull(post.bestThumbnailUrl(true))
    }

    @Test
    fun `parses autocomplete, pool, comment, note, and current user payloads`() {
        val acJson = """[{"id":660640,"name":"canid","post_count":1750704,"category":5,"antecedent_name":"canind"}]"""
        val ac = json.decodeFromString<List<TagAutocompleteDto>>(acJson).first().toDomain()
        assertEquals("canid", ac.name)
        assertEquals(TagCategory.SPECIES, ac.category)
        assertEquals("canind", ac.antecedentName)

        val poolJson = """{"id":60555,"name":"Popular_Demand","creator_name":"RoqueLobster","category":"series","post_ids":[1,2,3],"post_count":3}"""
        val pool = json.decodeFromString<PoolDto>(poolJson).toDomain()
        assertEquals("Popular Demand", pool.displayName)
        assertEquals(listOf(1, 2, 3), pool.postIds)

        val commentJson = """{"id":10189798,"post_id":6856742,"creator_id":1116899,"creator_name":"MuddyLittleBippy","body":"love this","score":5,"vote":1}"""
        val comment = json.decodeFromString<CommentDto>(commentJson).toDomain()
        assertEquals(1, comment.userVote)
        assertEquals("MuddyLittleBippy", comment.creatorName)

        val noteJson = """{"id":512935,"post_id":6869149,"x":86,"y":41,"width":271,"height":48,"is_active":true,"body":"Prioritize","creator_name":"GlitchedSystem"}"""
        val note = json.decodeFromString<NoteDto>(noteJson).toDomain()
        assertEquals("Prioritize", note.body)
        assertEquals(86, note.x)

        val anonJson = """{"id":null,"name":"Anonymous","level":0,"level_string":"Anonymous","tag_query_limit":40,"blacklisted_tags":"gore\nyoung -rating:s"}"""
        val anon = json.decodeFromString<FullCurrentUserDto>(anonJson).toDomain()
        assertFalse(anon.isAuthenticated)
        assertEquals(40, anon.tagQueryLimit)
        assertNotNull(anon.blacklistedTagsRaw)
    }
}
