package com.openshield.data.db

import android.content.Context
import androidx.room.*
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

@Database(
    entities = [SpamNumberEntity::class, WhitelistEntity::class, BlockedLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SpamDatabase : RoomDatabase() {
    abstract fun spamNumberDao(): SpamNumberDao
    abstract fun whitelistDao(): WhitelistDao
    abstract fun blockLogDao(): BlockedLogDao

    companion object {
        @Volatile private var INSTANCE: SpamDatabase? = null

        fun getInstance(context: Context): SpamDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SpamDatabase::class.java,
                    "openshield.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
