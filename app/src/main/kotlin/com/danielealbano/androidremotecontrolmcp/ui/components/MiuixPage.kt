@file:Suppress("FunctionNaming", "LongParameterList")

package com.danielealbano.androidremotecontrolmcp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.ui.components.glass.BlurredBar
import com.danielealbano.androidremotecontrolmcp.ui.components.glass.blurredBarColor
import com.danielealbano.androidremotecontrolmcp.ui.components.glass.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Horizontal margin of page content, as in the Miuix / HyperOS settings pages. */
val PageHorizontalPadding = 12.dp

/** Vertical gap between the cards of a page. */
val PageItemSpacing = 12.dp

/**
 * Space the page content must leave free at the bottom for whatever floats over it (the floating
 * bottom bar). Null outside the main screen: pages then only clear the navigation bar.
 */
val LocalBottomBarInset = staticCompositionLocalOf<Dp?> { null }

@Composable
private fun pageBottomInset(): Dp =
    (LocalBottomBarInset.current ?: WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()) +
        PageItemSpacing

/**
 * The chrome shared by every page: a large Miuix title that collapses on scroll, frosted (blurred)
 * over the content scrolling beneath it.
 */
@Composable
private fun PageScaffold(
    title: String,
    modifier: Modifier,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    snackbarHostState: SnackbarHostState?,
    body: @Composable (innerPadding: PaddingValues, scrollModifier: Modifier) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    title = title,
                    color = blurredBarColor(backdrop),
                    navigationIcon = { if (onBack != null) TopBarBackButton(onClick = onBack) },
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(state = snackbarHostState) },
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            body(
                innerPadding,
                Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            )
        }
    }
}

/** A page whose [content] is a vertically scrolling column of cards. */
@Composable
fun MiuixPage(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PageScaffold(title, modifier, onBack, actions, snackbarHostState) { innerPadding, scrollModifier ->
        Column(
            modifier =
                scrollModifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState(), overscrollEffect = null)
                    .padding(top = innerPadding.calculateTopPadding() + PageItemSpacing, bottom = pageBottomInset())
                    .padding(horizontal = PageHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(PageItemSpacing),
            content = content,
        )
    }
}

/** A page whose [content] is a lazy list (for long or unbounded lists). */
@Composable
fun MiuixLazyPage(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    listState: LazyListState = rememberLazyListState(),
    itemSpacing: Dp = PageItemSpacing,
    content: LazyListScope.() -> Unit,
) {
    PageScaffold(title, modifier, onBack, actions, snackbarHostState) { innerPadding, scrollModifier ->
        val bottomInset = pageBottomInset()
        LazyColumn(
            modifier = scrollModifier.fillMaxWidth(),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = PageHorizontalPadding,
                    end = PageHorizontalPadding,
                    top = innerPadding.calculateTopPadding() + PageItemSpacing,
                    bottom = bottomInset,
                ),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            overscrollEffect = null,
            content = content,
        )
    }
}
