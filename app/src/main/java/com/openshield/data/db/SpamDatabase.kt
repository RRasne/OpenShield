package com.openshield.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "spam_numbers")
data class SpamNumberEntity(
    @PrimaryKey val number: String,
    val label: String = "",
    val isUserAdded: Boolean = true,
    val reportCount: Int = 1,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "whitelist")
data class WhitelistEntity(
    @PrimaryKey val number: String,
    val name: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "blocked_log")
data class BlockedLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val reason: String,
    val score: Float,
    val blockedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sms_feedback")
data class SmsFeedbackEntity(
    @PrimaryKey val messageId: Long,
    val numberHash: String,
    val sender: String,
    val verdict: String,
    val isSpam: Boolean,
    val markedAt: Long = System.currentTimeMillis()
)

@Dao
interface SpamNumberDao {
    @Query("SELECT * FROM spam_numbers ORDER BY addedAt DESC")
    fun getAllFlow(): Flow<List<SpamNumberEntity>>

    @Query("SELECT * FROM spam_numbers WHERE number = :number LIMIT 1")
    suspend fun findByNumber(number: String): SpamNumberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpamNumberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SpamNumberEntity>)

    @Delete
    suspend fun delete(entity: SpamNumberEntity)

    @Query("DELETE FROM spam_numbers WHERE number = :number")
    suspend fun deleteByNumber(number: String)

    @Query("DELETE FROM spam_numbers WHERE isUserAdded = 0")
    suspend fun deleteAllBundled()

    @Query("SELECT COUNT(*) FROM spam_numbers")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM spam_numbers WHERE isUserAdded = 0")
    suspend fun bundledCount(): Int

    @Query("SELECT COUNT(*) FROM spam_numbers WHERE isUserAdded = 1")
    suspend fun userCount(): Int
}

@Dao
interface WhitelistDao {
    @Query("SELECT * FROM whitelist ORDER BY addedAt DESC")
    fun getAllFlow(): Flow<List<WhitelistEntity>>

    @Query("SELECT * FROM whitelist WHERE number = :number LIMIT 1")
    suspend fun findByNumber(number: String): WhitelistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WhitelistEntity)

    @Query("DELETE FROM whitelist WHERE number = :number")
    suspend fun deleteByNumber(number: String)

    @Query("SELECT COUNT(*) FROM whitelist")
    suspend fun count(): Int
}

@Dao
interface BlockedLogDao {
    @Query("SELECT * FROM blocked_log ORDER BY blockedAt DESC LIMIT 100")
    fun getRecentFlow(): Flow<List<BlockedLogEntity>>

    @Insert
    suspend fun insert(entity: BlockedLogEntity)

    @Query("SELECT COUNT(*) FROM blocked_log")
    suspend fun totalCount(): Int

    @Query("DELETE FROM blocked_log")
    suspend fun clearAll()
}

@Dao
interface SmsFeedbackDao {
    @Query("SELECT * FROM sms_feedback ORDER BY markedAt DESC")
    fun getAllFlow(): Flow<List<SmsFeedbackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SmsFeedbackEntity)

    @Query("DELETE FROM sms_feedback")
    suspend fun clearAll()
}

@Database(
    entities = [SpamNumberEntity::class, WhitelistEntity::class, BlockedLogEntity::class, SmsFeedbackEntity::class],
    version = 3,
    exportSchema = false
)
abstract class SpamDatabase : RoomDatabase() {
    abstract fun spamNumberDao(): SpamNumberDao
    abstract fun whitelistDao(): WhitelistDao
    abstract fun blockLogDao(): BlockedLogDao
    abstract fun smsFeedbackDao(): SmsFeedbackDao

    companion object {
        @Volatile
        private var INSTANCE: SpamDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sms_feedback (
                        messageId INTEGER NOT NULL,
                        numberHash TEXT NOT NULL,
                        isSpam INTEGER NOT NULL,
                        markedAt INTEGER NOT NULL,
                        PRIMARY KEY(messageId)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sms_feedback ADD COLUMN sender TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE sms_feedback ADD COLUMN verdict TEXT NOT NULL DEFAULT 'SPAM'")
            }
        }

        fun getInstance(context: Context): SpamDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SpamDatabase::class.java,
                    "openshield.db"
                )
                    .fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
