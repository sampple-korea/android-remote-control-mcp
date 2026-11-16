package com.danielealbano.androidremotecontrolmcp.ui.screens

/** External links opened from the About screen. `internal` so unit tests can verify the repository links. */
internal object AboutLinks {
    const val GITHUB_URL = "https://github.com/sampple-korea/android-remote-control-mcp"

    /** The project this app is a fork of. */
    const val UPSTREAM_URL = "https://github.com/danielealbano/android-remote-control-mcp"
    const val MAINTAINER_URL = "https://github.com/sampple-korea"
    const val LICENSE_URL = "$GITHUB_URL/blob/main/LICENSE.md"
    const val THIRD_PARTY_LICENSES_URL = "$GITHUB_URL/blob/main/THIRD_PARTY_LICENSES.md"
    const val ISSUES_URL = "$GITHUB_URL/issues"
    const val TUNNEL_STATUS_URL = "https://status.arc-tunnel.io/"
    const val TUNNEL_SERVER_REPOSITORY_URL = "https://github.com/danielealbano/android-remote-control-mcp-tunneld"
    const val DBIP_URL = "https://db-ip.com"
    const val PRIVACY_MODEL_URL =
        "https://huggingface.co/ai4privacy/llama-ai4privacy-multilingual-categorical-anonymiser-openpii"
}
