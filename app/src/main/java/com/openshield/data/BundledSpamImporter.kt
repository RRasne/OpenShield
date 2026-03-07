package com.openshield.data

import android.content.Context
import android.content.SharedPreferences
import com.openshield.data.db.SpamDatabase
import com.openshield.data.db.SpamNumberEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BundledSpamImporter {

    private const val PREFS_NAME = "openshield_prefs"
    private const val KEY_BUNDLED_VERSION = "bundled_version"
    private const val CURRENT_BUNDLED_VERSION = 1

    suspend fun importIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val installedVersion = prefs.getInt(KEY_BUNDLED_VERSION, 0)
        if (installedVersion >= CURRENT_BUNDLED_VERSION) return@withContext
        importFromAssets(context, prefs)
    }

    private suspend fun importFromAssets(context: Context, prefs: SharedPreferences) {
        val db = SpamDatabase.getInstance(context)
        val dao = db.spamNumberDao()
        try {
            context.assets.open("community_spam.csv").bufferedReader().useLines { lines ->
                val entities = lines
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .mapNotNull { parseLine(it) }
                    .toList()
                dao.deleteAllBundled()
                dao.insertAll(entities)
            }
            prefs.edit().putInt(KEY_BUNDLED_VERSION, CURRENT_BUNDLED_VERSION).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseLine(line: String): SpamNumberEntity? {
        val parts = line.trim().split(",")
        if (parts.size < 3) return null
        val number = parts[0].trim()
        val category = parts[1].trim()
        val reportCount = parts[2].trim().toIntOrNull() ?: 1
        if (number.isBlank()) return null
        return SpamNumberEntity(
            number = number,
            label = category,
            isUserAdded = false,
            reportCount = reportCount
        )
    }
}
