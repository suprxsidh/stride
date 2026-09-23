package com.suprxsidh.stride.ui.theme

/**
 * Nav-transition timing (spec §7). Nothing else in the app defines a duration constant --
 * this is the first, so a route's push/pop animation has one place to tune instead of a magic
 * number duplicated per route in `StrideNavHost`.
 */
object MotionTokens {
    const val NAV_TRANSITION_DURATION_MS = 280
}
