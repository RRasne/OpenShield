package com.openshield.ui

import android.content.Context

object OnboardingCheck {
    private const val PREF_FILE = "openshield"
    private const val KEY_DONE  = "onboarding_done"

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_DONE, false)

    fun markDone(context: Context) =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DONE, true).apply()
}
