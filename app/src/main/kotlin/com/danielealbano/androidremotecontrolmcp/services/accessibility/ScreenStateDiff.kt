package com.danielealbano.androidremotecontrolmcp.services.accessibility

// ─────────────────────────────────────────────────────────────────────────────
// Screen-state diffing for get_screen_state (diff=true)
//
// A fresh capture is reduced to the rows the compact formatter would list — one
// entry per kept node, keyed by node id — and stored as the baseline for the
// next capture. A diff call renders only the entries that were added, changed,
// or removed relative to that baseline, which is far smaller than the full
// state for the common "act, then look at what changed" flow. Node ids are the
// tree parser's content hashes: stable across captures while the UI state is
// unchanged, so an id present in both captures with a different rendered row
// is a changed node, and an id on only one side is an add/remove.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One listed node's comparable content: the window section it was captured in, its rendered TSV
 * row (exactly as [CompactTreeFormatter] emits it), and its labelled accessibility actions.
 * The actions are part of the comparison — a node whose actions change counts as changed even
 * when its row does not.
 */
data class ScreenStateRowEntry(
    val id: String,
    val windowHeader: String,
    val row: String,
    val actions: List<String> = emptyList(),
) {
    /** The string two captures of this node are compared by. */
    val comparable: String
        get() = row + COMPARABLE_SEPARATOR + actions.joinToString(NodeActionLabels.SEPARATOR)

    private companion object {
        const val COMPARABLE_SEPARATOR = "\u0001"
    }
}

/**
 * The comparable rows of one fresh get_screen_state capture, keyed by node id in capture order,
 * together with the [ScreenStateFormatOptions] they were listed under. A diff is only computed
 * between captures taken with equal options: a different filter changes which nodes are listed,
 * which would masquerade as mass add/removes.
 */
data class ScreenStateRows(
    val options: ScreenStateFormatOptions,
    val entries: Map<String, ScreenStateRowEntry>,
) {
    val nodeCount: Int get() = entries.size
}

/**
 * The difference between a baseline capture and the current one.
 *
 * [added] and [changed] hold the CURRENT capture's entries (in capture order); [removed] holds
 * the BASELINE's entries. [coversMostOfTree] is the fallback signal: when the changed entries
 * outnumber half of the larger side, a diff listing is no smaller than the full state, so the
 * caller returns the full state instead.
 */
data class ScreenStateDiff(
    val added: List<ScreenStateRowEntry>,
    val changed: List<ScreenStateRowEntry>,
    val removed: List<ScreenStateRowEntry>,
    val baselineNodeCount: Int,
    val currentNodeCount: Int,
) {
    val changeCount: Int get() = added.size + changed.size + removed.size

    val coversMostOfTree: Boolean
        get() = changeCount * 2 > maxOf(baselineNodeCount, currentNodeCount)
}

/**
 * Reduces [result] to its comparable rows under [options]: the same nodes, in the same order,
 * that [formatMultiWindow] would list, each rendered with the formatter's own row rendering so
 * diff output rows are identical to full-state rows.
 */
internal fun CompactTreeFormatter.collectScreenStateRows(
    result: MultiWindowResult,
    options: ScreenStateFormatOptions,
): ScreenStateRows {
    val entries = LinkedHashMap<String, ScreenStateRowEntry>()
    for (windowData in result.windows) {
        collectWindowRows(windowData.tree, buildWindowHeader(windowData), options, entries)
    }
    return ScreenStateRows(options, entries)
}

private fun CompactTreeFormatter.collectWindowRows(
    node: AccessibilityNodeData,
    windowHeader: String,
    options: ScreenStateFormatOptions,
    entries: MutableMap<String, ScreenStateRowEntry>,
) {
    if (isListed(node, options)) {
        val row =
            StringBuilder()
                .also { appendElementRow(it, node) }
                .toString()
                .trimEnd('\n')
        entries[node.id] = ScreenStateRowEntry(node.id, windowHeader, row, node.actions)
    }
    for (child in node.children) {
        collectWindowRows(child, windowHeader, options, entries)
    }
}

/** Computes the diff of [current] against [baseline]. Both must have been collected under equal options. */
internal fun computeScreenStateDiff(
    baseline: ScreenStateRows,
    current: ScreenStateRows,
): ScreenStateDiff {
    val added = mutableListOf<ScreenStateRowEntry>()
    val changed = mutableListOf<ScreenStateRowEntry>()
    for ((id, entry) in current.entries) {
        val previous = baseline.entries[id]
        when {
            previous == null -> added.add(entry)
            previous.comparable != entry.comparable -> changed.add(entry)
        }
    }
    val removed =
        baseline.entries.entries
            .filter { (id, _) -> id !in current.entries }
            .map { it.value }
    return ScreenStateDiff(
        added = added,
        changed = changed,
        removed = removed,
        baselineNodeCount = baseline.nodeCount,
        currentNodeCount = current.nodeCount,
    )
}

/**
 * Renders [diff] in the full state's text conventions: a leading note line, the flags legend, the
 * screen line, then one section per affected window with the TSV header and one row per changed
 * node, prefixed with its marker (`+` added, `~` changed, `-` removed). The window's added/changed
 * nodes with labelled actions get the usual actions section. There is no hierarchy section: the
 * nesting of a handful of changed nodes carries no information the rows do not.
 *
 * An empty diff renders as a single note line plus the screen line.
 */
internal fun formatScreenStateDiff(
    diff: ScreenStateDiff,
    screenInfo: ScreenInfo,
): String {
    val sb = StringBuilder()
    val screenLine =
        "screen:${screenInfo.width}x${screenInfo.height} " +
            "density:${screenInfo.densityDpi} orientation:${screenInfo.orientation}"
    if (diff.changeCount == 0) {
        sb.appendLine("note:no changes since the previous get_screen_state capture (${diff.currentNodeCount} nodes)")
        sb.appendLine(screenLine)
        return sb.toString().trimEnd('\n')
    }

    sb.appendLine(
        "note:diff against the previous get_screen_state capture: " +
            "+${diff.added.size} added, ~${diff.changed.size} changed, -${diff.removed.size} removed " +
            "(nodes ${diff.baselineNodeCount} -> ${diff.currentNodeCount}). " +
            "Rows are prefixed with their change marker. " +
            "Call get_screen_state without diff=true for the full state.",
    )
    sb.appendLine(CompactTreeFormatter.NOTE_LINE_FLAGS_LEGEND)
    sb.appendLine(screenLine)

    for ((windowHeader, section) in groupDiffByWindow(diff)) {
        sb.appendLine(windowHeader)
        sb.appendLine(CompactTreeFormatter.HEADER)
        for (row in section.rows) sb.appendLine(row)
        val withActions = section.actionEntries
        if (withActions.isNotEmpty()) {
            sb.appendLine(CompactTreeFormatter.ACTIONS_HEADER)
            sb.appendLine()
            for (entry in withActions) {
                sb.append(CompactTreeFormatter.ACTIONS_ITEM_PREFIX)
                    .append(entry.id)
                    .append(": ")
                    .appendLine(entry.actions.joinToString(NodeActionLabels.SEPARATOR))
            }
        }
    }
    return sb.toString().trimEnd('\n')
}

private class DiffWindowSection {
    val rows = mutableListOf<String>()
    val actionEntries = mutableListOf<ScreenStateRowEntry>()
}

/**
 * Groups the diff entries by window section: windows of the current capture first (in capture
 * order), then windows that only exist in the baseline (their nodes were all removed). Within a
 * window, added/changed rows come in capture order, then removed rows in baseline order.
 */
private fun groupDiffByWindow(diff: ScreenStateDiff): List<Pair<String, DiffWindowSection>> {
    val sections = LinkedHashMap<String, DiffWindowSection>()

    fun sectionFor(header: String) = sections.getOrPut(header) { DiffWindowSection() }

    for (entry in diff.added) {
        val section = sectionFor(entry.windowHeader)
        section.rows.add("+" + entry.row)
        if (entry.actions.isNotEmpty()) section.actionEntries.add(entry)
    }
    for (entry in diff.changed) {
        val section = sectionFor(entry.windowHeader)
        section.rows.add("~" + entry.row)
        if (entry.actions.isNotEmpty()) section.actionEntries.add(entry)
    }
    for (entry in diff.removed) {
        sectionFor(entry.windowHeader).rows.add("-" + entry.row)
    }
    return sections.entries.map { it.key to it.value }
}
