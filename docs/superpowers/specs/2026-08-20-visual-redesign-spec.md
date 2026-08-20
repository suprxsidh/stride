# Stride visual redesign — implementation spec

Status: approved direction (locked 2026-08-13, iterated 3 rounds), written up 2026-08-20.
Source: HTML/CSS mockup artifact from a prior brainstorming session (dashboard, consistency,
onboarding only) — **not saved anywhere accessible**. This document is the complete spec of what
was approved; a future session should be able to implement the remaining 4 screens (food logging,
weight, settings, health-connect-setup) from this doc alone, without needing the original mockup.

Worktree: `.claude/worktrees/redesign-v1`, branch `worktree-redesign-v1`. Do not merge to master —
see `BUILD_PLAN.md` for status.

## 1. Direction summary

Bold & athletic, Oura/Whoop-inspired dark theme, pushed past generic-dark-dashboard defaults into
a **running ledger / LED-readout / punch-card** motif:

- Dark surfaces, single ember accent (`#FF6A3D`), no secondary hue competing for attention.
- Tabular-monospace numerals everywhere a stat is shown — no proportional digits on any kcal,
  weight, streak, or time value.
- "Data as hero": numbers are the largest, highest-contrast element on any screen; chrome and
  decoration recede.
- Two recurring surface languages:
  - **LED-readout**: stat displays look like a seven-segment digital readout in a recessed bezel
    (dashboard hero kcal number, streak counters, weight delta).
  - **Punch-card / ledger row**: list and grid rows have a tactile ticket-stub feel — a squared-off
    card with two notches cut into its left/right edges, like a punched card or a torn ticket.
- **Nav icons** reuse the flag/punch-card motifs (checkered start-line flag glyph for the active
  tab indicator instead of a generic Material icon fill).
- A shared **"start line"** motif — a checkered horizontal bar — appears wherever a screen needs a
  divider, a progress track, or a section break. It is the one unifying decorative element used
  across every screen.

Only Dashboard, Consistency, and Onboarding were mocked directly. Food logging, Weight, Settings,
and Health-Connect-setup screens are **not** individually designed — they inherit this token/
component system as-is. When migrating them, reach for `LedReadout`, `PunchCardRow`, and
`StartLineDivider`/`StartLineProgress` first; do not invent new visual language for them.

## 2. Color tokens

New file content for `Color.kt` (replaces the current green-accent palette — Phase 1-3 shipped
with `DeficitAccent = #39FF88`; this redesign supersedes it with the approved ember accent). Keep
old constant *names* where reasonably possible is not required — downstream `Theme.kt` is the only
consumer of these tokens, so screens should reference `MaterialTheme.colorScheme.*`, not the raw
`Color.kt` constants, once migrated.

| Token | Hex | Use |
|---|---|---|
| `StrideBackground` | `#0A0A0B` | Scaffold background. Near-black, not pure `#000` — softer on OLED, avoids pure-black crush. |
| `StrideSurface` | `#141416` | Default card / row surface. |
| `StrideSurfaceRaised` | `#1C1C1F` | Elevated surface (dialogs, the LED-readout bezel's outer frame). |
| `StrideSurfaceSunken` | `#000000` | True black — used only for LED-readout bezel interior and punch-card notch cutouts, so they read as physically recessed/punched. |
| `StrideEmber` | `#FF6A3D` | The one accent. Primary actions, active nav state, lit LED segments, progress fills. |
| `StrideEmberDim` | `#7A3620` | Ember at rest / unlit-segment ghosting / secondary emphasis. Do not use as a second brand hue — it's the same hue, darkened, not a different color. |
| `StrideEmberGlow` | `#FF6A3D` at 10% alpha | Bloom/glow layer behind LED digits. Not a solid fill. |
| `StrideOnSurface` | `#F5F3F0` | Primary text. Warm off-white (ledger-paper tone), not pure white. |
| `StrideOnSurfaceMuted` | `#8A8A8E` | Secondary/caption text, inactive nav labels. |
| `StrideOutline` | `#2A2A2E` | Hairlines, card borders, perforation-line strokes. |
| `StrideError` | `#FF5C5C` | Errors, over-budget states. Shifted warmer than Material default red to sit near the ember family without being confusable with it. |
| `StridePositive` | `#4CAF7D` | The one non-ember semantic color, used sparingly for "under budget" / "floor met" / "run logged" affirmative dots on the consistency grid (mirrors the existing three-dot-per-day encoding — do not accent these ember, or they'd visually compete with primary CTAs). |

`Color.kt`:

```kotlin
package com.suprxsidh.deficit.ui.theme

import androidx.compose.ui.graphics.Color

val StrideBackground = Color(0xFF0A0A0B)
val StrideSurface = Color(0xFF141416)
val StrideSurfaceRaised = Color(0xFF1C1C1F)
val StrideSurfaceSunken = Color(0xFF000000)

val StrideEmber = Color(0xFFFF6A3D)
val StrideEmberDim = Color(0xFF7A3620)
val StrideEmberGlow = Color(0x1AFF6A3D) // StrideEmber @ 10% alpha

val StrideOnSurface = Color(0xFFF5F3F0)
val StrideOnSurfaceMuted = Color(0xFF8A8A8E)
val StrideOutline = Color(0xFF2A2A2E)

val StrideError = Color(0xFFFF5C5C)
val StridePositive = Color(0xFF4CAF7D)
```

`Theme.kt` maps these into `darkColorScheme()` the same way the current file does — `primary =
StrideEmber`, `onPrimary = StrideSurfaceSunken` (near-black text on ember buttons, not pure
white — keeps the ledger tone consistent), `background = StrideBackground`, `surface =
StrideSurface`, `surfaceVariant = StrideSurfaceRaised`, `outline = StrideOutline`, `error =
StrideError`. `StridePositive` and the LED/glow tokens have no Material 3 slot — expose them via a
small `CompositionLocal` or just reference the top-level `val`s directly from the two new
composables (simplest; this app has no theming variants to support).

## 3. Typography

**Bundled font: JetBrains Mono** (SIL OFL 1.1, redistribution-safe). Chosen because:
- True monospace → tabular figures are the default behavior for every digit, not a fallback
  feature flag some fonts only enable via `FontFeatureSetting("tnum")`.
- Has a real Bold/SemiBold/Medium/Regular family (needed for the LED readout vs. body-text
  contrast) rather than a single mono weight.
- No network dependency at runtime — bundled as a resource, unlike Compose's
  `GoogleFont`/`Downloadable Fonts` API, which would need Google Play Services and violates this
  project's "no network calls except OFF/Gemini" constraint (`CLAUDE.md`).

Four weights bundled at `app/src/main/res/font/`:
- `jetbrains_mono_regular.ttf`
- `jetbrains_mono_medium.ttf`
- `jetbrains_mono_semibold.ttf`
- `jetbrains_mono_bold.ttf`

`Type.kt` font family declaration:

```kotlin
package com.suprxsidh.deficit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.suprxsidh.deficit.R

val StrideMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)
```

The whole app uses one family (`StrideMono`) for everything, including body copy — this is
deliberate, not a placeholder. The ledger/terminal aesthetic depends on monospace body text too
(it's what makes list rows feel like a punch card rather than a generic card with a mono number
glued on). Do not introduce a second proportional-sans family for body text.

Type scale (`DeficitTypography` in `Type.kt`, keep the existing val name so `Theme.kt`'s
`MaterialTheme(typography = ...)` wire-up doesn't need a rename):

| Style | Weight | Size | Line height | Letter spacing | Use |
|---|---|---|---|---|---|
| `displayLarge` (LED hero) | Bold | 56.sp | 60.sp | 0.5.sp | The one big number per screen — dashboard kcal-remaining, hero streak. Only ever used inside `LedReadout`. |
| `displayMedium` (LED secondary) | Bold | 34.sp | 38.sp | 0.5.sp | Secondary LED numbers — weight delta, run pace. |
| `titleLarge` | SemiBold | 20.sp | 26.sp | 1.5.sp, **uppercase in the composable, not the string** | Screen/section titles ("DASHBOARD", "THIS WEEK"). |
| `titleMedium` | SemiBold | 16.sp | 22.sp | 1.sp | Card headers, punch-card row primary label. |
| `bodyLarge` | Normal | 16.sp | 22.sp | 0.sp | Primary body copy, form labels. |
| `bodyMedium` | Normal | 14.sp | 20.sp | 0.sp | Secondary copy. |
| `labelSmall` (stamp) | Medium | 11.sp | 14.sp | 1.5.sp, **uppercase in the composable** | Ticket-stub tags, chip labels, punch-card row metadata ("RAN", "LOGGED", "5/7"). |
| `bodySmall` | Normal | 12.sp | 16.sp | 0.sp | Captions, timestamps. |

Uppercase transform is applied at the call site (`text.uppercase()`) rather than baked into the
`TextStyle`, since `TextStyle` has no case-transform property — note this explicitly in the two
composables below so it isn't dropped by accident.

## 4. Spacing scale

4dp base grid, named tokens in a new `Spacing.kt` (or as `object Spacing` inside `Shape.kt` if you
want fewer files — either is fine, name resolution is what matters):

```kotlin
object Spacing {
    val xxs = 4.dp   // hairline gaps, notch inset
    val xs = 8.dp    // tight internal padding, gap between grid dots
    val sm = 12.dp   // default internal card padding (small cards)
    val md = 16.dp   // default screen edge padding, default internal card padding
    val lg = 24.dp   // section gaps
    val xl = 32.dp   // major section gaps, onboarding step padding
    val xxl = 48.dp  // rare — top-of-screen breathing room above a hero LED readout
}
```

## 5. Shapes

`Shape.kt`:

```kotlin
package com.suprxsidh.deficit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val StrideShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)
```

Corners stay small and squared-off everywhere (2-8dp) — this is a deliberate anti-Material choice:
bubbly 16-28dp Material 3 default corners read as generic-AI-dashboard, and the ledger/punch-card
motif wants hard edges. The two custom composables below use their own shapes (a plain rounded
rect for the LED bezel, a custom notched `Path` for the punch-card row), not `StrideShapes`
directly — `StrideShapes` is for ordinary `Card`/`Button`/`TextField` surfaces on screens that
haven't been fully migrated yet.

## 6. Component: LED-readout stat display

File: `app/src/main/java/com/suprxsidh/deficit/ui/theme/component/LedReadout.kt`

Visual: a recessed black bezel (`StrideSurfaceSunken`) inset inside a slightly raised outer frame
(`StrideSurfaceRaised`), with the number rendered in ember, plus a soft glow drawn behind it to
fake backlight bloom (no `RenderEffect` blur — project `minSdk = 28`, `RenderEffect` needs API 31+,
so the glow is faked with 3-4 offset low-alpha copies of the same text instead of a real blur).

```kotlin
package com.suprxsidh.deficit.ui.theme.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.suprxsidh.deficit.ui.theme.StrideEmber
import com.suprxsidh.deficit.ui.theme.StrideEmberGlow
import com.suprxsidh.deficit.ui.theme.StrideOnSurfaceMuted
import com.suprxsidh.deficit.ui.theme.StrideOutline
import com.suprxsidh.deficit.ui.theme.StrideSurfaceRaised
import com.suprxsidh.deficit.ui.theme.StrideSurfaceSunken
import com.suprxsidh.deficit.ui.theme.Spacing

/**
 * A seven-segment-LED-style stat readout: a recessed black bezel with a glowing ember number.
 * [value] should already be formatted (e.g. "1,842" or "-0.4"), not raw numeric — this composable
 * only draws, it does not format. [label] is rendered above the number, uppercase, muted.
 * [style] defaults to the hero size; pass `MaterialTheme.typography.displayMedium` for secondary
 * readouts (weight delta, pace).
 */
@Composable
fun LedReadout(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    style: TextStyle = androidx.compose.material3.MaterialTheme.typography.displayLarge,
) {
    Box(
        modifier = modifier
            .background(StrideSurfaceRaised, RoundedCornerShape(8.dp))
            .padding(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .background(StrideSurfaceSunken, RoundedCornerShape(4.dp))
                .border(1.dp, StrideOutline, RoundedCornerShape(4.dp))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = label.uppercase(),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = StrideOnSurfaceMuted,
                )
                Box {
                    // Faked bloom: 4 offset low-alpha copies behind the real digit.
                    val offsets = listOf(-1.5f to 0f, 1.5f to 0f, 0f to -1.5f, 0f to 1.5f)
                    offsets.forEach { (dx, dy) ->
                        Text(
                            text = value,
                            style = style,
                            color = StrideEmberGlow,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.graphicsLayerOffset(dx, dy),
                        )
                    }
                    Text(text = value, style = style, color = StrideEmber)
                }
            }
        }
    }
}

private fun Modifier.graphicsLayerOffset(dx: Float, dy: Float): Modifier =
    this.then(
        androidx.compose.ui.layout.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.place(dx.toInt(), dy.toInt())
            }
        }
    )
```

Usage note: `value` must always be passed through `NumberFormat`/`String.format` with the target
locale before reaching this composable — tabular figures only look correct if digit count and
decimal alignment are already normalized (e.g. always show one decimal on weight delta: `"-0.4"`
not `"-.4"`).

## 7. Component: punch-card list row

File: `app/src/main/java/com/suprxsidh/deficit/ui/theme/component/PunchCardRow.kt`

Visual: a squared card with two semicircular notches cut out of the left and right edges at
vertical center — like a ticket stub or an IBM punch card. Implemented via `Path.op(...,
PathOperation.Difference)` in a custom `Shape`, so it composes with `Modifier.background(...)` and
`Modifier.clip(...)` like any other shape (no need to hand-draw the background in `drawBehind`).

```kotlin
package com.suprxsidh.deficit.ui.theme.component

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * A rounded rect with two semicircular notches punched into the left/right edges at vertical
 * center, radius [notchRadiusDp] (density-independent). Reads as a ticket stub / punch card.
 */
class PunchCardShape(private val notchRadiusDp: Float = 8f, private val cornerDp: Float = 4f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val notchR = with(density) { notchRadiusDp.dp.toPx() }
        val corner = with(density) { cornerDp.dp.toPx() }
        val base = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = 0f, top = 0f, right = size.width, bottom = size.height,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
                )
            )
        }
        val midY = size.height / 2f
        val leftNotch = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center = androidx.compose.ui.geometry.Offset(0f, midY), radius = notchR)) }
        val rightNotch = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center = androidx.compose.ui.geometry.Offset(size.width, midY), radius = notchR)) }
        val result = Path()
        result.op(base, leftNotch, PathOperation.Difference)
        result.op(result, rightNotch, PathOperation.Difference)
        return Outline.Generic(result)
    }
}

// dp extension used above — import androidx.compose.ui.unit.dp at call site if this
// isn't already in scope as a top-level extension in the same file.
```

`PunchCardRow.kt` (the composable that wraps it — this is what screens actually call):

```kotlin
package com.suprxsidh.deficit.ui.theme.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suprxsidh.deficit.ui.theme.Spacing
import com.suprxsidh.deficit.ui.theme.StrideOnSurface
import com.suprxsidh.deficit.ui.theme.StrideOutline
import com.suprxsidh.deficit.ui.theme.StrideSurface

/**
 * A punch-card-shaped list row. [leading] is typically a small label/stamp or dot cluster,
 * [trailing] typically a tabular-mono value (kcal, weight, time). Both slots are optional so
 * this covers everything from a food-log entry to a consistency-grid week-summary line.
 */
@Composable
fun PunchCardRow(
    modifier: Modifier = Modifier,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(StrideSurface, PunchCardShape())
            .border(1.dp, StrideOutline, PunchCardShape())
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        Row(modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs)) { content() }
        trailing?.invoke(this)
    }
}
```

The 8dp notch radius means rows need at least `Spacing.lg` (24dp) of horizontal padding so text
never collides with the punched-out curve — don't shrink that padding when reusing this on a
narrow screen.

## 8. Component: "start line" motif

File: `app/src/main/java/com/suprxsidh/deficit/ui/theme/component/StartLine.kt`

Visual: a thin checkered flag bar (alternating ember/near-black squares), used two ways:
1. **Divider** — a static full-width checkered strip between sections.
2. **Progress** — the same checkered strip, but only the fraction of squares up to `progress`
   render in ember; the rest render dim (`StrideEmberDim` at low alpha) — this doubles as the
   budget-remaining / weekly-target progress track, replacing `LinearProgressIndicator`.

```kotlin
package com.suprxsidh.deficit.ui.theme.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.suprxsidh.deficit.ui.theme.StrideEmber
import com.suprxsidh.deficit.ui.theme.StrideEmberDim

private const val SQUARE_COUNT = 24

/** Static checkered start-line divider — use as a section break, no semantic meaning. */
@Composable
fun StartLineDivider(modifier: Modifier = Modifier) {
    StartLineTrack(modifier = modifier, progress = 1f, checkeredWhenFull = true)
}

/** Progress variant — squares up to [progress] (0f..1f) render lit, the rest render dim. */
@Composable
fun StartLineProgress(progress: Float, modifier: Modifier = Modifier) {
    StartLineTrack(modifier = modifier, progress = progress.coerceIn(0f, 1f), checkeredWhenFull = false)
}

@Composable
private fun StartLineTrack(progress: Float, checkeredWhenFull: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
        val squareW = size.width / SQUARE_COUNT
        val litCount = (SQUARE_COUNT * progress).toInt()
        for (i in 0 until SQUARE_COUNT) {
            val lit = i < litCount
            // checkerboard alternation only matters visually when fully lit (pure divider mode);
            // in progress mode every lit square is solid ember, unlit squares are dim, no
            // alternation, so the fill boundary reads clearly as a progress edge.
            val color = when {
                checkeredWhenFull -> if (i % 2 == 0) StrideEmber else StrideEmberDim
                lit -> StrideEmber
                else -> StrideEmberDim
            }
            drawRect(
                color = color,
                topLeft = Offset(i * squareW, 0f),
                size = Size(squareW * 0.82f, size.height), // 0.82 leaves a visible gap between squares
            )
        }
    }
}
```

Use `StartLineProgress` anywhere the app currently has a `LinearProgressIndicator` (dashboard
budget bar, weekly target bar) and `StartLineDivider` anywhere it currently has a `Divider()` /
`HorizontalDivider()` or a bare `Spacer` between named sections.

## 9. Nav icons

Bottom nav (`MainActivity.kt`) currently uses stock Material icons (`Icons.Filled.Home`,
`DateRange`, `Settings`, etc. — see `Routes.kt` for the exact per-destination assignments). Redesign
direction: replace the *active*-state icon rendering with a small checkered-flag glyph (reuse the
`StartLineTrack` drawing logic at icon scale, 4x4 squares, fully lit in `StrideEmber`) instead of a
tinted stock icon; inactive tabs keep the stock Material icon outline in `StrideOnSurfaceMuted` (no
custom glyph needed for inactive — only the active indicator needs the motif, per "nav icons reuse
the flag/punch-card motifs"). This has **not been built yet** — noted here so a future session
doesn't have to re-derive it; implement as a small `NavFlagIcon(active: Boolean)` composable next to
the other three in `component/`.

## 10. Migration order and inheritance rule

1. Dashboard (highest traffic) — hero kcal-remaining as `LedReadout`, budget bar as
   `StartLineProgress`, weekly-commitment/motivation/run-summary cards as `PunchCardRow`-based
   layouts, section breaks as `StartLineDivider`.
2. Consistency — grid week-summary rows as `PunchCardRow`, month header as `titleLarge`
   (uppercase), day-cell dots keep their existing three-dot encoding but recolor
   (`StridePositive`/`StrideEmber`/`StrideOutline` instead of `MaterialTheme.colorScheme.primary`/
   `surfaceVariant`).
3. Onboarding — profile-entry numeric fields get `StrideMono`, "Get started"/step-continue buttons
   use the ember `primary` color already wired via `Theme.kt`, step separators use
   `StartLineDivider`.
4. Food logging, Weight, Settings, Health-Connect-setup — **inherit only**. Do not design bespoke
   layouts for these; swap their `Card`/`Divider`/`LinearProgressIndicator` usages for
   `PunchCardRow`/`StartLineDivider`/`StartLineProgress`, and any standalone numeric readout (a
   logged-food kcal value, the weight-chart current value, a run's pace) for `LedReadout` at
   `displayMedium` or smaller. If a screen has no natural fit for one of the three components,
   leave it as plain `StrideMono`-styled Material components rather than inventing new visual
   language.
