package com.tyganeutronics.myratecalculator.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tyganeutronics.myratecalculator.database.contract.DatabaseContract
import com.tyganeutronics.myratecalculator.database.dao.RatesDao
import com.tyganeutronics.myratecalculator.database.dao.RewardsDao
import com.tyganeutronics.myratecalculator.database.dao.SpendsDao
import com.tyganeutronics.myratecalculator.database.entities.RateEntity
import com.tyganeutronics.myratecalculator.database.entities.RewardEntity
import com.tyganeutronics.myratecalculator.database.entities.SpendEntity

@Database(
    entities = [RateEntity::class, RewardEntity::class, SpendEntity::class],
    version = DatabaseContract.DATABASE_VERSION,
    autoMigrations = [

    ]
)
abstract class Database : RoomDatabase() {
    abstract fun rates(): RatesDao

    abstract fun rewards(): RewardsDao

    abstract fun spends(): SpendsDao

    companion object {
        /** v4 → v5: flag rates the user entered themselves, so a refresh cannot replace them. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE rates ADD COLUMN custom INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3 → v4: add sort_order so the rates list keeps a user defined order. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE rates ADD COLUMN sort_order INTEGER NOT NULL DEFAULT ${Int.MAX_VALUE}")
                // Seed initial order from existing row IDs so the list stays stable
                database.execSQL("UPDATE rates SET sort_order = _id")
            }
        }

        /** v2 → v3: add the hidden flag for swipe to hide. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE rates ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v1 → v2: remove the currency_base column from the rates table.
         * SQLite < API 35 doesn't support DROP COLUMN, so we recreate the table.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create a new table without the currency_base column
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `rates_new` (
                        `_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `url` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `rate` TEXT NOT NULL,
                        `last_rate` TEXT NOT NULL,
                        `last_checked` INTEGER NOT NULL,
                        `pinned` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO `rates_new` (`_id`, `created_at`, `updated_at`, `url`, `name`, `currency`, `rate`, `last_rate`, `last_checked`, `pinned`)
                    SELECT `_id`, `created_at`, `updated_at`, `url`, `name`, `currency`, `rate`, `last_rate`, `last_checked`, `pinned`
                    FROM `rates`
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE `rates`")
                database.execSQL("ALTER TABLE `rates_new` RENAME TO `rates`")
            }
        }
    }
}
