package com.ugf.yiffur1.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalFavoriteDao {
    @Query("SELECT * FROM local_favorites ORDER BY savedAtMs DESC")
    fun observeAll(): Flow<List<LocalFavoriteEntity>>

    @Query("SELECT postId FROM local_favorites")
    fun observeAllIds(): Flow<List<Int>>

    @Query("SELECT * FROM local_favorites ORDER BY savedAtMs DESC")
    suspend fun getAllSnapshot(): List<LocalFavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM local_favorites WHERE postId = :postId)")
    suspend fun exists(postId: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalFavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LocalFavoriteEntity>)

    @Query("DELETE FROM local_favorites WHERE postId = :postId")
    suspend fun deleteById(postId: Int)

    @Query("DELETE FROM local_favorites")
    suspend fun clearAll()
}

@Dao
interface BrowseTabDao {
    @Query("SELECT * FROM browse_tabs ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<BrowseTabEntity>>

    @Query("SELECT * FROM browse_tabs ORDER BY createdAtMs DESC")
    suspend fun getAllSnapshot(): List<BrowseTabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tab: BrowseTabEntity): Long

    @Query("UPDATE browse_tabs SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE browse_tabs SET isActive = (id = :tabId)")
    suspend fun setActive(tabId: Long)

    @Query("UPDATE browse_tabs SET scrollIndex = :index WHERE id = :tabId")
    suspend fun updateScrollIndex(tabId: Long, index: Int)

    @Query("DELETE FROM browse_tabs WHERE id = :tabId")
    suspend fun deleteById(tabId: Long)

    @Query("DELETE FROM browse_tabs")
    suspend fun clearAll()
}

@Dao
interface SavedSearchDao {
    @Query("SELECT * FROM saved_searches ORDER BY isPinned DESC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<SavedSearchEntity>>

    @Query("SELECT * FROM saved_searches ORDER BY isPinned DESC, sortOrder ASC, id ASC")
    suspend fun getAllSnapshot(): List<SavedSearchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SavedSearchEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SavedSearchEntity>)

    @Update
    suspend fun update(item: SavedSearchEntity)

    @Query("DELETE FROM saved_searches WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_searches")
    suspend fun clearAll()
}

@Dao
interface BlacklistDao {
    @Query("SELECT * FROM blacklist_rules ORDER BY createdAtMs DESC, id DESC")
    fun observeAll(): Flow<List<BlacklistRuleEntity>>

    @Query("SELECT * FROM blacklist_rules WHERE enabled = 1")
    fun observeEnabled(): Flow<List<BlacklistRuleEntity>>

    @Query("SELECT * FROM blacklist_rules")
    suspend fun getAllSnapshot(): List<BlacklistRuleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rule: BlacklistRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<BlacklistRuleEntity>)

    @Query("UPDATE blacklist_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE blacklist_rules SET rule = :ruleText WHERE id = :id")
    suspend fun updateRuleText(id: Long, ruleText: String)

    @Query("DELETE FROM blacklist_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM blacklist_rules WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM blacklist_rules WHERE LOWER(TRIM(rule)) = LOWER(TRIM(:rule))")
    suspend fun deleteByRuleText(rule: String)

    @Query("DELETE FROM blacklist_rules WHERE fromAccountSync = 1")
    suspend fun deleteSyncedRules()

    @Query("DELETE FROM blacklist_rules")
    suspend fun clearAll()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY lastUsedMs DESC LIMIT 25")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY lastUsedMs DESC LIMIT :limit")
    suspend fun getRecentSnapshot(limit: Int = 40): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

@Dao
interface SeenPostDao {
    @Query("SELECT * FROM seen_posts ORDER BY seenAtMs DESC LIMIT 2500")
    fun observeRecent(): Flow<List<SeenPostEntity>>

    @Query("SELECT * FROM seen_posts ORDER BY seenAtMs DESC LIMIT :limit")
    suspend fun getRecentSnapshot(limit: Int = 2500): List<SeenPostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SeenPostEntity)

    @Query(
        "DELETE FROM seen_posts WHERE postId NOT IN (SELECT postId FROM seen_posts ORDER BY seenAtMs DESC LIMIT :keepCount)"
    )
    suspend fun pruneOld(keepCount: Int = 2500)

    @Query("DELETE FROM seen_posts")
    suspend fun clearAll()
}

@Dao
interface MutedTasteTagDao {
    @Query("SELECT * FROM muted_taste_tags ORDER BY mutedAtMs DESC")
    fun observeAll(): Flow<List<MutedTasteTagEntity>>

    @Query("SELECT tag FROM muted_taste_tags")
    suspend fun getAllTagsSnapshot(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MutedTasteTagEntity)

    @Query("DELETE FROM muted_taste_tags WHERE tag = :tag")
    suspend fun delete(tag: String)

    @Query("DELETE FROM muted_taste_tags")
    suspend fun clearAll()
}
