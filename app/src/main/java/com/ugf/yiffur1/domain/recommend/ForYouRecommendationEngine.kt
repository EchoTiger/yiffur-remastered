package com.ugf.yiffur1.domain.recommend

import com.ugf.yiffur1.domain.model.Post
import com.ugf.yiffur1.domain.model.TagCategory
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random

enum class DiscoveryMixMode(val label: String, val description: String) {
    BALANCED_MIX(
        label = "Balanced Mix",
        description = "Mostly fresh unseen discoveries with occasional familiar gems"
    ),
    UNSEEN_ONLY(
        label = "Unseen Only",
        description = "Strictly hides posts you have already viewed or favorited"
    ),
    WILDCARDS(
        label = "Wildcards",
        description = "Explores adjacent tags, lesser-known artists & surprise high-craft picks"
    )
}

data class TasteTag(
    val tag: String,
    val score: Double,
    val sourceLabel: String, // e.g. "Favorites", "Searches", "Saved"
    val categoryHint: TagCategory? = null
)

data class TasteProfile(
    val topTags: List<TasteTag> = emptyList(),
    val topArtists: List<TasteTag> = emptyList(),
    val topCharacters: List<TasteTag> = emptyList(),
    val topSpecies: List<TasteTag> = emptyList(),
    val tagWeights: Map<String, Double> = emptyMap(),
    val signalCount: Int = 0
) {
    val isPersonalized: Boolean
        get() = topTags.isNotEmpty()
}

data class DiscoveryQuery(
    val tags: String,
    val page: String = "1",
    val limit: Int = 35,
    val bucketLabel: String
)

data class SeenPostSignal(
    val postId: Int,
    val seenAtMs: Long,
    val engagementScore: Int // 1 = viewed, 3 = liked/favorited, -5 = dismissed
)

/**
 * 100% on-device, privacy-sovereign recommendation engine powering Explore → "For You".
 *
 * Builds a weighted taste vector from local/remote favorites, saved searches, and search history,
 * generates diverse e621/e926 discovery queries (respecting active blacklist & API tag limits),
 * and ranks candidates with novelty/seen balancing and artist diversity smoothing.
 */
object ForYouRecommendationEngine {

    /**
     * Ultra-generic structural/meta boilerplate tags excluded from driving recommendations so the
     * For You feed discovers specific themes, species, characters, and artists instead of generic "anthro".
     */
    private val STOP_TAGS: Set<String> = setOf(
        "anthro", "mammal", "canid", "felid", "fur", "male", "female", "solo", "duo", "group",
        "clothed", "clothing", "nude", "standing", "sitting", "lying", "looking_at_viewer",
        "simple_background", "white_background", "detailed_background", "outside", "inside",
        "hi_res", "absurd_res", "digital_media_(artwork)", "digital_drawing_(artwork)",
        "digital_painting_(artwork)", "2d", "english_text", "text", "dialogue", "speech_bubble",
        "shaded", "colored", "sketch", "line_art", "signature", "watermark", "url", "portrait",
        "full-length_portrait", "half-length_portrait", "bust_portrait", "smile", "smiling",
        "open_mouth", "blush", "eyes_closed", "blue_eyes", "green_eyes", "brown_eyes", "yellow_eyes",
        "hair", "short_hair", "long_hair", "tuft", "tail", "ears", "paws", "claws", "biped",
        "humanoid", "ambiguous_gender", "intersex", "conditional_dnp", "avoid_posting",
        "sound_warning", "sound", "animated", "loop", "video", "short_playtime", "long_playtime",
        "2018", "2019", "2020", "2021", "2022", "2023", "2024", "2025", "2026", "2027"
    )

    private val GENERIC_ARTISTS: Set<String> = setOf(
        "unknown_artist", "anonymous_artist", "third-party_edit", "avoid_posting", "conditional_dnp", "sound_warning"
    )

    fun buildTasteProfile(
        localFavorites: List<Post>,
        remoteFavorites: List<Post> = emptyList(),
        savedSearchQueries: List<String> = emptyList(),
        recentSearchQueries: List<String> = emptyList(),
        mutedTags: Set<String> = emptySet(),
        singleBlacklistedTags: Set<String> = emptySet(),
        favoriteTagsRaw: String = ""
    ): TasteProfile {
        val blocked = buildSet {
            mutedTags.forEach { add(it.trim().lowercase()) }
            singleBlacklistedTags.forEach { add(it.trim().lowercase()) }
        }

        val scores = HashMap<String, Double>()
        val sourceMap = HashMap<String, String>()
        val categoryMap = HashMap<String, TagCategory>()
        var signals = 0

        fun addWeight(rawTag: String, weight: Double, source: String, category: TagCategory? = null) {
            val tag = rawTag.trim().lowercase()
            if (tag.length < 2) return
            if (tag.contains(':') || tag.startsWith("-") || tag.startsWith("~") || tag.contains('*')) return
            if (tag in blocked || tag in STOP_TAGS) return
            if (category == TagCategory.ARTIST && tag in GENERIC_ARTISTS) return
            if (tag.all { it.isDigit() }) return

            scores[tag] = (scores[tag] ?: 0.0) + weight
            if (category != null && tag !in categoryMap) {
                categoryMap[tag] = category
            }
            if (tag !in sourceMap) {
                sourceMap[tag] = source
            }
        }

        // 1. Saved searches (explicit high-intent interests)
        savedSearchQueries.forEach { query ->
            val tokens = extractPositiveQueryTags(query)
            if (tokens.isNotEmpty()) signals++
            tokens.forEach { addWeight(it, 4.8, "Saved") }
        }

        // 2. e621 account favorite_tags (if logged in)
        if (favoriteTagsRaw.isNotBlank()) {
            extractPositiveQueryTags(favoriteTagsRaw).forEach {
                signals++
                addWeight(it, 4.5, "Account")
            }
        }

        // 3. Search history (recency-weighted, newest first)
        recentSearchQueries.take(35).forEachIndexed { idx, query ->
            val decay = 1.0 / (1.0 + idx * 0.08)
            val tokens = extractPositiveQueryTags(query)
            if (tokens.isNotEmpty()) signals++
            tokens.forEach { addWeight(it, 3.0 * decay, "Searches") }
        }

        // 4. Local + remote favorites (deduplicated by ID, newest weighted higher)
        val combinedFavs = (localFavorites + remoteFavorites).distinctBy { it.id }.take(120)
        combinedFavs.forEachIndexed { idx, post ->
            signals++
            val recency = 1.0 / (1.0 + idx * 0.025)
            post.tags.artist.forEach { addWeight(it, 3.8 * recency, "Favorites", TagCategory.ARTIST) }
            post.tags.character.forEach { addWeight(it, 3.2 * recency, "Favorites", TagCategory.CHARACTER) }
            post.tags.species.forEach { addWeight(it, 2.6 * recency, "Favorites", TagCategory.SPECIES) }
            post.tags.copyright.forEach { addWeight(it, 2.2 * recency, "Favorites", TagCategory.COPYRIGHT) }
            post.tags.lore.forEach { addWeight(it, 1.8 * recency, "Favorites", TagCategory.LORE) }
            // Cap general tags contribution per post so over-tagged posts don't drown out focused ones
            post.tags.general.take(25).forEach { addWeight(it, 1.15 * recency, "Favorites", TagCategory.GENERAL) }
        }

        val sorted = scores.entries
            .sortedByDescending { it.value }
            .map { (tag, score) ->
                TasteTag(
                    tag = tag,
                    score = score,
                    sourceLabel = sourceMap[tag] ?: "Preferences",
                    categoryHint = categoryMap[tag]
                )
            }

        val topArtists = sorted.filter { it.categoryHint == TagCategory.ARTIST }.take(12)
        val topCharacters = sorted.filter { it.categoryHint == TagCategory.CHARACTER }.take(12)
        val topSpecies = sorted.filter { it.categoryHint == TagCategory.SPECIES }.take(12)
        val topGeneral = sorted.take(28)

        return TasteProfile(
            topTags = topGeneral,
            topArtists = topArtists,
            topCharacters = topCharacters,
            topSpecies = topSpecies,
            tagWeights = scores,
            signalCount = signals
        )
    }

    /**
     * Plans 3–4 e621/e926 API queries per discovery batch (staying well within e621's tag limit and
     * 2 req/sec rate limit) to synthesize a fresh mix of targeted taste picks and serendipity.
     */
    fun buildDiscoveryQueries(
        profile: TasteProfile,
        mixMode: DiscoveryMixMode,
        focusedTag: String? = null,
        batchIndex: Int = 0,
        randomSeed: Long = System.currentTimeMillis()
    ): List<DiscoveryQuery> {
        val rng = Random(randomSeed + batchIndex * 7919L)

        // Focused single-tag drill-down inside For You
        val cleanFocus = focusedTag?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (cleanFocus != null) {
            val pageNum = (batchIndex + 1).toString()
            return listOf(
                DiscoveryQuery(
                    tags = "$cleanFocus order:random score:>15",
                    page = "1",
                    limit = 35,
                    bucketLabel = "Focused: $cleanFocus"
                ),
                DiscoveryQuery(
                    tags = "$cleanFocus order:score",
                    page = pageNum,
                    limit = 30,
                    bucketLabel = "Top $cleanFocus"
                )
            )
        }

        // Cold-start experience when the user hasn't searched or favorited anything yet
        if (!profile.isPersonalized) {
            val pageNum = (batchIndex + 1).toString()
            return listOf(
                DiscoveryQuery(
                    tags = "order:hot",
                    page = pageNum,
                    limit = 35,
                    bucketLabel = "Trending"
                ),
                DiscoveryQuery(
                    tags = "order:random score:>60",
                    page = "1",
                    limit = 35,
                    bucketLabel = "High-Score Gems"
                ),
                DiscoveryQuery(
                    tags = "order:favcount",
                    page = ((batchIndex % 5) + 2).toString(),
                    limit = 25,
                    bucketLabel = "Community Favorites"
                )
            )
        }

        val queries = mutableListOf<DiscoveryQuery>()
        val allPool = profile.topTags.take(20)

        // Bucket 1: Core Taste Blend (weighted OR of 2–3 strong tags + random discovery)
        val corePicks = pickWeightedDistinct(allPool, count = 3, rng = rng)
        if (corePicks.isNotEmpty()) {
            val orClause = corePicks.joinToString(" ") { "~${it.tag}" }
            val minScore = if (mixMode == DiscoveryMixMode.WILDCARDS) 10 else 20
            queries += DiscoveryQuery(
                tags = "$orClause score:>$minScore order:random",
                page = "1",
                limit = 40,
                bucketLabel = "For You Blend"
            )
        }

        // Bucket 2: Artist / Character Spotlight (surfacing works the user might have missed!)
        val creatorPool = (profile.topArtists + profile.topCharacters).distinctBy { it.tag }
        if (creatorPool.isNotEmpty()) {
            val picks = pickWeightedDistinct(creatorPool, count = 2, rng = rng)
            val queryStr = if (picks.size == 1) {
                "${picks[0].tag} order:random"
            } else {
                picks.joinToString(" ") { "~${it.tag}" } + " order:random"
            }
            queries += DiscoveryQuery(
                tags = queryStr,
                page = "1",
                limit = 30,
                bucketLabel = "Favorite Creators & Characters"
            )
        }

        // Bucket 3: Deep-Cut / Adjacent Serendipity
        val serendipityPool = when (mixMode) {
            DiscoveryMixMode.WILDCARDS -> profile.topTags.drop(3).ifEmpty { allPool }
            else -> (profile.topSpecies + profile.topTags.drop(2)).distinctBy { it.tag }.ifEmpty { allPool }
        }
        val anchor = pickWeightedDistinct(serendipityPool, count = 1, rng = rng).firstOrNull()
        if (anchor != null) {
            val orderTag = when {
                mixMode == DiscoveryMixMode.WILDCARDS -> "order:random score:>15"
                batchIndex % 2 == 0 -> "order:favcount"
                else -> "order:score"
            }
            val pageStr = if (orderTag.contains("random")) "1" else ((batchIndex % 6) + 1).toString()
            queries += DiscoveryQuery(
                tags = "${anchor.tag} $orderTag",
                page = pageStr,
                limit = 30,
                bucketLabel = "Because you like ${anchor.tag.replace('_', ' ')}"
            )
        }

        // Bucket 4 (Wildcards mode or small profile): Fresh serendipity from trending high-score posts
        if (mixMode == DiscoveryMixMode.WILDCARDS || queries.size < 3) {
            queries += DiscoveryQuery(
                tags = "order:random score:>50",
                page = "1",
                limit = 25,
                bucketLabel = "Wildcard Discovery"
            )
        }

        return queries
    }

    /**
     * Ranks, filters, and artist-diversifies candidate posts purely in Kotlin on Dispatchers.Default.
     */
    fun rankAndDiversify(
        candidates: List<Post>,
        profile: TasteProfile,
        seenSignals: Map<Int, SeenPostSignal>,
        favoritePostIds: Set<Int>,
        mixMode: DiscoveryMixMode,
        alreadyDisplayedIds: Set<Int>,
        nowMs: Long = System.currentTimeMillis(),
        randomSeed: Long = nowMs
    ): List<Post> {
        if (candidates.isEmpty()) return emptyList()
        val rng = Random(randomSeed)

        val unique = candidates
            .distinctBy { it.id }
            .filter { post ->
                if (post.id in alreadyDisplayedIds) return@filter false
                val seen = seenSignals[post.id]
                // Always respect explicit "Less like this" dismissals
                if (seen != null && seen.engagementScore < 0) return@filter false
                if (mixMode == DiscoveryMixMode.UNSEEN_ONLY && (seen != null || post.id in favoritePostIds)) {
                    return@filter false
                }
                true
            }

        if (unique.isEmpty()) return emptyList()

        // First-pass raw affinity + quality + novelty score
        data class ScoredPost(val post: Post, val baseScore: Double, val primaryArtist: String)

        val scored = unique.map { post ->
            var affinity = 0.0
            val weights = profile.tagWeights
            if (weights.isNotEmpty()) {
                post.tags.artist.forEach { affinity += (weights[it.lowercase()] ?: 0.0) * 1.35 }
                post.tags.character.forEach { affinity += (weights[it.lowercase()] ?: 0.0) * 1.25 }
                post.tags.species.forEach { affinity += (weights[it.lowercase()] ?: 0.0) * 1.1 }
                post.tags.copyright.forEach { affinity += (weights[it.lowercase()] ?: 0.0) * 1.0 }
                // Cap general tag sum so mega-tagged posts don't artificially crush focused art
                var generalSum = 0.0
                post.tags.general.forEach { generalSum += (weights[it.lowercase()] ?: 0.0) * 0.65 }
                affinity += generalSum.coerceAtMost(18.0)
            }

            // Gentle logarithmic quality signal (rewards well-loved art without turning feed into pure top-all-time)
            val qualityPrior = ln(max(1, post.scoreTotal + post.favCount).toDouble() + 1.0) * 1.1

            // Novelty / seen balancing
            val seen = seenSignals[post.id]
            val isFav = post.id in favoritePostIds
            val novelty = when {
                seen == null && !isFav -> 4.2 // Strong bonus for never-seen discoveries
                mixMode == DiscoveryMixMode.WILDCARDS -> -3.0
                else -> {
                    // Older seen posts can gracefully resurface occasionally; very recent ones sink
                    val ageHours = ((nowMs - (seen?.seenAtMs ?: nowMs)).coerceAtLeast(0L)) / 3_600_000.0
                    when {
                        ageHours < 12.0 -> -4.5
                        ageHours < 72.0 -> -2.0
                        else -> 0.5
                    }
                }
            }

            // Controlled discovery jitter so refreshes feel fresh and alive
            val jitterRange = if (mixMode == DiscoveryMixMode.WILDCARDS) 5.0 else 2.2
            val jitter = rng.nextDouble() * jitterRange

            val artistKey = post.tags.artist.firstOrNull { it.lowercase() !in GENERIC_ARTISTS }?.lowercase()
                ?: "post_${post.id}"

            ScoredPost(
                post = post,
                baseScore = affinity + qualityPrior + novelty + jitter,
                primaryArtist = artistKey
            )
        }.sortedByDescending { it.baseScore }.toMutableList()

        // Greedy diversity pass: gently penalize consecutive/repeated works by the exact same artist
        val result = ArrayList<Post>(scored.size)
        val artistCounts = HashMap<String, Int>()
        var lastArtist: String? = null

        while (scored.isNotEmpty()) {
            // Inspect top 10 remaining candidates to pick highest diversity-adjusted item (fast O(N))
            val windowSize = scored.size.coerceAtMost(10)
            var bestIdx = 0
            var bestAdjusted = Double.NEGATIVE_INFINITY

            for (i in 0 until windowSize) {
                val item = scored[i]
                val count = artistCounts[item.primaryArtist] ?: 0
                val repeatPenalty = count * 4.5 + if (item.primaryArtist == lastArtist) 6.0 else 0.0
                val adj = item.baseScore - repeatPenalty
                if (adj > bestAdjusted) {
                    bestAdjusted = adj
                    bestIdx = i
                }
            }

            val chosen = scored.removeAt(bestIdx)
            result.add(chosen.post)
            artistCounts[chosen.primaryArtist] = (artistCounts[chosen.primaryArtist] ?: 0) + 1
            lastArtist = chosen.primaryArtist
        }

        return result
    }

    private fun extractPositiveQueryTags(rawQuery: String): List<String> {
        return rawQuery
            .split(Regex("\\s+"))
            .map { it.trim().lowercase() }
            .filter { token ->
                token.length >= 2 &&
                    !token.startsWith("-") &&
                    !token.contains(':') &&
                    !token.contains('*')
            }
            .map { it.removePrefix("~") }
            .filter { it.length >= 2 && it !in STOP_TAGS }
    }

    private fun pickWeightedDistinct(
        pool: List<TasteTag>,
        count: Int,
        rng: Random
    ): List<TasteTag> {
        if (pool.isEmpty() || count <= 0) return emptyList()
        val remaining = pool.toMutableList()
        val picked = mutableListOf<TasteTag>()

        repeat(count.coerceAtMost(remaining.size)) {
            val totalWeight = remaining.sumOf { max(0.1, it.score) }
            var target = rng.nextDouble() * totalWeight
            var chosenIdx = remaining.lastIndex
            for (i in remaining.indices) {
                target -= max(0.1, remaining[i].score)
                if (target <= 0.0) {
                    chosenIdx = i
                    break
                }
            }
            picked += remaining.removeAt(chosenIdx)
        }
        return picked
    }
}
