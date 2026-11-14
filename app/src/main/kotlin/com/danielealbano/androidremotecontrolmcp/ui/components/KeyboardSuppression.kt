package com.danielealbano.androidremotecontrolmcp.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Keeps the soft keyboard from appearing when the modified text field gains focus.
 *
 * This app is driven remotely — MCP tools set field values through the accessibility ACTION_SET_TEXT
 * action — so nobody types on the device itself, and a keyboard popping up on focus only shifts the
 * layout and invalidates the on-screen coordinates the tools work with. The field still takes focus
 * (focus-loss handlers keep working); the keyboard is hidden again as soon as the focus event lands,
 * so an IME that shows itself in the very same frame can still flash briefly.
 */
fun Modifier.hideSoftKeyboardOnFocus(): Modifier =
    composed {
        val keyboardController = LocalSoftwareKeyboardController.current
        onFocusChanged { focusState ->
            if (focusState.isFocused) {
                keyboardController?.hide()
            }
        }
    }
