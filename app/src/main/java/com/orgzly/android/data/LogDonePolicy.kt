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

    /**
     * Rewrites a preface so its `#+STARTUP:` states [logDone], or states nothing when it is
     * null. Only done-logging tokens are touched; unrelated ones such as `overview` and
     * `logrepeat` keep their place, and a line left with no tokens is dropped.
     */
    fun withLogDoneInPreface(preface: String?, logDone: LogDone?): String {
        val token = when (logDone) {
            LogDone.NONE -> "nologdone"
            LogDone.TIME -> "logdone"
            LogDone.NOTE -> "lognotedone"
            null -> null
        }

        val original = preface ?: ""
        // "".lines() is one empty line, which would survive as a trailing blank.
        if (original.isEmpty()) return if (token != null) "#+STARTUP: " + token else ""

        val lines = original.lines()
        val startupLines =
            lines.indices.filter { lines[it].trimStart().startsWith(STARTUP_PREFIX, true) }
        val target = startupLines.lastOrNull()

        val out = ArrayList<String>(lines.size + 1)

        for (i in lines.indices) {
            if (i !in startupLines) {
                out.add(lines[i])
                continue
            }

            val colon = lines[i].indexOf(':')
            var kept = lines[i].substring(colon + 1)
                .trim()
                .split(WHITESPACE)
                .filter { it.isNotBlank() && LogDone.fromToken(it) == null }

            if (i == target && token != null) {
                kept = kept + token
            }

            if (kept.isNotEmpty()) {
                out.add(lines[i].substring(0, colon + 1) + " " + kept.joinToString(" "))
            }
        }

        if (token != null && target == null) {
            out.add(0, "#+STARTUP: " + token)
        }

        return out.joinToString("\n")
    }

    private const val STARTUP_PREFIX = "#+STARTUP:"

    private const val STARTUP = "STARTUP"
}
