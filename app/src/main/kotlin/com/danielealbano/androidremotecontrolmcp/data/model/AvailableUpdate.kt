package com.danielealbano.androidremotecontrolmcp.data.model

/**
 * A newer app release that has been detected on GitHub and is available to install.
 *
 * @property versionName The canonical version of the newer release (no leading `v`). Releases may be
 * two-part (`major.minor`); the canonical form always carries all three components, with a missing
 * patch reported as 0.
 * @property releaseUrl The canonical GitHub release page URL to open in the browser.
 */
data class AvailableUpdate(
    val versionName: String,
    val releaseUrl: String,
)
