package com.orgzly.android.data

import android.content.Context
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.org.OrgFileSettings
import com.orgzly.org.utils.LogDone

/**
 * Resolves what to record when a note is marked as done.
 *
 * Follows org's `org-log-done`: an inherited `LOGGING` property overrides a notebook's
 * `#+STARTUP:`, which overrides the app setting.
 *
 * The two file-level scopes differ in how silence is read, as they do in org. `#+STARTUP:`
 * only sets what it names, so a preface with no done keyword defers. `LOGGING` replaces the
 * whole logging configuration, so a property that names no done keyword means off.
 */
object LogDonePolicy {

    private val WHITESPACE = Regex("\\s+")

    const val LOGGING = "LOGGING"

    fun resolve(context: Context, loggingProperty: String?, preface: String?): LogDone =
        fromLoggingProperty(loggingProperty) ?: fromPreface(preface) ?: fromPreferences(context)

    /** Null only when the property is absent; present-but-silent means [LogDone.NONE]. */
    fun fromLoggingProperty(value: String?): LogDone? {
        if (value == null) return null

        return value.trim().split(WHITESPACE)
            .mapNotNull(LogDone::fromToken)
            .lastOrNull() ?: LogDone.NONE
    }

    /** Null when the preface carries no done-logging token, so the caller falls back. */
    fun fromPreface(preface: String?): LogDone? =
        preface
            ?.let { OrgFileSettings.fromPreface(it) }
            ?.getKeywordValues(STARTUP)
            ?.flatMap { it.trim().split(WHITESPACE) }
            ?.mapNotNull(LogDone::fromToken)
            ?.lastOrNull()

    private fun fromPreferences(context: Context): LogDone =
        if (AppPreferences.logDone(context)) LogDone.TIME else LogDone.NONE

    private const val STARTUP = "STARTUP"
}
