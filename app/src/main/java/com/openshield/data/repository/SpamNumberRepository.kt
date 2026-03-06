package com.openshield.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * SpamDetectionEngine'in beklediği numara repository interface'i.
 * SpamRepository bu interface'i implement eder.
 */
interface SpamNumberRepository {
    suspend fun isInBlacklist(number: String): Boolean
    suspend fun isInWhitelist(number: String): Boolean
    suspend fun getCommunityReportCount(number: String): Int
}
