package com.openshield.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ─── Entity'ler ───────────────────────────────────────────────────────────────

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

// Kullanıcının "Spam mı?" kararını bekleyen şüpheli SMS'ler
@Entity(tableName = "pending_review")
data class PendingReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val reason: String,
    val score: Float,
    val receivedAt: Long = System.currentTimeMillis()
)

// ─── DAO'lar ──────────────────────────────────────────────────────────────────

@Dao
interface SpamNumberDao {
    @Query("SELECT * FROM spam_numbers ORDER BY addedAt DESC")
    fun getAllFlow(): Flow<List<SpamNumberEntity>>

    @Query("SELECT * FROM spam_numbers WHERE number = :number LIMIT 1")
    suspend fun findByNumber(number: String): SpamNumberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpamNumberEntity)

    @Query("DELETE FROM spam_numbers WHERE number = :number")
    suspend fun deleteByNumber(number: String)

    @Query("SELECT COUNT(*) FROM spam_numbers")
    suspend fun count(): Int
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
interface PendingReviewDao {
    // Tüm bekleyen incelemeler — uygulama açılınca kontrol edilir
    @Query("SELECT * FROM pending_review ORDER BY receivedAt ASC")
    suspend fun getAll(): List<PendingReviewEntity>

    @Insert
    suspend fun insert(entity: PendingReviewEntity)

    // Kullanıcı "Spam" veya "Değil" dedikten sonra sil
    @Query("DELETE FROM pending_review WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM pending_review")
    suspend fun count(): Int
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [
        SpamNumberEntity::class,
        WhitelistEntity::class,
        BlockedLogEntity::class,
        PendingReviewEntity::class   // yeni tablo
    ],
    version = 2,
    exportSchema = false
)
abstract class SpamDatabase : RoomDatabase() {
    abstract fun spamNumberDao(): SpamNumberDao
    abstract fun whitelistDao(): WhitelistDao
    abstract fun blockLogDao(): BlockedLogDao
    abstract fun pendingReviewDao(): PendingReviewDao

    companion object {
        @Volatile private var INSTANCE: SpamDatabase? = null

        // v1 → v2: pending_review tablosu eklendi
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_review (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sender      TEXT NOT NULL,
                        reason      TEXT NOT NULL,
                        score       REAL NOT NULL,
                        receivedAt  INTEGER NOT NULL
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
