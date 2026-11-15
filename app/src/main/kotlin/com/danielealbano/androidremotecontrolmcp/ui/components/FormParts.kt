@file:Suppress("FunctionNaming", "LongParameterList")

package com.danielealbano.androidremotecontrolmcp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val DISABLED_ALPHA = 0.38f

/**
 * A Miuix text field with an optional error (shown in the error color, and announced as the
 * field's error) or [supportingText] below it. [modifier] lays out the whole block; focus
 * modifiers go in [fieldModifier], which is applied to the focusable field itself.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    error: String? = null,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                fieldModifier
                    .fillMaxWidth()
                    .then(if (error != null) Modifier.semantics { error(error) } else Modifier),
            label = label,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            colors =
                if (error != null) {
                    TextFieldDefaults.textFieldColors(borderColor = MiuixTheme.colorScheme.error)
                } else {
                    TextFieldDefaults.textFieldColors()
                },
        )
        val note = error ?: supportingText
        if (note != null) {
            Text(
                text = note,
                style = MiuixTheme.textStyles.footnote1,
                color = if (error != null) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp),
            )
        }
    }
}

/**
 * A row of mutually exclusive options (Miuix pill segments), announced as radio buttons. Replaces
 * the Material segmented button row.
 */
@Composable
fun SegmentedChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.secondaryContainer)
                .padding(4.dp)
                .selectableGroup()
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Text(
                text = option,
                textAlign = TextAlign.Center,
                style = MiuixTheme.textStyles.body2,
                color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSecondaryContainer,
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer)
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) },
                        ).padding(vertical = 10.dp, horizontal = 6.dp),
            )
        }
    }
}

/** A small selectable pill (replaces the Material filter chip). */
@Composable
fun ChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer,
                ).selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * A Miuix arrow preference row ([title], [summary], trailing chevron) that opens another screen,
 * with [onClickLabel] spoken as the double-tap action.
 */
@Composable
fun NavigationPreference(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClickLabel: String = stringResource(R.string.a11y_action_open),
) {
    BasicComponent(
        modifier = modifier,
        title = title,
        summary = summary,
        startAction =
            icon?.let {
                {
                    // Decorative: the title says it
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            },
        endActions = { PreferenceArrow(enabled) },
        onClick = onClick,
        onClickLabel = onClickLabel,
        enabled = enabled,
    )
}

/**
 * A preference row where only the switch itself toggles (no whole-row click), for settings a
 * stray touch must not flip.
 */
@Composable
fun SwitchOnlyPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
) {
    BasicComponent(
        modifier = modifier,
        title = title,
        summary = summary,
        endActions = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        insideMargin = insideMargin,
        enabled = enabled,
    )
}

/** The Miuix chevron that ends a row opening another screen. */
@Composable
private fun RowScope.PreferenceArrow(enabled: Boolean) {
    val layoutDirection = LocalLayoutDirection.current
    Image(
        imageVector = MiuixIcons.Basic.ArrowRight,
        contentDescription = null,
        colorFilter =
            ColorFilter.tint(
                if (enabled) {
                    MiuixTheme.colorScheme.onSurfaceVariantActions
                } else {
                    MiuixTheme.colorScheme.disabledOnSecondaryVariant
                },
            ),
        modifier =
            Modifier
                .padding(start = 8.dp)
                .size(width = 10.dp, height = 16.dp)
                .graphicsLayer { scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f }
                .align(Alignment.CenterVertically),
    )
}

/**
 * An event source row: the row opens its own settings ([onClick]), so the switch is a control of
 * its own, named after the row.
 */
@Composable
fun EventSourceRow(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicComponent(
        modifier = modifier,
        title = title,
        startAction = {
            // Decorative: the title says it
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.padding(end = 12.dp),
            )
        },
        endActions = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = title },
            )
            PreferenceArrow(enabled = true)
        },
        onClick = onClick,
        onClickLabel = stringResource(R.string.a11y_action_open),
    )
}
