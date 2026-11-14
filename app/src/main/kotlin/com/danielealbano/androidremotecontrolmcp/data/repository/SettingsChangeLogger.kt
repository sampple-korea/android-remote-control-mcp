package com.danielealbano.androidremotecontrolmcp.data.repository

import com.danielealbano.androidremotecontrolmcp.data.model.ServerLogEntry
import com.danielealbano.androidremotecontrolmcp.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coalesces settings-change log entries per setting key: text fields persist on every keystroke,
 * so a burst of writes to the same key becomes ONE entry after [windowMs] of quiet, rendered with
 * the value BEFORE the burst and the latest value. Bursts that end where they started (old ==
 * final new) are dropped. Values are compared but only reach the log through [submit]'s render
 * lambda — secret settings pass a lambda that ignores them.
 *
 * A change submitted with `immediate = true` skips coalescing and is logged synchronously: boolean
 * toggles produce no burst, and a coalesced entry is lost when the process dies inside the window
 * (an app reinstall right after a toggle left the stored value changed with no log entry at all).
 */
@Singleton
class SettingsChangeLogger internal constructor(
    private val serverLogRepository: ServerLogRepository,
    dispatcher: CoroutineDispatcher,
    private val windowMs: Long,
) {
    @Inject
    constructor(
        serverLogRepository: ServerLogRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : this(serverLogRepository, dispatcher, COALESCE_WINDOW_MS)

    private class Pending(
        val firstOldValue: String,
        var latestNewValue: String,
        var render: (old: String, new: String) -> String,
    ) {
        var generation: Long = 0
        var flushJob: Job? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val pending = mutableMapOf<String, Pending>()

    fun submit(
        key: String,
        oldValue: String,
        newValue: String,
        render: (old: String, new: String) -> String,
        immediate: Boolean = false,
    ) {
        if (immediate) {
            // Supersede any coalesced entry still pending for this key: it must not flush a second,
            // stale entry later, and the value before its burst is the truthful "old" value.
            val firstOldValue =
                synchronized(pending) {
                    pending.remove(key)?.also { it.flushJob?.cancel() }?.firstOldValue ?: oldValue
                }
            if (firstOldValue != newValue) {
                serverLogRepository.log(ServerLogEntry.Type.SETTINGS, render(firstOldValue, newValue))
            }
            return
        }
        synchronized(pending) {
            val existing = pending[key]
            if (existing == null) {
                val created = Pending(oldValue, newValue, render)
                pending[key] = created
                created.flushJob = scheduleFlush(key, created.generation)
            } else {
                existing.latestNewValue = newValue
                existing.render = render
                existing.generation += 1
                existing.flushJob?.cancel()
                existing.flushJob = scheduleFlush(key, existing.generation)
            }
        }
    }

    private fun scheduleFlush(
        key: String,
        generation: Long,
    ): Job =
        scope.launch {
            delay(windowMs)
            flush(key, generation)
        }

    /**
     * The generation guard makes a superseded flush a no-op even in the narrow race where a prior
     * flush coroutine passes its delay concurrently with a new [submit] — a burst therefore ALWAYS
     * coalesces to exactly one entry carrying the pre-burst old value.
     */
    private fun flush(
        key: String,
        expectedGeneration: Long,
    ) {
        val entry =
            synchronized(pending) {
                val current = pending[key]
                if (current == null || current.generation != expectedGeneration) {
                    null
                } else {
                    pending.remove(key)
                }
            } ?: return
        if (entry.firstOldValue == entry.latestNewValue) return
        serverLogRepository.log(
            ServerLogEntry.Type.SETTINGS,
            entry.render(entry.firstOldValue, entry.latestNewValue),
        )
    }

    companion object {
        const val COALESCE_WINDOW_MS = 2_000L
    }
}

/** Submits a boolean setting change, rendered as "<subject> <onWord|offWord>" with the final value. */
internal fun SettingsChangeLogger.submitToggle(
    key: String,
    oldValue: Boolean,
    newValue: Boolean,
    subject: String,
    onWord: String = "enabled",
    offWord: String = "disabled",
    immediate: Boolean = false,
) {
    submit(
        key,
        oldValue.toString(),
        newValue.toString(),
        render = { _, n -> "$subject ${if (n.toBoolean()) onWord else offWord}" },
        immediate = immediate,
    )
}
