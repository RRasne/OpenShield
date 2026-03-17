package com.openshield.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

// ─── Entities ─────────────────────────────────────────────────────────────────

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

@Entity(tableName = "community_reports")
data class CommunityReportEntity(
    @PrimaryKey val numberHash: String,
    val reportCount: Int = 1,
    val source: String = "community",
    val firstReportedAt: Long = System.currentTimeMillis(),
    val lastReportedAt: Long = System.currentTimeMillis()
)

/**
 * Şüpheli olarak işaretlenen ama henüz kullanıcı kararı verilmemiş mesajlar.
 * Uygulama açılınca MainViewModel bunları çeker ve dialog gösterir.
 * Karar sonrası kayıt silinir:
 *   - Spam → blocked_log + community_reports'a ekle
 *   - Değil → sadece sil
 */
@Entity(tableName = "pending_review")
data class PendingReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val reason: String,
    val score: Float,
    val receivedAt: Long = System.currentTimeMillis()
)

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface SpamNumberDao {
    @Query("SELECT * FROM spam_numbers WHERE isUserAdded = 1 ORDER BY addedAt DESC")
    fun getUserAddedFlow(): Flow<List<SpamNumberEntity>>

    @Query("SELECT * FROM spam_numbers ORDER BY addedAt DESC")
    fun getAllFlow(): Flow<List<SpamNumberEntity>>

    @Query("SELECT * FROM spam_numbers WHERE number = :number LIMIT 1")
    suspend fun findByNumber(number: String): SpamNumberEntity?

    @Query("SELECT COUNT(*) FROM spam_numbers")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpamNumberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SpamNumberEntity>)

    @Query("DELETE FROM spam_numbers WHERE number = :number")
    suspend fun deleteByNumber(number: String)

    @Query("DELETE FROM spam_numbers WHERE isUserAdded = 0")
    suspend fun deleteAllBundled()
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
interface CommunityReportDao {
    @Query("SELECT reportCount FROM community_reports WHERE numberHash = :hash LIMIT 1")
    suspend fun getReportCount(hash: String): Int?

    @Transaction
    suspend fun addReport(hash: String) {
        val existing = findByHash(hash)
        if (existing == null) {
            insert(CommunityReportEntity(numberHash = hash, reportCount = 1))
        } else {
            incrementCount(hash, System.currentTimeMillis())
        }
    }

    @Query("SELECT * FROM community_reports WHERE numberHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): CommunityReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CommunityReportEntity)

    @Query("UPDATE community_reports SET reportCount = reportCount + 1, lastReportedAt = :ts WHERE numberHash = :hash")
    suspend fun incrementCount(hash: String, ts: Long)

    @Query("SELECT COUNT(*) FROM community_reports")
    suspend fun totalCount(): Int

    @Query("SELECT * FROM community_reports ORDER BY reportCount DESC")
    fun getAllFlow(): Flow<List<CommunityReportEntity>>
}

@Dao
interface PendingReviewDao {
    @Query("SELECT * FROM pending_review ORDER BY receivedAt DESC")
    fun getAllFlow(): Flow<List<PendingReviewEntity>>

    @Query("SELECT COUNT(*) FROM pending_review")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: PendingReviewEntity)

    @Query("DELETE FROM pending_review WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_review")
    suspend fun clearAll()
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [
        SpamNumberEntity::class,
        WhitelistEntity::class,
        BlockedLogEntity::class,
        CommunityReportEntity::class,
        PendingReviewEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SpamDatabase : RoomDatabase() {
    abstract fun spamNumberDao(): SpamNumberDao
    abstract fun whitelistDao(): WhitelistDao
    abstract fun blockLogDao(): BlockedLogDao
    abstract fun communityReportDao(): CommunityReportDao
    abstract fun pendingReviewDao(): PendingReviewDao

    companion object {
        @Volatile private var INSTANCE: SpamDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS community_reports (
                        numberHash TEXT NOT NULL PRIMARY KEY,
                        reportCount INTEGER NOT NULL DEFAULT 1,
                        source TEXT NOT NULL DEFAULT 'community',
                        firstReportedAt INTEGER NOT NULL,
                        lastReportedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_review (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sender TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        score REAL NOT NULL,
                        receivedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): SpamDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SpamDatabase::class.java,
                    "openshield.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
