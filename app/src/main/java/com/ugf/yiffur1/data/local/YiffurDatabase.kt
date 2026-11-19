package com.ugf.yiffur1.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LocalFavoriteEntity::class,
        BrowseTabEntity::class,
        SavedSearchEntity::class,
        BlacklistRuleEntity::class,
        SearchHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class YiffurDatabase : RoomDatabase() {
    abstract fun localFavoriteDao(): LocalFavoriteDao
    abstract fun browseTabDao(): BrowseTabDao
    abstract fun savedSearchDao(): SavedSearchDao
    abstract fun blacklistDao(): BlacklistDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        fun create(context: Context): YiffurDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                YiffurDatabase::class.java,
                "yiffur_v2.db"
            )
                .fallbackToDestructiveMigration()
                .build()
    }
}
