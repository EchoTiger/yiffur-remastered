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
