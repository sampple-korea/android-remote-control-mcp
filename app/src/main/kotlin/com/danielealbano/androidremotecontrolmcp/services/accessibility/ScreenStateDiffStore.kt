package com.danielealbano.androidremotecontrolmcp.services.accessibility

/**
 * Single-slot, thread-safe store holding the comparable rows of the most recent fresh
 * get_screen_state capture — the baseline a `diff=true` call compares against.
 *
 * Unlike [ScreenStateSnapshotCache] there is no id and no pagination: [replace] is called on
 * EVERY fresh capture (diff or not), so the baseline always tracks the last state the client
 * was shown, and returns the previous baseline for the caller to diff against.
 */
interface ScreenStateDiffStore {
    /** Atomically stores [rows] as the new baseline and returns the previous one, or null if none. */
    fun replace(rows: ScreenStateRows): ScreenStateRows?

    fun clear()
}
