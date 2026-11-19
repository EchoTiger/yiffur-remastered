package com.ugf.yiffur1.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LocalFavoriteEntity::class,
        BrowseTabEntity::class,
        SavedSearchEntity::class,
        BlacklistRuleEntity::class,
        SearchHistoryEntity::class,
        SeenPostEntity::class,
        MutedTasteTagEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class YiffurDatabase : RoomDatabase() {
    abstract fun localFavoriteDao(): LocalFavoriteDao
    abstract fun browseTabDao(): BrowseTabDao
    abstract fun savedSearchDao(): SavedSearchDao
    abstract fun blacklistDao(): BlacklistDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun seenPostDao(): SeenPostDao
    abstract fun mutedTasteTagDao(): MutedTasteTagDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `seen_posts` (
                        `postId` INTEGER NOT NULL,
                        `seenAtMs` INTEGER NOT NULL,
                        `engagementScore` INTEGER NOT NULL,
                        PRIMARY KEY(`postId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `muted_taste_tags` (
                        `tag` TEXT NOT NULL,
                        `mutedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`tag`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun create(context: Context): YiffurDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                YiffurDatabase::class.java,
                "yiffur_v2.db"
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
