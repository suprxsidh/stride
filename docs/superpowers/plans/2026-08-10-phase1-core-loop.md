# Deficit Phase 1 (Core Loop) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a compiling, sideloadable debug APK covering the core loop of the Deficit app — onboarding, manual/OFF/custom food logging with the +10% buffer, a dashboard, and weight tracking with a rolling-average chart — with no Health Connect, Gemini, notifications, widget, or routines yet (those are later phases against the same `SPEC.md`).

**Architecture:** Single-module Android app, Kotlin + Jetpack Compose + Material 3, Room for local persistence, Retrofit + kotlinx.serialization for the Open Food Facts API. Packages: `data/` (entities, DAOs, database, calc utilities, repositories), `food/off/` (Open Food Facts client), `ui/` (screens, viewmodels, theme, nav). No `health/` package yet — Health Connect is Phase 2.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose (Compose BOM 2026.06.01), Material 3, Room 2.7.1, Retrofit 2.11.0 + kotlinx.serialization, AndroidX Navigation Compose 2.8.5, Lifecycle ViewModel Compose 2.8.7, Kotlin Coroutines 1.9.0, AGP 8.7.3, Gradle wrapper 8.9, Robolectric 4.13 for JVM-only DAO/UI tests, MockWebServer 4.12.0 for OFF client tests.

## Global Constraints

- Source spec of record: `SPEC.md` at the project root — every task below implements a named subsection of it. Do not re-derive requirements from memory; if this plan and `SPEC.md` conflict, that's a stop-and-ask, not a silent pick.
- `applicationId` / base package: `com.suprxsidh.deficit`. minSdk 28, compileSdk 36, targetSdk 36 (SPEC.md §2).
- Dark theme default: pure black `#000000` background, single saturated accent color `#39FF88` (a saturated green — the philosophy is "calm, not alarming"; this is the one specific hex the implementer should NOT invent a substitute for, since every screen in later phases must match it). Material 3 dark color scheme built from this pair (SPEC.md §2).
- All data stays on-device. Only network call in Phase 1 is Open Food Facts. No accounts, no analytics, no cloud sync (SPEC.md §2, §7).
- Calorie buffer: every logged food's raw kcal gets **+10%** applied automatically; store the raw value, display and budget against the buffered value, and show a line like "logged 450 → counted 495" (SPEC.md §3.3).
- Exercise credit is **50%** of real kcal — the math function is built and tested in Phase 1 (per SPEC.md §5/§6 data-agent scope) even though it has no real caller until Health Connect lands in Phase 2.
- BMR via Mifflin-St Jeor; TDEE = BMR × 1.2 (sedentary, always — never bakes in activity); soft budget = TDEE − 500, floored at 1500 kcal (SPEC.md §3.1).
- Day boundary is **3:00 AM** — an entry logged between midnight and 3 AM belongs to the previous calendar day for every "today" query (SPEC.md §4.4). This must be centralized in one function, never inlined ad hoc.
- Weight chart: raw points faint, 7-day rolling average bold; show total change since start and 4-week trend direction; **never** a projected date (SPEC.md §3.5, §1).
- Per-food macros are not required — calories only (SPEC.md §3.3).
- `CustomFood` supports fractional servings and an `isPinned` flag for up to 6 one-tap snack presets; logging a pinned snack must be ≤2 taps (SPEC.md §3.3).
- Open Food Facts: query the v2 API with `countries_tags_en=india` as a bias, not an exclusive filter; cache results locally (SPEC.md §3.3).
- Every screen must render meaningfully with zero data (day 1) — never a blank panel or spinner-forever (SPEC.md §4.9). Phase 1 has no permission-missing states to handle (those start in Phase 2 with Health Connect).
- The only required input in the whole app is onboarding (height, weight, age, sex) — every other Phase 1 feature must work with sensible defaults (SPEC.md §4.8).
- Verification approach for this plan: pure-logic and data-layer tasks (calc utilities, DAOs, repositories, the OFF client) follow strict TDD — write the failing test, watch it fail, implement, watch it pass. For screens: the ViewModel behind each screen (validation, state transitions, what it calls on the repository) is unit-tested with strict TDD; the `@Composable` screen itself is verified only by `./gradlew assembleDebug` succeeding, not by a Robolectric Compose UI test — Compose-under-Robolectric needs native graphics mode and extra plumbing that's a common source of flaky, low-value setup, and an emulator was explicitly ruled out of scope for Phase 1. Real visual verification happens once, on-device, when the user sideloads the Phase 1 APK at the end.
- Toolchain (already installed locally, do not `brew install` anything): `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`, `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`. Task 1 bakes both into the project (`gradle.properties`, `local.properties`) so no subagent needs to export shell env vars.
- If a pinned dependency version fails to resolve (Maven Central 404, etc.), bump only the patch version to the nearest available one and note it in your task report — do not change majors/minors without flagging it as a concern.

---

### Task 1: Gradle scaffold, theme, and navigation shell

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle.properties`
- Create: `local.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/suprxsidh/deficit/DeficitApp.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/MainActivity.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/theme/Color.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/theme/Type.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/nav/Routes.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/nav/DeficitNavHost.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/onboarding/OnboardingScreen.kt` (placeholder)
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/dashboard/DashboardScreen.kt` (placeholder)
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/food/FoodLogScreen.kt` (placeholder)
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/weight/WeightScreen.kt` (placeholder)
- Create: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `Routes` object with route constants `Routes.ONBOARDING = "onboarding"`, `Routes.DASHBOARD = "dashboard"`, `Routes.FOOD_LOG = "food_log"`, `Routes.WEIGHT = "weight"`. `DeficitNavHost(navController: NavHostController, startDestination: String)` composable. `DeficitTheme(content: @Composable () -> Unit)` composable wrapping `MaterialTheme` with the black+accent color scheme. Every placeholder screen composable takes no required params yet: `OnboardingScreen()`, `DashboardScreen()`, `FoodLogScreen()`, `WeightScreen()` — later tasks will add real parameters/viewmodels, replacing the bodies.
- Consumes: nothing (first task).

- [ ] **Step 1: Generate the Gradle wrapper**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home /opt/homebrew/bin/gradle wrapper --gradle-version 8.9` from the project root (`~/claudecode-projects/deficit`). This creates `gradlew`, `gradlew.bat`, and `gradle/wrapper/`. From this point on, use `./gradlew` (it carries its own Gradle 8.9, independent of the system `gradle` binary).

- [ ] **Step 2: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Deficit"
include(":app")
```

- [ ] **Step 3: Write root `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}
```

- [ ] **Step 4: Write `gradle.properties`**

```properties
org.gradle.java.home=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
android.useAndroidX=true
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.nonTransitiveRClass=true
```

- [ ] **Step 5: Write `local.properties`**

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

- [ ] **Step 6: Write `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.suprxsidh.deficit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.suprxsidh.deficit"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-phase1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

- [ ] **Step 7: Write `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".DeficitApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Deficit"
        android:theme="@android:style/Theme.Material.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@android:style/Theme.Material.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 8: Write `app/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Deficit</string>
</resources>
```

- [ ] **Step 9: Write `ui/theme/Color.kt`**

```kotlin
package com.suprxsidh.deficit.ui.theme

import androidx.compose.ui.graphics.Color

val DeficitBlack = Color(0xFF000000)
val DeficitAccent = Color(0xFF39FF88)
val DeficitSurface = Color(0xFF121212)
val DeficitOnBlack = Color(0xFFEAEAEA)
val DeficitError = Color(0xFFCF6679)
```

- [ ] **Step 10: Write `ui/theme/Type.kt`**

```kotlin
package com.suprxsidh.deficit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val DeficitTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
)
```

- [ ] **Step 11: Write `ui/theme/Theme.kt`**

```kotlin
package com.suprxsidh.deficit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DeficitColorScheme = darkColorScheme(
    primary = DeficitAccent,
    onPrimary = DeficitBlack,
    background = DeficitBlack,
    onBackground = DeficitOnBlack,
    surface = DeficitSurface,
    onSurface = DeficitOnBlack,
    error = DeficitError
)

@Composable
fun DeficitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DeficitColorScheme,
        typography = DeficitTypography,
        content = content
    )
}
```

- [ ] **Step 12: Write `ui/nav/Routes.kt`**

```kotlin
package com.suprxsidh.deficit.ui.nav

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val FOOD_LOG = "food_log"
    const val WEIGHT = "weight"
}
```

- [ ] **Step 13: Write placeholder screens**

`ui/onboarding/OnboardingScreen.kt`:
```kotlin
package com.suprxsidh.deficit.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OnboardingScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text("Onboarding — Task 5 replaces this")
    }
}
```

`ui/dashboard/DashboardScreen.kt`:
```kotlin
package com.suprxsidh.deficit.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DashboardScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text("Dashboard — Task 8 replaces this")
    }
}
```

`ui/food/FoodLogScreen.kt`:
```kotlin
package com.suprxsidh.deficit.ui.food

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FoodLogScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text("Food log — Task 7 replaces this")
    }
}
```

`ui/weight/WeightScreen.kt`:
```kotlin
package com.suprxsidh.deficit.ui.weight

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun WeightScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text("Weight — Task 9 replaces this")
    }
}
```

- [ ] **Step 14: Write `ui/nav/DeficitNavHost.kt`**

```kotlin
package com.suprxsidh.deficit.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.suprxsidh.deficit.ui.dashboard.DashboardScreen
import com.suprxsidh.deficit.ui.food.FoodLogScreen
import com.suprxsidh.deficit.ui.onboarding.OnboardingScreen
import com.suprxsidh.deficit.ui.weight.WeightScreen

@Composable
fun DeficitNavHost(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) { OnboardingScreen() }
        composable(Routes.DASHBOARD) { DashboardScreen() }
        composable(Routes.FOOD_LOG) { FoodLogScreen() }
        composable(Routes.WEIGHT) { WeightScreen() }
    }
}
```

- [ ] **Step 15: Write `DeficitApp.kt` and `MainActivity.kt`**

```kotlin
package com.suprxsidh.deficit

import android.app.Application

class DeficitApp : Application()
```

```kotlin
package com.suprxsidh.deficit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.suprxsidh.deficit.ui.nav.DeficitNavHost
import com.suprxsidh.deficit.ui.nav.Routes
import com.suprxsidh.deficit.ui.theme.DeficitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeficitTheme {
                val navController = rememberNavController()
                DeficitNavHost(navController = navController, startDestination = Routes.ONBOARDING)
            }
        }
    }
}
```

- [ ] **Step 16: Build and verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`, with `app/build/outputs/apk/debug/app-debug.apk` produced.

- [ ] **Step 17: Commit**

```bash
git add -A
git commit -m "Task 1: Gradle scaffold, black+accent theme, nav shell with placeholder screens"
```

### Task 2: Core calculation utilities (buffer/credit math, BMR/TDEE, day boundary, rolling average)

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/calc/Sex.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/calc/CalorieMath.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/calc/DayBoundary.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/calc/RollingAverage.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/calc/CalorieMathTest.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/calc/DayBoundaryTest.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/calc/RollingAverageTest.kt`

**Interfaces:**
- Produces: `enum class Sex { MALE, FEMALE }`. `object CalorieMath` with `fun bufferedKcal(rawKcal: Int): Int`, `fun creditedExerciseKcal(realKcal: Int): Int`, `fun bmr(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Double`, `fun tdee(bmr: Double): Double`, `fun softBudgetKcal(tdee: Double): Int`. `object DayBoundary` with `fun logicalDate(dateTime: java.time.LocalDateTime): java.time.LocalDate`. `data class WeighInPoint(val date: java.time.LocalDate, val weightKg: Double)` and `object RollingAverage` with `fun sevenDayRollingAverage(points: List<WeighInPoint>): List<Pair<java.time.LocalDate, Double>>` (one output point per input point, ordered by date ascending, each averaged over the trailing 7-day window ending on that date).
- Consumes: nothing beyond Task 1's Kotlin/JVM setup (pure logic, no Android framework classes — these tests run as plain JUnit, not Robolectric).

- [ ] **Step 1: Write the failing tests for `CalorieMath`**

```kotlin
package com.suprxsidh.deficit.data.calc

import org.junit.Assert.assertEquals
import org.junit.Test

class CalorieMathTest {

    @Test
    fun `buffered kcal adds 10 percent rounded up`() {
        assertEquals(495, CalorieMath.bufferedKcal(450))
        assertEquals(257, CalorieMath.bufferedKcal(233)) // 256.3 -> ceil verifies rounding path
        assertEquals(0, CalorieMath.bufferedKcal(0))
    }

    @Test
    fun `credited exercise kcal is half of real, rounded down`() {
        assertEquals(150, CalorieMath.creditedExerciseKcal(300))
        assertEquals(150, CalorieMath.creditedExerciseKcal(301))
        assertEquals(0, CalorieMath.creditedExerciseKcal(0))
    }

    @Test
    fun `bmr uses Mifflin-St Jeor with sex-specific constant`() {
        val maleBmr = CalorieMath.bmr(weightKg = 80.0, heightCm = 178.0, age = 26, sex = Sex.MALE)
        assertEquals(1787.5, maleBmr, 0.01)

        val femaleBmr = CalorieMath.bmr(weightKg = 65.0, heightCm = 165.0, age = 26, sex = Sex.FEMALE)
        assertEquals(1390.25, femaleBmr, 0.01)
    }

    @Test
    fun `tdee is bmr times 1_2 sedentary always`() {
        assertEquals(2145.0, CalorieMath.tdee(1787.5), 0.01)
    }

    @Test
    fun `soft budget is tdee minus 500 floored at 1500`() {
        assertEquals(1645, CalorieMath.softBudgetKcal(2145.0))
        assertEquals(1500, CalorieMath.softBudgetKcal(1900.0)) // 1900-500=1400 < floor
        assertEquals(1500, CalorieMath.softBudgetKcal(1200.0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.calc.CalorieMathTest"`
Expected: FAIL — `CalorieMath`, `Sex` unresolved references (files don't exist yet).

- [ ] **Step 3: Write `Sex.kt` and `CalorieMath.kt`**

```kotlin
package com.suprxsidh.deficit.data.calc

enum class Sex { MALE, FEMALE }
```

```kotlin
package com.suprxsidh.deficit.data.calc

import kotlin.math.floor

object CalorieMath {

    // Integer ceiling-division (rawKcal * 11 + 9) / 10, NOT ceil(rawKcal * 1.10) —
    // the double multiplication drifts above exact values (450*1.10 == 495.00000000000006
    // in IEEE 754), which makes ceil() overshoot by 1 on round numbers. Pure integer
    // math has no such drift.
    fun bufferedKcal(rawKcal: Int): Int = (rawKcal * 11 + 9) / 10

    fun creditedExerciseKcal(realKcal: Int): Int = floor(realKcal / 2.0).toInt()

    fun bmr(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * age
        return when (sex) {
            Sex.MALE -> base + 5
            Sex.FEMALE -> base - 161
        }
    }

    fun tdee(bmr: Double): Double = bmr * 1.2

    fun softBudgetKcal(tdee: Double): Int {
        val raw = floor(tdee - 500).toInt()
        return maxOf(1500, raw)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.calc.CalorieMathTest"`
Expected: PASS (5/5).

- [ ] **Step 5: Write the failing test for `DayBoundary`**

```kotlin
package com.suprxsidh.deficit.data.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DayBoundaryTest {

    @Test
    fun `entry logged at 11pm belongs to that calendar day`() {
        val dt = LocalDateTime.of(2026, 8, 10, 23, 0)
        assertEquals(LocalDate.of(2026, 8, 10), DayBoundary.logicalDate(dt))
    }

    @Test
    fun `entry logged at 1am belongs to the previous calendar day`() {
        val dt = LocalDateTime.of(2026, 8, 11, 1, 0)
        assertEquals(LocalDate.of(2026, 8, 10), DayBoundary.logicalDate(dt))
    }

    @Test
    fun `entry logged exactly at 3am belongs to the new calendar day`() {
        val dt = LocalDateTime.of(2026, 8, 11, 3, 0)
        assertEquals(LocalDate.of(2026, 8, 11), DayBoundary.logicalDate(dt))
    }

    @Test
    fun `entry logged at 2_59am belongs to the previous calendar day`() {
        val dt = LocalDateTime.of(2026, 8, 11, 2, 59)
        assertEquals(LocalDate.of(2026, 8, 10), DayBoundary.logicalDate(dt))
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.calc.DayBoundaryTest"`
Expected: FAIL — `DayBoundary` unresolved reference.

- [ ] **Step 7: Write `DayBoundary.kt`**

```kotlin
package com.suprxsidh.deficit.data.calc

import java.time.LocalDate
import java.time.LocalDateTime

object DayBoundary {
    const val BOUNDARY_HOUR = 3

    fun logicalDate(dateTime: LocalDateTime): LocalDate =
        if (dateTime.hour < BOUNDARY_HOUR) dateTime.toLocalDate().minusDays(1) else dateTime.toLocalDate()
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.calc.DayBoundaryTest"`
Expected: PASS (4/4).

- [ ] **Step 9: Write the failing test for `RollingAverage`**

```kotlin
package com.suprxsidh.deficit.data.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RollingAverageTest {

    @Test
    fun `single point average equals itself`() {
        val points = listOf(WeighInPoint(LocalDate.of(2026, 8, 1), 80.0))
        val result = RollingAverage.sevenDayRollingAverage(points)
        assertEquals(1, result.size)
        assertEquals(80.0, result[0].second, 0.001)
    }

    @Test
    fun `average only includes points within trailing 7 days`() {
        val points = listOf(
            WeighInPoint(LocalDate.of(2026, 8, 1), 82.0),
            WeighInPoint(LocalDate.of(2026, 8, 5), 81.0),
            WeighInPoint(LocalDate.of(2026, 8, 12), 79.0) // more than 7 days after Aug 1 — falls outside window ending Aug 12
        )
        val result = RollingAverage.sevenDayRollingAverage(points)
        val lastPoint = result.last()
        assertEquals(LocalDate.of(2026, 8, 12), lastPoint.first)
        // window for Aug 12 is Aug 6..Aug 12: only the Aug 12 point itself qualifies
        assertEquals(79.0, lastPoint.second, 0.001)
    }

    @Test
    fun `gaps do not skew the average toward missing days`() {
        val points = listOf(
            WeighInPoint(LocalDate.of(2026, 8, 1), 80.0),
            WeighInPoint(LocalDate.of(2026, 8, 2), 80.0),
            WeighInPoint(LocalDate.of(2026, 8, 7), 78.0)
        )
        val result = RollingAverage.sevenDayRollingAverage(points)
        val lastPoint = result.last()
        assertEquals(LocalDate.of(2026, 8, 7), lastPoint.first)
        // window Aug 1..Aug 7 includes all three logged points, averaged over 3 (not 7)
        assertEquals((80.0 + 80.0 + 78.0) / 3, lastPoint.second, 0.001)
    }
}
```

- [ ] **Step 10: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.calc.RollingAverageTest"`
Expected: FAIL — `WeighInPoint`, `RollingAverage` unresolved references.

- [ ] **Step 11: Write `RollingAverage.kt`**

```kotlin
package com.suprxsidh.deficit.data.calc

import java.time.LocalDate

data class WeighInPoint(val date: LocalDate, val weightKg: Double)

object RollingAverage {

    fun sevenDayRollingAverage(points: List<WeighInPoint>): List<Pair<LocalDate, Double>> {
        val sorted = points.sortedBy { it.date }
        return sorted.map { point ->
            val windowStart = point.date.minusDays(6)
            val window = sorted.filter { !it.date.isBefore(windowStart) && !it.date.isAfter(point.date) }
            point.date to window.map { it.weightKg }.average()
        }
    }
}
```

- [ ] **Step 12: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.calc.RollingAverageTest"`
Expected: PASS (3/3).

- [ ] **Step 13: Run the full calc test package**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.calc.*"`
Expected: PASS (12/12 total across the three classes).

- [ ] **Step 14: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/calc app/src/test/java/com/suprxsidh/deficit/data/calc
git commit -m "Task 2: calorie/BMR/TDEE math, 3am day boundary, 7-day rolling average"
```

### Task 3: Room entities, DAOs, and database

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/entity/UserProfileEntity.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/entity/FoodEntryEntity.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/entity/CustomFoodEntity.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/entity/WeighInEntity.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/dao/UserProfileDao.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/dao/FoodEntryDao.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/dao/CustomFoodDao.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/dao/WeighInDao.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/DeficitDatabase.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/db/dao/FoodEntryDaoTest.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/db/dao/WeighInDaoTest.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/db/dao/CustomFoodDaoTest.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/db/dao/UserProfileDaoTest.kt`

**Interfaces:**
- Produces: `UserProfileEntity(id: Int = 1, heightCm: Double, weightKgAtStart: Double, age: Int, sex: String, goalWeightKg: Double, softBudgetKcal: Int, createdAt: Long)`. `FoodEntryEntity(id: Long = 0, date: String, name: String, rawKcal: Int, bufferedKcal: Int, source: String, offBarcode: String?, loggedAt: Long)` — `date` is the ISO `LocalDate.toString()` **logical** date from `DayBoundary.logicalDate` (Task 2), `loggedAt` is the real epoch-millis timestamp. `source` is one of the strings `"QUICK"`, `"OFF"`, `"CUSTOM"` (Phase 1 — `"AI"` is reserved for Phase 2, do not build a path that produces it yet). `CustomFoodEntity(id: Long = 0, name: String, kcalPerServing: Int, servingLabel: String, isPinned: Boolean = false)`. `WeighInEntity(id: Long = 0, date: String, weightKg: Double, syncedToHc: Boolean = false)` — `syncedToHc` always false in Phase 1, reserved for Phase 2's Health Connect write-back.
  `UserProfileDao`: `fun observe(): Flow<UserProfileEntity?>`, `suspend fun get(): UserProfileEntity?`, `suspend fun upsert(profile: UserProfileEntity)`.
  `FoodEntryDao`: `suspend fun insert(entry: FoodEntryEntity): Long`, `fun observeForDate(date: String): Flow<List<FoodEntryEntity>>`, `fun observeBufferedTotalForDate(date: String): Flow<Int>`, `suspend fun delete(entry: FoodEntryEntity)`.
  `CustomFoodDao`: `fun observeAll(): Flow<List<CustomFoodEntity>>`, `fun observePinned(): Flow<List<CustomFoodEntity>>` (max 6, per SPEC.md §3.3), `suspend fun upsert(food: CustomFoodEntity): Long`, `suspend fun delete(food: CustomFoodEntity)`.
  `WeighInDao`: `suspend fun upsert(weighIn: WeighInEntity): Long`, `fun observeAll(): Flow<List<WeighInEntity>>`, `suspend fun getForDate(date: String): WeighInEntity?`.
  `DeficitDatabase` (abstract `RoomDatabase`): exposes `userProfileDao()`, `foodEntryDao()`, `customFoodDao()`, `weighInDao()`; companion `getInstance(context: Context): DeficitDatabase` (singleton, `Room.databaseBuilder(..., "deficit.db")`).
- Consumes: nothing from Task 2 directly (entities store plain strings/longs; Task 4's repositories are what call into `CalorieMath`/`DayBoundary`/`RollingAverage`).

- [ ] **Step 1: Write the entities**

`data/db/entity/UserProfileEntity.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val heightCm: Double,
    val weightKgAtStart: Double,
    val age: Int,
    val sex: String,
    val goalWeightKg: Double,
    val softBudgetKcal: Int,
    val createdAt: Long
)
```

`data/db/entity/FoodEntryEntity.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_entry")
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val name: String,
    val rawKcal: Int,
    val bufferedKcal: Int,
    val source: String,
    val offBarcode: String?,
    val loggedAt: Long
)
```

`data/db/entity/CustomFoodEntity.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_food")
data class CustomFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kcalPerServing: Int,
    val servingLabel: String,
    val isPinned: Boolean = false
)
```

`data/db/entity/WeighInEntity.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weigh_in")
data class WeighInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weightKg: Double,
    val syncedToHc: Boolean = false
)
```

- [ ] **Step 2: Write the DAOs**

`data/db/dao/UserProfileDao.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)
}
```

`data/db/dao/FoodEntryDao.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {
    @Insert
    suspend fun insert(entry: FoodEntryEntity): Long

    @Query("SELECT * FROM food_entry WHERE date = :date ORDER BY loggedAt ASC")
    fun observeForDate(date: String): Flow<List<FoodEntryEntity>>

    @Query("SELECT COALESCE(SUM(bufferedKcal), 0) FROM food_entry WHERE date = :date")
    fun observeBufferedTotalForDate(date: String): Flow<Int>

    @Delete
    suspend fun delete(entry: FoodEntryEntity)
}
```

`data/db/dao/CustomFoodDao.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFoodDao {
    @Query("SELECT * FROM custom_food ORDER BY name ASC")
    fun observeAll(): Flow<List<CustomFoodEntity>>

    @Query("SELECT * FROM custom_food WHERE isPinned = 1 ORDER BY name ASC LIMIT 6")
    fun observePinned(): Flow<List<CustomFoodEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: CustomFoodEntity): Long

    @Delete
    suspend fun delete(food: CustomFoodEntity)
}
```

`data/db/dao/WeighInDao.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeighInDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(weighIn: WeighInEntity): Long

    @Query("SELECT * FROM weigh_in ORDER BY date ASC")
    fun observeAll(): Flow<List<WeighInEntity>>

    @Query("SELECT * FROM weigh_in WHERE date = :date LIMIT 1")
    suspend fun getForDate(date: String): WeighInEntity?
}
```

- [ ] **Step 3: Write `DeficitDatabase.kt`**

```kotlin
package com.suprxsidh.deficit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.suprxsidh.deficit.data.db.dao.CustomFoodDao
import com.suprxsidh.deficit.data.db.dao.FoodEntryDao
import com.suprxsidh.deficit.data.db.dao.UserProfileDao
import com.suprxsidh.deficit.data.db.dao.WeighInDao
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.db.entity.WeighInEntity

@Database(
    entities = [UserProfileEntity::class, FoodEntryEntity::class, CustomFoodEntity::class, WeighInEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DeficitDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun weighInDao(): WeighInDao

    companion object {
        @Volatile private var INSTANCE: DeficitDatabase? = null

        fun getInstance(context: Context): DeficitDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DeficitDatabase::class.java,
                    "deficit.db"
                ).build().also { INSTANCE = it }
            }
    }
}
```

- [ ] **Step 4: Write the failing DAO tests**

`data/db/dao/FoodEntryDaoTest.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodEntryDaoTest {
    private lateinit var db: DeficitDatabase
    private lateinit var dao: FoodEntryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.foodEntryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `buffered total for date sums only that date's entries`() = runTest {
        dao.insert(FoodEntryEntity(date = "2026-08-10", name = "Roti+Dal", rawKcal = 450, bufferedKcal = 495, source = "QUICK", offBarcode = null, loggedAt = 1L))
        dao.insert(FoodEntryEntity(date = "2026-08-10", name = "Chaas", rawKcal = 80, bufferedKcal = 88, source = "CUSTOM", offBarcode = null, loggedAt = 2L))
        dao.insert(FoodEntryEntity(date = "2026-08-11", name = "Other day", rawKcal = 300, bufferedKcal = 330, source = "QUICK", offBarcode = null, loggedAt = 3L))

        val total = dao.observeBufferedTotalForDate("2026-08-10").first()
        assertEquals(583, total)
    }

    @Test
    fun `buffered total for a date with no entries is zero, not null`() = runTest {
        val total = dao.observeBufferedTotalForDate("2026-01-01").first()
        assertEquals(0, total)
    }

    @Test
    fun `observeForDate returns entries ordered by loggedAt`() = runTest {
        dao.insert(FoodEntryEntity(date = "2026-08-10", name = "Second", rawKcal = 100, bufferedKcal = 110, source = "QUICK", offBarcode = null, loggedAt = 200L))
        dao.insert(FoodEntryEntity(date = "2026-08-10", name = "First", rawKcal = 50, bufferedKcal = 55, source = "QUICK", offBarcode = null, loggedAt = 100L))

        val entries = dao.observeForDate("2026-08-10").first()
        assertEquals(listOf("First", "Second"), entries.map { it.name })
    }
}
```

`data/db/dao/WeighInDaoTest.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeighInDaoTest {
    private lateinit var db: DeficitDatabase
    private lateinit var dao: WeighInDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.weighInDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert then getForDate returns the weigh-in`() = runTest {
        dao.upsert(WeighInEntity(date = "2026-08-10", weightKg = 81.4))
        val result = dao.getForDate("2026-08-10")
        assertEquals(81.4, result!!.weightKg, 0.001)
    }

    @Test
    fun `getForDate on a day with no entry returns null`() = runTest {
        assertNull(dao.getForDate("2099-01-01"))
    }

    @Test
    fun `observeAll returns entries ordered by date ascending`() = runTest {
        dao.upsert(WeighInEntity(date = "2026-08-11", weightKg = 81.0))
        dao.upsert(WeighInEntity(date = "2026-08-09", weightKg = 82.0))
        val all = dao.observeAll().first()
        assertEquals(listOf("2026-08-09", "2026-08-11"), all.map { it.date })
    }
}
```

`data/db/dao/CustomFoodDaoTest.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomFoodDaoTest {
    private lateinit var db: DeficitDatabase
    private lateinit var dao: CustomFoodDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.customFoodDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `observePinned only returns pinned foods, capped at 6`() = runTest {
        repeat(7) { i ->
            dao.upsert(CustomFoodEntity(name = "Pinned$i", kcalPerServing = 100, servingLabel = "1 cup", isPinned = true))
        }
        dao.upsert(CustomFoodEntity(name = "NotPinned", kcalPerServing = 50, servingLabel = "1 pc", isPinned = false))

        val pinned = dao.observePinned().first()
        assertEquals(6, pinned.size)
        assertTrue(pinned.all { it.name.startsWith("Pinned") })
    }

    @Test
    fun `observeAll includes both pinned and unpinned, ordered by name`() = runTest {
        dao.upsert(CustomFoodEntity(name = "Zucchini snack", kcalPerServing = 40, servingLabel = "1 cup"))
        dao.upsert(CustomFoodEntity(name = "Almonds", kcalPerServing = 160, servingLabel = "28g"))

        val all = dao.observeAll().first()
        assertEquals(listOf("Almonds", "Zucchini snack"), all.map { it.name })
    }
}
```

`data/db/dao/UserProfileDaoTest.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserProfileDaoTest {
    private lateinit var db: DeficitDatabase
    private lateinit var dao: UserProfileDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.userProfileDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `get returns null before onboarding`() = runTest {
        assertNull(dao.get())
    }

    @Test
    fun `upsert replaces the single row (id is always 1)`() = runTest {
        dao.upsert(UserProfileEntity(heightCm = 178.0, weightKgAtStart = 80.0, age = 26, sex = "MALE", goalWeightKg = 70.0, softBudgetKcal = 1645, createdAt = 1L))
        dao.upsert(UserProfileEntity(heightCm = 178.0, weightKgAtStart = 79.5, age = 26, sex = "MALE", goalWeightKg = 70.0, softBudgetKcal = 1645, createdAt = 1L))

        val result = dao.get()
        assertEquals(79.5, result!!.weightKgAtStart, 0.001)
    }
}
```

- [ ] **Step 5: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.db.dao.*"`
Expected: FAIL to compile — none of the entities/DAOs/database exist yet.

- [ ] **Step 6: Confirm Step 1-3's code is in place, then run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.db.dao.*"`
Expected: PASS (10/10 across the four DAO test classes).

- [ ] **Step 7: Build check**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/db app/src/test/java/com/suprxsidh/deficit/data/db
git commit -m "Task 3: Room entities, DAOs, and database for profile/food/custom-food/weigh-in"
```

### Task 4: Repository layer (profile, food, weight) + app container wiring

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/repository/UserProfileRepository.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/repository/FoodRepository.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/repository/WeightRepository.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/AppContainer.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/DeficitApp.kt` (holds the `AppContainer`)
- Test: `app/src/test/java/com/suprxsidh/deficit/data/repository/UserProfileRepositoryTest.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/repository/FoodRepositoryTest.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/repository/WeightRepositoryTest.kt`

**Interfaces:**
- Produces: `UserProfileRepository(userProfileDao: UserProfileDao, clock: () -> LocalDateTime = { LocalDateTime.now() })` with `fun observeProfile(): Flow<UserProfileEntity?>`, `suspend fun getProfile(): UserProfileEntity?`, `suspend fun completeOnboarding(heightCm: Double, weightKg: Double, age: Int, sex: Sex, goalWeightKg: Double? = null): UserProfileEntity`.
  `FoodRepository(foodEntryDao: FoodEntryDao, customFoodDao: CustomFoodDao, clock: () -> LocalDateTime = { LocalDateTime.now() })` with `suspend fun logQuickAdd(name: String, rawKcal: Int): FoodEntryEntity`, `suspend fun logCustomFood(food: CustomFoodEntity, servings: Double): FoodEntryEntity`, `suspend fun logOffProduct(name: String, rawKcal: Int, barcode: String): FoodEntryEntity`, `fun observeTodayEntries(): Flow<List<FoodEntryEntity>>`, `fun observeTodayBufferedTotal(): Flow<Int>`, `fun observeAllCustomFoods(): Flow<List<CustomFoodEntity>>`, `fun observePinnedCustomFoods(): Flow<List<CustomFoodEntity>>`, `suspend fun upsertCustomFood(food: CustomFoodEntity): Long`, `suspend fun deleteCustomFood(food: CustomFoodEntity)`, `suspend fun deleteFoodEntry(entry: FoodEntryEntity)`.
  `WeightRepository(weighInDao: WeighInDao, clock: () -> LocalDateTime = { LocalDateTime.now() })` with `suspend fun logWeighIn(weightKg: Double): WeighInEntity`, `fun observeRollingAverageSeries(): Flow<List<Pair<LocalDate, Double>>>`, `fun observeRawSeries(): Flow<List<Pair<LocalDate, Double>>>`, `fun observeTotalChangeSinceStart(): Flow<Double?>`, `fun observeFourWeekTrend(): Flow<TrendDirection?>`. `enum class TrendDirection { UP, DOWN, FLAT }` lives in this same file.
  `AppContainer(context: Context)` exposes vals `userProfileRepository`, `foodRepository`, `weightRepository`, built from `DeficitDatabase.getInstance(context)`. `DeficitApp` exposes `val container: AppContainer` (set in `onCreate`).
- Consumes: `CalorieMath`, `DayBoundary`, `RollingAverage`, `WeighInPoint`, `Sex` from Task 2; all entities/DAOs/`DeficitDatabase` from Task 3.

- [ ] **Step 1: Write the failing repository tests**

`data/repository/UserProfileRepositoryTest.kt`:
```kotlin
package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.db.DeficitDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserProfileRepositoryTest {
    private lateinit var db: DeficitDatabase
    private lateinit var repo: UserProfileRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = UserProfileRepository(db.userProfileDao()) { LocalDateTime.of(2026, 8, 10, 9, 0) }
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `completing onboarding computes soft budget from Mifflin-St Jeor and stores it`() = runTest {
        val profile = repo.completeOnboarding(heightCm = 178.0, weightKg = 80.0, age = 26, sex = Sex.MALE)
        assertEquals(1645, profile.softBudgetKcal)
        assertEquals(70.0, profile.goalWeightKg, 0.001) // default = weightKg - 10
    }

    @Test
    fun `explicit goal weight overrides the default`() = runTest {
        val profile = repo.completeOnboarding(heightCm = 165.0, weightKg = 65.0, age = 26, sex = Sex.FEMALE, goalWeightKg = 60.0)
        assertEquals(60.0, profile.goalWeightKg, 0.001)
    }
}
```

`data/repository/FoodRepositoryTest.kt`:
```kotlin
package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodRepositoryTest {
    private lateinit var db: DeficitDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `quick add applies the 10 percent buffer and lands in today's total`() = runTest {
        val repo = FoodRepository(db.foodEntryDao(), db.customFoodDao()) { LocalDateTime.of(2026, 8, 10, 20, 0) }
        repo.logQuickAdd("2 rotis, dal", 450)

        val total = repo.observeTodayBufferedTotal().first()
        assertEquals(495, total)
    }

    @Test
    fun `an entry logged at 1am counts toward the previous day, not today`() = runTest {
        val lateNightClock = { LocalDateTime.of(2026, 8, 11, 1, 0) } // 1am -> logical date is Aug 10
        val repo = FoodRepository(db.foodEntryDao(), db.customFoodDao(), lateNightClock)
        repo.logQuickAdd("Late dinner", 300)

        // "today" per this same clock is still Aug 10 (before the 3am boundary), so the entry shows up
        val total = repo.observeTodayBufferedTotal().first()
        assertEquals(330, total)

        // and it's really stored against 2026-08-10, not 2026-08-11
        val stored = db.foodEntryDao().observeForDate("2026-08-10").first()
        assertEquals(1, stored.size)
    }

    @Test
    fun `logging a custom food multiplies kcal per serving by fractional servings`() = runTest {
        val repo = FoodRepository(db.foodEntryDao(), db.customFoodDao()) { LocalDateTime.of(2026, 8, 10, 12, 0) }
        val chaas = CustomFoodEntity(name = "Chaas", kcalPerServing = 100, servingLabel = "1 glass", isPinned = true)
        val entry = repo.logCustomFood(chaas, servings = 1.5)

        assertEquals(150, entry.rawKcal)
        assertEquals(165, entry.bufferedKcal)
    }
}
```

`data/repository/WeightRepositoryTest.kt`:
```kotlin
package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeightRepositoryTest {
    private lateinit var db: DeficitDatabase
    private lateinit var repo: WeightRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = WeightRepository(db.weighInDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `logging a second weigh-in same day overwrites, not duplicates`() = runTest {
        val sameDayClock = { LocalDateTime.of(2026, 8, 10, 7, 0) }
        val repoWithClock = WeightRepository(db.weighInDao(), sameDayClock)
        repoWithClock.logWeighIn(81.8)
        repoWithClock.logWeighIn(81.4) // correction later same morning

        val all = db.weighInDao().observeAll().first()
        assertEquals(1, all.size)
        assertEquals(81.4, all[0].weightKg, 0.001)
    }

    @Test
    fun `total change since start is latest minus first, null with fewer than 2 points`() = runTest {
        assertEquals(null, repo.observeTotalChangeSinceStart().first())

        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-07-01", weightKg = 85.0))
        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-08-10", weightKg = 81.0))

        val change = repo.observeTotalChangeSinceStart().first()
        assertEquals(-4.0, change!!, 0.001)
    }

    @Test
    fun `rolling average series delegates to RollingAverage over stored weigh-ins`() = runTest {
        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-08-01", weightKg = 82.0))
        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-08-02", weightKg = 80.0))

        val series = repo.observeRollingAverageSeries().first()
        assertEquals(2, series.size)
        assertEquals(81.0, series.last().second, 0.001) // average of the two points, both within the trailing window
    }

    @Test
    fun `four-week trend is DOWN when the reference point ~28 days back is meaningfully heavier`() = runTest {
        // Points are spaced more than 7 days apart, so each one's own rolling average equals itself --
        // this isolates the trend-window logic from the rolling-average logic already covered above.
        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-07-01", weightKg = 85.0))
        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-07-13", weightKg = 85.0)) // ~28 days before the latest point
        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-08-10", weightKg = 81.0))

        val trend = repo.observeFourWeekTrend().first()
        assertEquals(TrendDirection.DOWN, trend)
    }

    @Test
    fun `four-week trend is null with fewer than 2 rolling-average points`() = runTest {
        db.weighInDao().upsert(com.suprxsidh.deficit.data.db.entity.WeighInEntity(date = "2026-08-10", weightKg = 81.0))
        assertEquals(null, repo.observeFourWeekTrend().first())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.repository.*"`
Expected: FAIL to compile — none of the repositories exist yet.

- [ ] **Step 3: Write `UserProfileRepository.kt`**

```kotlin
package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.calc.CalorieMath
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.db.dao.UserProfileDao
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.ZoneId

class UserProfileRepository(
    private val userProfileDao: UserProfileDao,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {
    fun observeProfile(): Flow<UserProfileEntity?> = userProfileDao.observe()
    suspend fun getProfile(): UserProfileEntity? = userProfileDao.get()

    suspend fun completeOnboarding(
        heightCm: Double,
        weightKg: Double,
        age: Int,
        sex: Sex,
        goalWeightKg: Double? = null
    ): UserProfileEntity {
        val bmr = CalorieMath.bmr(weightKg, heightCm, age, sex)
        val tdee = CalorieMath.tdee(bmr)
        val entity = UserProfileEntity(
            heightCm = heightCm,
            weightKgAtStart = weightKg,
            age = age,
            sex = sex.name,
            goalWeightKg = goalWeightKg ?: (weightKg - 10.0),
            softBudgetKcal = CalorieMath.softBudgetKcal(tdee),
            createdAt = clock().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        userProfileDao.upsert(entity)
        return entity
    }
}
```

- [ ] **Step 4: Write `FoodRepository.kt`**

```kotlin
package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.calc.CalorieMath
import com.suprxsidh.deficit.data.calc.DayBoundary
import com.suprxsidh.deficit.data.db.dao.CustomFoodDao
import com.suprxsidh.deficit.data.db.dao.FoodEntryDao
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

class FoodRepository(
    private val foodEntryDao: FoodEntryDao,
    private val customFoodDao: CustomFoodDao,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {
    private fun todayKey(): String = DayBoundary.logicalDate(clock()).toString()
    private fun nowMillis(): Long = clock().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private suspend fun log(name: String, rawKcal: Int, source: String, barcode: String? = null): FoodEntryEntity {
        val entity = FoodEntryEntity(
            date = todayKey(),
            name = name,
            rawKcal = rawKcal,
            bufferedKcal = CalorieMath.bufferedKcal(rawKcal),
            source = source,
            offBarcode = barcode,
            loggedAt = nowMillis()
        )
        val id = foodEntryDao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun logQuickAdd(name: String, rawKcal: Int): FoodEntryEntity = log(name, rawKcal, "QUICK")

    suspend fun logCustomFood(food: CustomFoodEntity, servings: Double): FoodEntryEntity =
        log(food.name, (food.kcalPerServing * servings).roundToInt(), "CUSTOM")

    suspend fun logOffProduct(name: String, rawKcal: Int, barcode: String): FoodEntryEntity =
        log(name, rawKcal, "OFF", barcode)

    fun observeTodayEntries(): Flow<List<FoodEntryEntity>> = foodEntryDao.observeForDate(todayKey())
    fun observeTodayBufferedTotal(): Flow<Int> = foodEntryDao.observeBufferedTotalForDate(todayKey())

    fun observeAllCustomFoods(): Flow<List<CustomFoodEntity>> = customFoodDao.observeAll()
    fun observePinnedCustomFoods(): Flow<List<CustomFoodEntity>> = customFoodDao.observePinned()
    suspend fun upsertCustomFood(food: CustomFoodEntity): Long = customFoodDao.upsert(food)
    suspend fun deleteCustomFood(food: CustomFoodEntity) = customFoodDao.delete(food)
    suspend fun deleteFoodEntry(entry: FoodEntryEntity) = foodEntryDao.delete(entry)
}
```

- [ ] **Step 5: Write `WeightRepository.kt`**

```kotlin
package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.calc.DayBoundary
import com.suprxsidh.deficit.data.calc.RollingAverage
import com.suprxsidh.deficit.data.calc.WeighInPoint
import com.suprxsidh.deficit.data.db.dao.WeighInDao
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

enum class TrendDirection { UP, DOWN, FLAT }

class WeightRepository(
    private val weighInDao: WeighInDao,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {
    suspend fun logWeighIn(weightKg: Double): WeighInEntity {
        val date = DayBoundary.logicalDate(clock()).toString()
        val existing = weighInDao.getForDate(date)
        val entity = existing?.copy(weightKg = weightKg) ?: WeighInEntity(date = date, weightKg = weightKg)
        val newId = weighInDao.upsert(entity)
        return if (existing != null) entity else entity.copy(id = newId)
    }

    fun observeRollingAverageSeries(): Flow<List<Pair<LocalDate, Double>>> =
        weighInDao.observeAll().map { entries ->
            RollingAverage.sevenDayRollingAverage(entries.map { WeighInPoint(LocalDate.parse(it.date), it.weightKg) })
        }

    fun observeRawSeries(): Flow<List<Pair<LocalDate, Double>>> =
        weighInDao.observeAll().map { entries -> entries.map { LocalDate.parse(it.date) to it.weightKg } }

    fun observeTotalChangeSinceStart(): Flow<Double?> =
        weighInDao.observeAll().map { entries ->
            if (entries.size < 2) null else entries.last().weightKg - entries.first().weightKg
        }

    fun observeFourWeekTrend(): Flow<TrendDirection?> =
        observeRollingAverageSeries().map { series ->
            if (series.size < 2) return@map null
            val latest = series.last()
            val fourWeeksAgoTarget = latest.first.minusDays(28)
            val reference = series.firstOrNull { !it.first.isBefore(fourWeeksAgoTarget) } ?: series.first()
            if (reference.first == latest.first) return@map null
            val delta = latest.second - reference.second
            when {
                delta <= -0.2 -> TrendDirection.DOWN
                delta >= 0.2 -> TrendDirection.UP
                else -> TrendDirection.FLAT
            }
        }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.repository.*"`
Expected: PASS (10/10).

- [ ] **Step 7: Write `AppContainer.kt` and wire it into `DeficitApp.kt`**

```kotlin
package com.suprxsidh.deficit.data

import android.content.Context
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.data.repository.UserProfileRepository
import com.suprxsidh.deficit.data.repository.WeightRepository

class AppContainer(context: Context) {
    private val database = DeficitDatabase.getInstance(context)
    val userProfileRepository = UserProfileRepository(database.userProfileDao())
    val foodRepository = FoodRepository(database.foodEntryDao(), database.customFoodDao())
    val weightRepository = WeightRepository(database.weighInDao())
}
```

Replace `DeficitApp.kt`'s contents entirely with:
```kotlin
package com.suprxsidh.deficit

import android.app.Application
import com.suprxsidh.deficit.data.AppContainer

class DeficitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

- [ ] **Step 8: Build check**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data app/src/test/java/com/suprxsidh/deficit/data/repository
git commit -m "Task 4: repository layer wiring calc utilities to Room, plus AppContainer"
```

### Task 5: Onboarding screen + start-destination routing

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/onboarding/OnboardingScreen.kt` (replace placeholder)
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/nav/DeficitNavHost.kt` (wire onboarding's completion callback)
- Modify: `app/src/main/java/com/suprxsidh/deficit/MainActivity.kt` (resolve start destination from whether a profile already exists)
- Test: `app/src/test/java/com/suprxsidh/deficit/ui/onboarding/OnboardingViewModelTest.kt`

**Interfaces:**
- Produces: `OnboardingViewModel(repository: UserProfileRepository)` — a `ViewModel` exposing Compose `mutableStateOf` fields `heightCm: String`, `weightKg: String`, `age: String`, `sex: Sex`, `goalWeightKg: String`, `error: String?`, and `suspend fun submit(onDone: () -> Unit)`. `OnboardingScreen(onComplete: () -> Unit)` composable.
- Consumes: `UserProfileRepository`, `Sex` (Tasks 2 & 4). `DeficitApp.container.userProfileRepository` (Task 4) as the concrete instance passed to the ViewModel.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.suprxsidh.deficit.ui.onboarding

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.repository.UserProfileRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingViewModelTest {
    private lateinit var db: DeficitDatabase
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        viewModel = OnboardingViewModel(UserProfileRepository(db.userProfileDao()))
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `submitting with blank fields sets an error and does not call onDone`() = runTest {
        var called = false
        viewModel.heightCm = ""
        viewModel.submit { called = true }

        assertEquals(false, called)
        assertNotNull(viewModel.error)
    }

    @Test
    fun `submitting valid data clears any error and calls onDone`() = runTest {
        var called = false
        viewModel.heightCm = "178"
        viewModel.weightKg = "80"
        viewModel.age = "26"
        viewModel.sex = Sex.MALE
        viewModel.submit { called = true }

        assertEquals(true, called)
        assertNull(viewModel.error)
    }

    @Test
    fun `blank goal weight leaves it to the repository default`() = runTest {
        viewModel.heightCm = "178"
        viewModel.weightKg = "80"
        viewModel.age = "26"
        viewModel.sex = Sex.MALE
        viewModel.goalWeightKg = ""
        viewModel.submit {}

        val profile = db.userProfileDao().get()
        assertEquals(70.0, profile!!.goalWeightKg, 0.001)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.onboarding.OnboardingViewModelTest"`
Expected: FAIL — `OnboardingViewModel` does not exist.

- [ ] **Step 3: Write `OnboardingViewModel.kt`**

```kotlin
package com.suprxsidh.deficit.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.repository.UserProfileRepository

class OnboardingViewModel(private val repository: UserProfileRepository) : ViewModel() {
    var heightCm by mutableStateOf("")
    var weightKg by mutableStateOf("")
    var age by mutableStateOf("")
    var sex by mutableStateOf(Sex.MALE)
    var goalWeightKg by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun submit(onDone: () -> Unit) {
        val h = heightCm.toDoubleOrNull()
        val w = weightKg.toDoubleOrNull()
        val a = age.toIntOrNull()
        if (h == null || h <= 0.0 || w == null || w <= 0.0 || a == null || a <= 0) {
            error = "Enter a valid height, weight, and age."
            return
        }
        error = null
        val goal = goalWeightKg.toDoubleOrNull()
        repository.completeOnboarding(heightCm = h, weightKg = w, age = a, sex = sex, goalWeightKg = goal)
        onDone()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.onboarding.OnboardingViewModelTest"`
Expected: PASS (3/3).

- [ ] **Step 5: Replace `OnboardingScreen.kt`**

```kotlin
package com.suprxsidh.deficit.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.deficit.DeficitApp
import com.suprxsidh.deficit.data.calc.Sex
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: OnboardingViewModel = viewModel(factory = viewModelFactory {
        initializer { OnboardingViewModel(app.container.userProfileRepository) }
    })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("A few numbers to start", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = viewModel.heightCm,
            onValueChange = { viewModel.heightCm = it },
            label = { Text("Height (cm)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = viewModel.weightKg,
            onValueChange = { viewModel.weightKg = it },
            label = { Text("Weight (kg)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = viewModel.age,
            onValueChange = { viewModel.age = it },
            label = { Text("Age") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = viewModel.sex == Sex.MALE, onClick = { viewModel.sex = Sex.MALE }, label = { Text("Male") })
            FilterChip(selected = viewModel.sex == Sex.FEMALE, onClick = { viewModel.sex = Sex.FEMALE }, label = { Text("Female") })
        }
        OutlinedTextField(
            value = viewModel.goalWeightKg,
            onValueChange = { viewModel.goalWeightKg = it },
            label = { Text("Goal weight (kg) — optional, defaults to current − 10") },
            modifier = Modifier.fillMaxWidth()
        )

        viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = { scope.launch { viewModel.submit(onComplete) } }, modifier = Modifier.fillMaxWidth()) {
            Text("Get started")
        }
    }
}
```

- [ ] **Step 6: Wire the completion callback in `DeficitNavHost.kt`**

Replace the `composable(Routes.ONBOARDING) { OnboardingScreen() }` line with:
```kotlin
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onComplete = {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
```

- [ ] **Step 7: Resolve the start destination in `MainActivity.kt`**

Replace `MainActivity.kt` entirely with:
```kotlin
package com.suprxsidh.deficit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.suprxsidh.deficit.ui.nav.DeficitNavHost
import com.suprxsidh.deficit.ui.nav.Routes
import com.suprxsidh.deficit.ui.theme.DeficitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DeficitApp
        setContent {
            DeficitTheme {
                var startDestination by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    val profile = app.container.userProfileRepository.getProfile()
                    startDestination = if (profile == null) Routes.ONBOARDING else Routes.DASHBOARD
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    startDestination?.let { start ->
                        val navController = rememberNavController()
                        DeficitNavHost(navController = navController, startDestination = start)
                    }
                }
            }
        }
    }
}
```

Note: this needs `androidx.compose.runtime.remember` imported alongside the others (`import androidx.compose.runtime.remember`) — add it to the import list above.

- [ ] **Step 8: Build check**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/ui/onboarding app/src/main/java/com/suprxsidh/deficit/ui/nav/DeficitNavHost.kt app/src/main/java/com/suprxsidh/deficit/MainActivity.kt app/src/test/java/com/suprxsidh/deficit/ui/onboarding
git commit -m "Task 5: onboarding screen, ViewModel, and profile-aware start destination"
```

### Task 6: Open Food Facts client, local cache, and repository

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/entity/OffCacheEntity.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/dao/OffCacheDao.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/db/DeficitDatabase.kt` (add `OffCacheEntity`, bump `version` to 2, add `.fallbackToDestructiveMigration()` — no shipped users yet, so a destructive migration during pre-release schema churn is the pragmatic choice, not a shortcut around real data loss)
- Create: `app/src/main/java/com/suprxsidh/deficit/food/off/OffModels.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/food/off/OpenFoodFactsApi.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/food/off/OpenFoodFactsServiceFactory.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/food/off/OpenFoodFactsRepository.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/AppContainer.kt` (add `openFoodFactsRepository`)
- Test: `app/src/test/java/com/suprxsidh/deficit/food/off/OpenFoodFactsRepositoryTest.kt`

**Interfaces:**
- Produces: `OffCacheEntity(code: String, productName: String, kcalPerServing: Int, servingLabel: String, cachedAt: Long)` (primary key `code`). `OffCacheDao` with `suspend fun get(code: String): OffCacheEntity?`, `suspend fun upsert(entry: OffCacheEntity)`, `fun observeRecent(): Flow<List<OffCacheEntity>>`. `OpenFoodFactsApi` (Retrofit interface) with `suspend fun search(searchTerms: String, countriesTagsEn: String = "india", fields: String = "code,product_name,nutriments", pageSize: Int = 20): OffSearchResponse`. `OpenFoodFactsServiceFactory.create(baseUrl: String = "https://world.openfoodfacts.org/"): OpenFoodFactsApi`. `OpenFoodFactsRepository(api: OpenFoodFactsApi, cacheDao: OffCacheDao, clock: () -> Long = { System.currentTimeMillis() })` with `suspend fun search(query: String): List<OffCacheEntity>` — on network failure, returns an empty list rather than throwing (Task 7's UI shows "no results, check connection" for empty + a flag; **do not** silently return cached results on failure and call it a search result, since staleness would be invisible to the user).
- Consumes: `DeficitDatabase` (Task 3) to add the new DAO; `AppContainer` (Task 4) to expose the repository.

- [ ] **Step 1: Write the failing repository test**

```kotlin
package com.suprxsidh.deficit.food.off

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenFoodFactsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var db: DeficitDatabase
    private lateinit var repo: OpenFoodFactsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        val api = OpenFoodFactsServiceFactory.create(baseUrl = server.url("/").toString())
        repo = OpenFoodFactsRepository(api, db.offCacheDao()) { 1_000L }
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    @Test
    fun `search parses products and caches them by code`() = runTest {
        val body = """
            {"products": [
                {"code": "8901058851031", "product_name": "Amul Chaas", "nutriments": {"energy-kcal_serving": 45.0}},
                {"code": "8901030812345", "product_name": "No kcal data", "nutriments": {}}
            ]}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val results = repo.search("chaas")

        assertEquals(1, results.size) // the product with no usable kcal field is dropped
        assertEquals("Amul Chaas", results[0].productName)
        assertEquals(45, results[0].kcalPerServing)

        val cached = db.offCacheDao().get("8901058851031")
        assertEquals(1_000L, cached!!.cachedAt)
    }

    @Test
    fun `search on network failure returns an empty list, not a crash`() = runTest {
        server.shutdown() // nothing listening -> connection refused
        val results = repo.search("anything")
        assertTrue(results.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.food.off.OpenFoodFactsRepositoryTest"`
Expected: FAIL to compile — none of the OFF classes exist yet.

- [ ] **Step 3: Write `OffCacheEntity.kt` and `OffCacheDao.kt`**

```kotlin
package com.suprxsidh.deficit.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "off_cache")
data class OffCacheEntity(
    @PrimaryKey val code: String,
    val productName: String,
    val kcalPerServing: Int,
    val servingLabel: String,
    val cachedAt: Long
)
```

```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suprxsidh.deficit.data.db.entity.OffCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OffCacheDao {
    @Query("SELECT * FROM off_cache WHERE code = :code LIMIT 1")
    suspend fun get(code: String): OffCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: OffCacheEntity)

    @Query("SELECT * FROM off_cache ORDER BY cachedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<OffCacheEntity>>
}
```

- [ ] **Step 4: Update `DeficitDatabase.kt`**

Add `OffCacheEntity::class` to the `entities` array, bump `version = 2`, add `abstract fun offCacheDao(): OffCacheDao`, and change the builder call to:
```kotlin
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DeficitDatabase::class.java,
                    "deficit.db"
                ).fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
```

- [ ] **Step 5: Write `OffModels.kt`, `OpenFoodFactsApi.kt`, `OpenFoodFactsServiceFactory.kt`**

```kotlin
package com.suprxsidh.deficit.food.off

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OffSearchResponse(val products: List<OffProduct> = emptyList())

@Serializable
data class OffProduct(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    val nutriments: OffNutriments? = null
)

@Serializable
data class OffNutriments(
    @SerialName("energy-kcal_serving") val energyKcalServing: Double? = null,
    @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null
)
```

```kotlin
package com.suprxsidh.deficit.food.off

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenFoodFactsApi {
    @GET("api/v2/search")
    suspend fun search(
        @Query("search_terms") searchTerms: String,
        @Query("countries_tags_en") countriesTagsEn: String = "india",
        @Query("fields") fields: String = "code,product_name,nutriments",
        @Query("page_size") pageSize: Int = 20
    ): OffSearchResponse
}
```

```kotlin
package com.suprxsidh.deficit.food.off

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object OpenFoodFactsServiceFactory {
    fun create(baseUrl: String = "https://world.openfoodfacts.org/"): OpenFoodFactsApi {
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenFoodFactsApi::class.java)
    }
}
```

- [ ] **Step 6: Write `OpenFoodFactsRepository.kt`**

```kotlin
package com.suprxsidh.deficit.food.off

import com.suprxsidh.deficit.data.db.dao.OffCacheDao
import com.suprxsidh.deficit.data.db.entity.OffCacheEntity
import java.io.IOException

class OpenFoodFactsRepository(
    private val api: OpenFoodFactsApi,
    private val cacheDao: OffCacheDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun search(query: String): List<OffCacheEntity> {
        val response = try {
            api.search(searchTerms = query)
        } catch (e: IOException) {
            return emptyList()
        }

        val now = clock()
        val results = response.products.mapNotNull { product ->
            val code = product.code ?: return@mapNotNull null
            val nutriments = product.nutriments
            val kcal = nutriments?.energyKcalServing?.toInt() ?: nutriments?.energyKcal100g?.toInt()
                ?: return@mapNotNull null
            val servingLabel = if (nutriments.energyKcalServing != null) "1 serving" else "100g"
            OffCacheEntity(
                code = code,
                productName = product.productName ?: "Unknown product",
                kcalPerServing = kcal,
                servingLabel = servingLabel,
                cachedAt = now
            )
        }
        results.forEach { cacheDao.upsert(it) }
        return results
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.food.off.OpenFoodFactsRepositoryTest"`
Expected: PASS (2/2).

- [ ] **Step 8: Wire into `AppContainer.kt`**

Add to `AppContainer`:
```kotlin
    val openFoodFactsRepository = com.suprxsidh.deficit.food.off.OpenFoodFactsRepository(
        com.suprxsidh.deficit.food.off.OpenFoodFactsServiceFactory.create(),
        database.offCacheDao()
    )
```
(Add proper top-of-file imports for `OpenFoodFactsRepository` and `OpenFoodFactsServiceFactory` instead of using fully-qualified names inline, if you prefer — either compiles.)

- [ ] **Step 9: Build check**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data app/src/main/java/com/suprxsidh/deficit/food app/src/test/java/com/suprxsidh/deficit/food
git commit -m "Task 6: Open Food Facts client, local cache, repository"
```

### Task 7: Food logging screen (quick-add, pinned snacks, custom foods, OFF search)

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/food/FoodLogViewModel.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/food/FoodLogScreen.kt` (replace placeholder)
- Test: `app/src/test/java/com/suprxsidh/deficit/ui/food/FoodLogViewModelTest.kt`

**Interfaces:**
- Produces: `FoodLogViewModel(foodRepository: FoodRepository, offRepository: OpenFoodFactsRepository)` — a `ViewModel` exposing `StateFlow<List<FoodEntryEntity>> todayEntries`, `StateFlow<Int> todayBufferedTotal`, `StateFlow<List<CustomFoodEntity>> pinnedFoods`, `StateFlow<List<CustomFoodEntity>> allCustomFoods`; mutable Compose state `quickAddName`, `quickAddKcal`, `offQuery`, `offResults: List<OffCacheEntity>`, `offSearchInFlight: Boolean`, `offSearchedOnce: Boolean`, `customFoodName`, `customFoodKcal`, `customFoodServingLabel`; functions `logQuickAdd()`, `logPinned(food: CustomFoodEntity)` (one tap — the button press itself calls this, satisfying the "≤2 taps" requirement from SPEC.md §3.3 with room to spare), `searchOff()`, `logOffResult(item: OffCacheEntity)`, `saveCustomFood(isPinned: Boolean)`, `logCustomFoodWithServings(food: CustomFoodEntity, servings: Double)`, `deleteCustomFood(food: CustomFoodEntity)`. `FoodLogScreen()` composable (reads `DeficitApp.container` itself, same pattern as Task 5's `OnboardingScreen`).
- Consumes: `FoodRepository`, `OpenFoodFactsRepository` (Tasks 4 & 6); `FoodEntryEntity`, `CustomFoodEntity`, `OffCacheEntity` (Tasks 3 & 6); `DeficitApp.container` (Task 4).

- [ ] **Step 1: Write the failing ViewModel tests**

```kotlin
package com.suprxsidh.deficit.ui.food

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.food.off.OpenFoodFactsRepository
import com.suprxsidh.deficit.food.off.OpenFoodFactsServiceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodLogViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var db: DeficitDatabase
    private lateinit var viewModel: FoodLogViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        server = MockWebServer()
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        val foodRepo = FoodRepository(db.foodEntryDao(), db.customFoodDao())
        val offRepo = OpenFoodFactsRepository(
            OpenFoodFactsServiceFactory.create(baseUrl = server.url("/").toString()),
            db.offCacheDao()
        )
        viewModel = FoodLogViewModel(foodRepo, offRepo)
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `logQuickAdd inserts a buffered entry and clears the input fields`() = runTest(testDispatcher) {
        viewModel.quickAddName = "Poha"
        viewModel.quickAddKcal = "300"
        viewModel.logQuickAdd()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.quickAddName)
        assertEquals("", viewModel.quickAddKcal)
        assertEquals(330, db.foodEntryDao().observeBufferedTotalForDate(
            com.suprxsidh.deficit.data.calc.DayBoundary.logicalDate(java.time.LocalDateTime.now()).toString()
        ).let { flow -> kotlinx.coroutines.flow.first(flow) })
    }

    @Test
    fun `logQuickAdd with blank name does nothing`() = runTest(testDispatcher) {
        viewModel.quickAddName = ""
        viewModel.quickAddKcal = "300"
        viewModel.logQuickAdd()
        testDispatcher.scheduler.advanceUntilIdle()

        val all = db.foodEntryDao().observeForDate(
            com.suprxsidh.deficit.data.calc.DayBoundary.logicalDate(java.time.LocalDateTime.now()).toString()
        ).let { flow -> kotlinx.coroutines.flow.first(flow) }
        assertTrue(all.isEmpty())
    }

    @Test
    fun `logPinned logs one serving of the given custom food in a single call`() = runTest(testDispatcher) {
        val chaas = CustomFoodEntity(name = "Chaas", kcalPerServing = 100, servingLabel = "1 glass", isPinned = true)
        viewModel.logPinned(chaas)
        testDispatcher.scheduler.advanceUntilIdle()

        val all = db.foodEntryDao().observeForDate(
            com.suprxsidh.deficit.data.calc.DayBoundary.logicalDate(java.time.LocalDateTime.now()).toString()
        ).let { flow -> kotlinx.coroutines.flow.first(flow) }
        assertEquals(1, all.size)
        assertEquals(110, all[0].bufferedKcal)
    }

    @Test
    fun `searchOff populates results and marks a search as having happened`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setBody("""{"products": [{"code": "123", "product_name": "Test", "nutriments": {"energy-kcal_serving": 50.0}}]}""").setResponseCode(200))
        viewModel.offQuery = "test"
        viewModel.searchOff()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.offResults.size)
        assertTrue(viewModel.offSearchedOnce)
        assertEquals(false, viewModel.offSearchInFlight)
    }

    @Test
    fun `saveCustomFood with blank name is a no-op`() = runTest(testDispatcher) {
        viewModel.customFoodName = ""
        viewModel.customFoodKcal = "100"
        viewModel.saveCustomFood(isPinned = false)
        testDispatcher.scheduler.advanceUntilIdle()

        val all = db.customFoodDao().observeAll().let { flow -> kotlinx.coroutines.flow.first(flow) }
        assertTrue(all.isEmpty())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.food.FoodLogViewModelTest"`
Expected: FAIL — `FoodLogViewModel` does not exist.

- [ ] **Step 3: Write `FoodLogViewModel.kt`**

```kotlin
package com.suprxsidh.deficit.ui.food

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.db.entity.CustomFoodEntity
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.db.entity.OffCacheEntity
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.food.off.OpenFoodFactsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoodLogViewModel(
    private val foodRepository: FoodRepository,
    private val offRepository: OpenFoodFactsRepository
) : ViewModel() {

    val todayEntries: StateFlow<List<FoodEntryEntity>> =
        foodRepository.observeTodayEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val todayBufferedTotal: StateFlow<Int> =
        foodRepository.observeTodayBufferedTotal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val pinnedFoods: StateFlow<List<CustomFoodEntity>> =
        foodRepository.observePinnedCustomFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allCustomFoods: StateFlow<List<CustomFoodEntity>> =
        foodRepository.observeAllCustomFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var quickAddName by mutableStateOf("")
    var quickAddKcal by mutableStateOf("")

    var offQuery by mutableStateOf("")
    var offResults by mutableStateOf<List<OffCacheEntity>>(emptyList())
    var offSearchInFlight by mutableStateOf(false)
    var offSearchedOnce by mutableStateOf(false)

    var customFoodName by mutableStateOf("")
    var customFoodKcal by mutableStateOf("")
    var customFoodServingLabel by mutableStateOf("")

    fun logQuickAdd() {
        val kcal = quickAddKcal.toIntOrNull() ?: return
        if (quickAddName.isBlank()) return
        val name = quickAddName
        viewModelScope.launch {
            foodRepository.logQuickAdd(name, kcal)
            quickAddName = ""
            quickAddKcal = ""
        }
    }

    fun logPinned(food: CustomFoodEntity) {
        viewModelScope.launch { foodRepository.logCustomFood(food, servings = 1.0) }
    }

    fun searchOff() {
        val query = offQuery
        if (query.isBlank()) return
        viewModelScope.launch {
            offSearchInFlight = true
            offResults = offRepository.search(query)
            offSearchInFlight = false
            offSearchedOnce = true
        }
    }

    fun logOffResult(item: OffCacheEntity) {
        viewModelScope.launch { foodRepository.logOffProduct(item.productName, item.kcalPerServing, item.code) }
    }

    fun saveCustomFood(isPinned: Boolean) {
        val kcal = customFoodKcal.toIntOrNull() ?: return
        if (customFoodName.isBlank()) return
        val name = customFoodName
        val label = customFoodServingLabel.ifBlank { "1 serving" }
        viewModelScope.launch {
            foodRepository.upsertCustomFood(
                CustomFoodEntity(name = name, kcalPerServing = kcal, servingLabel = label, isPinned = isPinned)
            )
            customFoodName = ""
            customFoodKcal = ""
            customFoodServingLabel = ""
        }
    }

    fun logCustomFoodWithServings(food: CustomFoodEntity, servings: Double) {
        viewModelScope.launch { foodRepository.logCustomFood(food, servings) }
    }

    fun deleteCustomFood(food: CustomFoodEntity) {
        viewModelScope.launch { foodRepository.deleteCustomFood(food) }
    }
}
```


- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.food.FoodLogViewModelTest"`
Expected: PASS (5/5).

- [ ] **Step 5: Replace `FoodLogScreen.kt`**

```kotlin
package com.suprxsidh.deficit.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.deficit.DeficitApp

@Composable
fun FoodLogScreen() {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: FoodLogViewModel = viewModel(factory = viewModelFactory {
        initializer { FoodLogViewModel(app.container.foodRepository, app.container.openFoodFactsRepository) }
    })

    val total by viewModel.todayBufferedTotal.collectAsState()
    val entries by viewModel.todayEntries.collectAsState()
    val pinned by viewModel.pinnedFoods.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Today: $total kcal counted", style = MaterialTheme.typography.titleLarge) }

        if (pinned.isNotEmpty()) {
            item { Text("Snacks", style = MaterialTheme.typography.bodyLarge) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pinned) { food ->
                        AssistChip(onClick = { viewModel.logPinned(food) }, label = { Text(food.name) })
                    }
                }
            }
        }

        item { Divider() }
        item { Text("Quick add", style = MaterialTheme.typography.bodyLarge) }
        item {
            OutlinedTextField(value = viewModel.quickAddName, onValueChange = { viewModel.quickAddName = it }, label = { Text("Food name") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = viewModel.quickAddKcal, onValueChange = { viewModel.quickAddKcal = it }, label = { Text("Calories") }, modifier = Modifier.fillMaxWidth())
        }
        item { Button(onClick = { viewModel.logQuickAdd() }) { Text("Add") } }

        item { Divider() }
        item { Text("Search packaged foods (Open Food Facts)", style = MaterialTheme.typography.bodyLarge) }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = viewModel.offQuery, onValueChange = { viewModel.offQuery = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
            }
        }
        item { Button(onClick = { viewModel.searchOff() }) { Text("Search") } }
        if (viewModel.offSearchInFlight) {
            item { CircularProgressIndicator() }
        } else if (viewModel.offSearchedOnce && viewModel.offResults.isEmpty()) {
            item { Text("No results — check spelling or your connection.") }
        }
        items(viewModel.offResults) { result ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${result.productName} (${result.kcalPerServing} kcal / ${result.servingLabel})")
                Button(onClick = { viewModel.logOffResult(result) }) { Text("Log") }
            }
        }

        item { Divider() }
        item { Text(if (entries.isEmpty()) "Nothing logged yet today." else "Logged today", style = MaterialTheme.typography.bodyLarge) }
        items(entries) { entry ->
            Text("${entry.name}: logged ${entry.rawKcal} → counted ${entry.bufferedKcal}")
        }
    }
}
```

- [ ] **Step 6: Build check**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/ui/food app/src/test/java/com/suprxsidh/deficit/ui/food
git commit -m "Task 7: food logging screen — quick add, pinned snacks, custom foods, OFF search"
```

### Task 8: Dashboard screen (budget bar, weight sparkline, quick-add entry point)

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/dashboard/DashboardScreen.kt` (replace placeholder)
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/nav/DeficitNavHost.kt` (pass a quick-add navigation callback into `DashboardScreen`)
- Test: `app/src/test/java/com/suprxsidh/deficit/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Produces: `DashboardViewModel(foodRepository: FoodRepository, userProfileRepository: UserProfileRepository, weightRepository: WeightRepository)` exposing `StateFlow<UserProfileEntity?> profile`, `StateFlow<Int> todayBufferedTotal`, `StateFlow<List<Pair<LocalDate, Double>>> rollingAverageSeries`. `DashboardScreen(onQuickAdd: () -> Unit)` composable; `WeightSparkline(points: List<Pair<LocalDate, Double>>)` composable (renders nothing but an empty-state message when `points.size < 2` — no crash, per SPEC.md §4.9).
- Consumes: `FoodRepository`, `UserProfileRepository`, `WeightRepository` (Task 4); `UserProfileEntity` (Task 3).

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.suprxsidh.deficit.ui.dashboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.data.repository.UserProfileRepository
import com.suprxsidh.deficit.data.repository.WeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashboardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: DeficitDatabase
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        viewModel = DashboardViewModel(
            FoodRepository(db.foodEntryDao(), db.customFoodDao()) { java.time.LocalDateTime.of(2026, 8, 10, 12, 0) },
            UserProfileRepository(db.userProfileDao()),
            WeightRepository(db.weighInDao())
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `profile is null before onboarding`() = runTest(testDispatcher) {
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.profile.value)
    }

    @Test
    fun `today buffered total reflects logged entries`() = runTest(testDispatcher) {
        db.foodEntryDao().insert(FoodEntryEntity(date = "2026-08-10", name = "Test", rawKcal = 200, bufferedKcal = 220, source = "QUICK", offBarcode = null, loggedAt = 1L))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(220, viewModel.todayBufferedTotal.value)
    }

    @Test
    fun `after onboarding the profile reflects the computed soft budget`() = runTest(testDispatcher) {
        UserProfileRepository(db.userProfileDao()).completeOnboarding(178.0, 80.0, 26, Sex.MALE)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1645, viewModel.profile.value?.softBudgetKcal)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `DashboardViewModel` does not exist.

- [ ] **Step 3: Write `DashboardViewModel.kt`**

```kotlin
package com.suprxsidh.deficit.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.data.repository.UserProfileRepository
import com.suprxsidh.deficit.data.repository.WeightRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class DashboardViewModel(
    foodRepository: FoodRepository,
    userProfileRepository: UserProfileRepository,
    weightRepository: WeightRepository
) : ViewModel() {

    val profile: StateFlow<UserProfileEntity?> =
        userProfileRepository.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todayBufferedTotal: StateFlow<Int> =
        foodRepository.observeTodayBufferedTotal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val rollingAverageSeries: StateFlow<List<Pair<LocalDate, Double>>> =
        weightRepository.observeRollingAverageSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.dashboard.DashboardViewModelTest"`
Expected: PASS (3/3).

- [ ] **Step 5: Replace `DashboardScreen.kt`**

```kotlin
package com.suprxsidh.deficit.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.deficit.DeficitApp
import java.time.LocalDate

@Composable
fun DashboardScreen(onQuickAdd: () -> Unit) {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: DashboardViewModel = viewModel(factory = viewModelFactory {
        initializer {
            DashboardViewModel(app.container.foodRepository, app.container.userProfileRepository, app.container.weightRepository)
        }
    })

    val profile by viewModel.profile.collectAsState()
    val total by viewModel.todayBufferedTotal.collectAsState()
    val series by viewModel.rollingAverageSeries.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Today", style = MaterialTheme.typography.titleLarge)

        val budget = profile?.softBudgetKcal ?: 0
        Text(if (budget > 0) "$total / $budget kcal counted" else "$total kcal counted")
        if (budget > 0) {
            LinearProgressIndicator(
                progress = { (total.toFloat() / budget.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }

        Text("Weight (7-day average)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
        WeightSparkline(points = series.takeLast(30))

        Button(onClick = onQuickAdd, modifier = Modifier.padding(top = 16.dp)) {
            Text("Log food")
        }
    }
}

@Composable
fun WeightSparkline(points: List<Pair<LocalDate, Double>>) {
    if (points.size < 2) {
        Text("Log a couple of weigh-ins to see your trend here.")
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        val values = points.map { it.second }
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (points.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { index, (_, value) ->
            val x = index * stepX
            val y = size.height - ((value - minV) / range * size.height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
    }
}
```

- [ ] **Step 6: Wire the quick-add callback in `DeficitNavHost.kt`**

Replace the `composable(Routes.DASHBOARD) { DashboardScreen() }` line with:
```kotlin
        composable(Routes.DASHBOARD) {
            DashboardScreen(onQuickAdd = { navController.navigate(Routes.FOOD_LOG) })
        }
```

- [ ] **Step 7: Build check**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/ui/dashboard app/src/main/java/com/suprxsidh/deficit/ui/nav/DeficitNavHost.kt app/src/test/java/com/suprxsidh/deficit/ui/dashboard
git commit -m "Task 8: dashboard — soft-budget bar, weight sparkline, quick-add entry point"
```

### Task 9: Weight tracking screen (manual entry, raw+rolling chart, total change, trend)

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/weight/WeightViewModel.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/weight/WeightScreen.kt` (replace placeholder)
- Test: `app/src/test/java/com/suprxsidh/deficit/ui/weight/WeightViewModelTest.kt`

**Interfaces:**
- Produces: `WeightViewModel(weightRepository: WeightRepository)` exposing `StateFlow<List<Pair<LocalDate, Double>>> rawSeries`, `StateFlow<List<Pair<LocalDate, Double>>> rollingSeries`, `StateFlow<Double?> totalChange`, `StateFlow<TrendDirection?> trend`, mutable `weightInput: String`, and `fun logWeighIn()`. `WeightScreen()` composable.
- Consumes: `WeightRepository`, `TrendDirection` (Task 4); `DeficitApp.container` (Task 4).

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.suprxsidh.deficit.ui.weight

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import com.suprxsidh.deficit.data.repository.WeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeightViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: DeficitDatabase
    private lateinit var viewModel: WeightViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        viewModel = WeightViewModel(WeightRepository(db.weighInDao()))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `logWeighIn stores the entry and clears the input`() = runTest(testDispatcher) {
        viewModel.weightInput = "81.4"
        viewModel.logWeighIn()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.weightInput)
        assertEquals(1, viewModel.rawSeries.value.size)
        assertEquals(81.4, viewModel.rawSeries.value[0].second, 0.001)
    }

    @Test
    fun `logWeighIn with non-numeric input is a no-op`() = runTest(testDispatcher) {
        viewModel.weightInput = "not a number"
        viewModel.logWeighIn()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.rawSeries.value.size)
    }

    @Test
    fun `total change reflects the repository's computed delta`() = runTest(testDispatcher) {
        db.weighInDao().upsert(WeighInEntity(date = "2026-07-01", weightKg = 85.0))
        db.weighInDao().upsert(WeighInEntity(date = "2026-08-10", weightKg = 81.0))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(-4.0, viewModel.totalChange.value!!, 0.001)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.weight.WeightViewModelTest"`
Expected: FAIL — `WeightViewModel` does not exist.

- [ ] **Step 3: Write `WeightViewModel.kt`**

```kotlin
package com.suprxsidh.deficit.ui.weight

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.repository.TrendDirection
import com.suprxsidh.deficit.data.repository.WeightRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class WeightViewModel(private val weightRepository: WeightRepository) : ViewModel() {

    val rawSeries: StateFlow<List<Pair<LocalDate, Double>>> =
        weightRepository.observeRawSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rollingSeries: StateFlow<List<Pair<LocalDate, Double>>> =
        weightRepository.observeRollingAverageSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val totalChange: StateFlow<Double?> =
        weightRepository.observeTotalChangeSinceStart().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val trend: StateFlow<TrendDirection?> =
        weightRepository.observeFourWeekTrend().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var weightInput by mutableStateOf("")

    fun logWeighIn() {
        val weight = weightInput.toDoubleOrNull() ?: return
        viewModelScope.launch {
            weightRepository.logWeighIn(weight)
            weightInput = ""
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.weight.WeightViewModelTest"`
Expected: PASS (3/3).

- [ ] **Step 5: Replace `WeightScreen.kt`**

```kotlin
package com.suprxsidh.deficit.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.deficit.DeficitApp
import com.suprxsidh.deficit.data.repository.TrendDirection
import java.time.LocalDate

@Composable
fun WeightScreen() {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: WeightViewModel = viewModel(factory = viewModelFactory {
        initializer { WeightViewModel(app.container.weightRepository) }
    })

    val raw by viewModel.rawSeries.collectAsState()
    val rolling by viewModel.rollingSeries.collectAsState()
    val totalChange by viewModel.totalChange.collectAsState()
    val trend by viewModel.trend.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Weight", style = MaterialTheme.typography.titleLarge)

        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedTextField(
                value = viewModel.weightInput,
                onValueChange = { viewModel.weightInput = it },
                label = { Text("Today's weight (kg)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Button(onClick = { viewModel.logWeighIn() }) { Text("Log weigh-in") }

        if (raw.size < 2) {
            Text("Log a couple of weigh-ins to see your chart.", modifier = Modifier.padding(top = 16.dp))
        } else {
            WeightChart(raw = raw, rolling = rolling, modifier = Modifier.fillMaxWidth().height(160.dp).padding(top = 16.dp))
        }

        totalChange?.let {
            val direction = if (it <= 0) "down" else "up"
            Text("Total change: ${"%.1f".format(kotlin.math.abs(it))} kg $direction since you started", modifier = Modifier.padding(top = 8.dp))
        }
        trend?.let {
            val label = when (it) {
                TrendDirection.DOWN -> "Trending down over the last 4 weeks"
                TrendDirection.UP -> "Trending up over the last 4 weeks"
                TrendDirection.FLAT -> "Holding steady over the last 4 weeks"
            }
            Text(label)
        }
    }
}

@Composable
private fun WeightChart(
    raw: List<Pair<LocalDate, Double>>,
    rolling: List<Pair<LocalDate, Double>>,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val faint = accent.copy(alpha = 0.35f)
    Canvas(modifier = modifier) {
        val allValues = raw.map { it.second }
        val minV = allValues.min()
        val maxV = allValues.max()
        val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (raw.size - 1).coerceAtLeast(1)

        raw.forEachIndexed { index, (_, value) ->
            val x = index * stepX
            val y = size.height - ((value - minV) / range * size.height).toFloat()
            drawCircle(color = faint, radius = 4f, center = androidx.compose.ui.geometry.Offset(x, y))
        }

        if (rolling.size >= 2) {
            val path = androidx.compose.ui.graphics.Path()
            val rollingStepX = size.width / (rolling.size - 1)
            rolling.forEachIndexed { index, (_, value) ->
                val x = index * rollingStepX
                val y = size.height - ((value - minV) / range * size.height).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = accent, style = Stroke(width = 5f))
        }
    }
}
```

- [ ] **Step 6: Build check**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/ui/weight app/src/test/java/com/suprxsidh/deficit/ui/weight
git commit -m "Task 9: weight screen — manual entry, raw+rolling chart, total change, 4-week trend"
```

### Task 10: Bottom navigation, full-suite verification, and Phase 1 TESTING.md

**Files:**
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/nav/DeficitNavHost.kt` (accept a `modifier: Modifier = Modifier` param, pass through to `NavHost`)
- Modify: `app/src/main/java/com/suprxsidh/deficit/MainActivity.kt` (wrap in a `Scaffold` with a bottom `NavigationBar` shown once onboarding is done)
- Create: `TESTING.md` (project root)

**Interfaces:**
- Produces: nothing new consumed by later tasks — this is the last task of Phase 1.
- Consumes: `Routes`, `DeficitNavHost` (Task 1/5/8), all four screens (Tasks 5, 7, 8, 9).

- [ ] **Step 1: Update `DeficitNavHost.kt`'s signature**

Change the function signature to:
```kotlin
@Composable
fun DeficitNavHost(navController: NavHostController, startDestination: String, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
```
(Add `import androidx.compose.ui.Modifier` to the file's imports.) Leave the rest of the `NavHost` body — the three `composable(...)` blocks from Tasks 5 and 8, plus the plain `composable(Routes.WEIGHT) { WeightScreen() }` from Task 1 — unchanged.

- [ ] **Step 2: Replace `MainActivity.kt` with the bottom-nav-aware version**

```kotlin
package com.suprxsidh.deficit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.suprxsidh.deficit.ui.nav.DeficitNavHost
import com.suprxsidh.deficit.ui.nav.Routes
import com.suprxsidh.deficit.ui.theme.DeficitTheme

private data class BottomDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val BOTTOM_DESTINATIONS = listOf(
    BottomDestination(Routes.DASHBOARD, "Today", Icons.Default.Home),
    BottomDestination(Routes.FOOD_LOG, "Food", Icons.Default.List),
    BottomDestination(Routes.WEIGHT, "Weight", Icons.Default.Info)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DeficitApp
        setContent {
            DeficitTheme {
                var startDestination by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    val profile = app.container.userProfileRepository.getProfile()
                    startDestination = if (profile == null) Routes.ONBOARDING else Routes.DASHBOARD
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    startDestination?.let { start ->
                        val navController = rememberNavController()
                        val backStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = backStackEntry?.destination?.route
                        val showBottomBar = currentRoute != null && currentRoute != Routes.ONBOARDING

                        Scaffold(
                            bottomBar = {
                                if (showBottomBar) {
                                    NavigationBar {
                                        BOTTOM_DESTINATIONS.forEach { destination ->
                                            val selected = backStackEntry?.destination?.hierarchy
                                                ?.any { it.route == destination.route } == true
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = {
                                                    navController.navigate(destination.route) {
                                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                },
                                                icon = { Icon(destination.icon, contentDescription = destination.label) },
                                                label = { Text(destination.label) }
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            DeficitNavHost(
                                navController = navController,
                                startDestination = start,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build check**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, all tests across every task green (48 tests as specified across this plan's ten tasks; exact count may drift slightly if an earlier fix round added a test — note the real number in your report, don't force it to match 48).

- [ ] **Step 5: Write `TESTING.md`**

```markdown
# Deficit — Phase 1 (Core Loop) Testing Notes

Phase 1 has no Health Connect, Gemini, notifications, widget, or routines — see `SPEC.md` and `BUILD_PLAN.md` for what's deferred to later phases against the same spec.

## Automated coverage
`./gradlew testDebugUnitTest` — calorie/BMR/TDEE math, 3am day-boundary logic, 7-day rolling average, all four DAOs, all three repositories, the Open Food Facts client (success + network-failure paths), and every screen's ViewModel (validation, buffer math wiring, day-boundary-aware "today" queries).

## Manual on-device checks (sideload `app/build/outputs/apk/debug/app-debug.apk`)
1. **Onboarding**: fresh install → onboarding screen appears; submitting blank fields shows a validation error; submitting valid data lands on the Dashboard with a bottom nav bar (Today / Food / Weight).
2. **Quick add**: log a food via Quick Add on the Food screen; confirm the dashboard's "counted" total updates and matches raw×1.10 (rounded up).
3. **Pinned snack**: pin a custom food (isPinned = true via the custom-food form), confirm it appears as a one-tap chip and logs correctly.
4. **Open Food Facts search**: search a real packaged food (needs network); confirm results appear and logging one adds it to today's list. Turn off network and search again — should show "No results — check spelling or your connection." not a crash.
5. **Weigh-in**: log a weigh-in; log a second one the same day and confirm it overwrites (still one entry for today, not two). Log a few more across different days and confirm the raw+rolling chart renders (raw points faint, rolling average bold) and total-change/trend text appears.
6. **Day boundary**: if practical, change the device clock to just after midnight and log a meal — confirm it's grouped with the previous day's log, not a new "today."
7. **Zero-data day 1**: on a completely fresh profile with nothing logged, confirm every screen shows a sensible message (never a blank panel or infinite spinner).

## Known Phase 1 gaps (deferred to later phases, not bugs)
- No Health Connect: no run detection, no two-way weigh-in sync, no exercise credit shown anywhere in the UI (the math function exists and is tested, just has no live caller yet).
- No Gemini AI food estimation — only Quick Add / OFF search / Custom Foods.
- No notifications of any kind (motivation, meals, weigh-in, posture, backup reminder).
- No home screen widget.
- No guided routines (mobility/warm-up/cool-down).
- No backup/export/restore.
- No weekly commitment/motivation system, consistency grid, or weekly review.
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/ui/nav/DeficitNavHost.kt app/src/main/java/com/suprxsidh/deficit/MainActivity.kt TESTING.md
git commit -m "Task 10: bottom navigation, full-suite green, Phase 1 TESTING.md"
```
