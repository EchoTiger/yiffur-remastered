package com.ugf.yiffur1.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_favorites")
data class LocalFavoriteEntity(
    @PrimaryKey val postId: Int,
    val postJson: String,
    val searchableTags: String,
    val rating: String,
    val savedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "browse_tabs")
data class BrowseTabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val query: String,
    val isPopular: Boolean = false,
    val popularScale: String = "day",
    val isActive: Boolean = false,
    val scrollIndex: Int = 0,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_searches")
data class SavedSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val query: String,
    val isPinned: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "blacklist_rules")
data class BlacklistRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val rule: String,
    val enabled: Boolean = true,
    val fromAccountSync: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val lastUsedMs: Long = System.currentTimeMillis()
)

/**
 * Tracks posts the user has opened, engaged with, or dismissed ("Less like this") so the
 * Explore "For You" discovery engine can balance fresh unseen content with familiar gems.
 */
@Entity(tableName = "seen_posts")
data class SeenPostEntity(
    @PrimaryKey val postId: Int,
    val seenAtMs: Long = System.currentTimeMillis(),
    val engagementScore: Int = 1 // 1 = viewed, 3 = liked/favorited, -5 = dismissed
)

/**
 * Tags muted specifically from "For You" preference recommendations (without globally blacklisting them).
 */
@Entity(tableName = "muted_taste_tags")
data class MutedTasteTagEntity(
    @PrimaryKey val tag: String,
    val mutedAtMs: Long = System.currentTimeMillis()
)
