package com.danielealbano.androidremotecontrolmcp.services.accessibility

/**
 * Output options of `get_screen_state`. The defaults give the full output, unchanged.
 *
 * @property includeOffscreen When false, nodes that are not on screen (flag `off`) are not listed: no TSV row and no
 *   hierarchy line. Their on-screen descendants are still listed, nested under the nearest listed ancestor, the same
 *   way the children of structural-only nodes are.
 * @property includeHierarchy When false, the `hierarchy:` block of every window is left out.
 * @property interactiveOnly When true, only interactive nodes are listed: nodes that are clickable,
 *   long-clickable, editable, scrollable or carry accessibility actions. Pure labels, images and
 *   containers drop out (their interactive descendants are still listed, nested under the nearest
 *   listed ancestor). Applies to the node listing only, not to the screenshot annotation path.
 */
data class ScreenStateFormatOptions(
    val includeOffscreen: Boolean = true,
    val includeHierarchy: Boolean = true,
    val interactiveOnly: Boolean = false,
)
