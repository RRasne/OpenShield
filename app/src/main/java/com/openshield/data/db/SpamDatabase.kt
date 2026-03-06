package com.openshield.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val numberHash: String,
    val category: String,
    val reportedAt: Long = System.currentTimeMillis(),
    val isSent: Boolean = false
)

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface SpamNumberDao {
    @Query("SELECT * FROM spam_numbers ORDER BY addedAt DESC")
    fun getAllFlow(): Flow<List<SpamNumberEntity>>

    @Query("SELECT * FROM spam_numbers WHERE isUserAdded = 1 ORDER BY addedAt DESC")
    fun getUserAddedFlow(): Flow<List<SpamNumberEntity>>

    @Query("SELECT * FROM spam_numbers WHERE number = :number LIMIT 1")
    suspend fun findByNumber(number: String): SpamNumberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpamNumberEntity)

    @Query("DELETE FROM spam_numbers WHERE number = :number")
    suspend fun deleteByNumber(number: String)

    @Query("SELECT COUNT(*) FROM spam_numbers")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM spam_numbers WHERE isUserAdded = 1")
    suspend fun userAddedCount(): Int
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
    // LIMIT 100 + ORDER BY — duplicate row'lar zaten ayrı id ile gelir
    // ama aynı sender'dan kısa sürede gelen duplicate'leri önlemek için
    // DAO'ya unique index ekle: sender + blockedAt (saniye hassasiyetiyle)
    @Query("SELECT * FROM blocked_log ORDER BY blockedAt DESC LIMIT 100")
    fun getRecentFlow(): Flow<List<BlockedLogEntity>>

    // FIX: Aynı sender'dan 3 saniye içinde duplicate insert'i engelle
    @Query("""
        INSERT OR IGNORE INTO blocked_log (sender, reason, score, blockedAt)
        SELECT :sender, :reason, :score, :blockedAt
        WHERE NOT EXISTS (
            SELECT 1 FROM blocked_log
            WHERE sender = :sender
            AND blockedAt > :blockedAt - 3000
        )
    """)
    suspend fun insertIfNotDuplicate(
        sender: String,
        reason: String,
        score: Float,
        blockedAt: Long = System.currentTimeMillis()
    )

    @Insert
    suspend fun insert(entity: BlockedLogEntity)

    @Query("SELECT COUNT(*) FROM blocked_log")
    suspend fun totalCount(): Int

    @Query("DELETE FROM blocked_log")
    suspend fun clearAll()
}

@Dao
interface CommunityReportDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(report: CommunityReportEntity)

    @Query("SELECT * FROM community_reports WHERE isSent = 0 ORDER BY reportedAt ASC LIMIT :limit")
    suspend fun getPendingReports(limit: Int): List<CommunityReportEntity>

    @Query("UPDATE community_reports SET isSent = 1 WHERE id IN (:ids)")
    suspend fun markAsSent(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM community_reports WHERE isSent = 0")
    suspend fun pendingCount(): Int

    @Query("DELETE FROM community_reports WHERE isSent = 1")
    suspend fun clearSent()
}

// ─── Migration ────────────────────────────────────────────────────────────────

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS community_reports (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                numberHash TEXT NOT NULL,
                category TEXT NOT NULL,
                reportedAt INTEGER NOT NULL,
                isSent INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [
        SpamNumberEntity::class,
        WhitelistEntity::class,
        BlockedLogEntity::class,
        CommunityReportEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SpamDatabase : RoomDatabase() {
    abstract fun spamNumberDao(): SpamNumberDao
    abstract fun whitelistDao(): WhitelistDao
    abstract fun blockLogDao(): BlockedLogDao
    abstract fun communityReportDao(): CommunityReportDao

    companion object {
        @Volatile private var INSTANCE: SpamDatabase? = null

        fun getInstance(context: Context): SpamDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SpamDatabase::class.java,
                    "openshield.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
