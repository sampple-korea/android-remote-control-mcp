package com.danielealbano.androidremotecontrolmcp.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/** Duration of the NavHost push/pop transitions. */
private const val NAV_TRANSITION_DURATION_MS = 300

/** The incoming screen starts this fraction of the full width in from its resting side. */
private const val SLIDE_OFFSET_DIVISOR = 3

/**
 * Shared NavHost transitions: a push slides the new screen in from the right while the old one
 * drifts left and fades, and a pop mirrors that. Defined once so the settings graph and the
 * server graph move identically. The pop pair is also what the predictive back gesture scrubs
 * through, so it must stay the visual reverse of the push pair.
 */
val navEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    {
        slideInHorizontally(
            animationSpec = tween(NAV_TRANSITION_DURATION_MS),
            initialOffsetX = { it / SLIDE_OFFSET_DIVISOR },
        ) + fadeIn(animationSpec = tween(NAV_TRANSITION_DURATION_MS))
    }

val navExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    {
        slideOutHorizontally(
            animationSpec = tween(NAV_TRANSITION_DURATION_MS),
            targetOffsetX = { -it / SLIDE_OFFSET_DIVISOR },
        ) + fadeOut(animationSpec = tween(NAV_TRANSITION_DURATION_MS))
    }

val navPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    {
        slideInHorizontally(
            animationSpec = tween(NAV_TRANSITION_DURATION_MS),
            initialOffsetX = { -it / SLIDE_OFFSET_DIVISOR },
        ) + fadeIn(animationSpec = tween(NAV_TRANSITION_DURATION_MS))
    }

val navPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    {
        slideOutHorizontally(
            animationSpec = tween(NAV_TRANSITION_DURATION_MS),
            targetOffsetX = { it / SLIDE_OFFSET_DIVISOR },
        ) + fadeOut(animationSpec = tween(NAV_TRANSITION_DURATION_MS))
    }
