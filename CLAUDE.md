# 记账 (Jizhang)

Personal Android bookkeeping app, for the owner's own use only. It has no server and no ads, and it is not published.

## Stack
- Kotlin + Jetpack Compose + Room + DataStore. Package `com.jizhang`, minSdk 26, targetSdk/compileSdk 37.
- AGP 9 has Kotlin built in, so do **not** apply `kotlin-android`. Only the `kotlin.compose` and `ksp` plugins are applied.
- Glass effects come from Haze 2 (`haze`, `haze-blur`, `haze-glass`). Charts come from Vico 3 (line) and ComposeCharts (`PieChart`).
- Build from bash:
  ```
  JAVA_HOME=/f/AndroidStudio/jbr ./gradlew :app:assembleDebug
  ```
  - SDK: `F:\AndroidSDK`.
  - AVD: `Medium_Phone_API_37.0`.

## Layout
- `domain/`: Pure Kotlin, unit-tested.
  - Categories.
  - `AmountInput` (keypad expression; amounts in cents).
  - `MonthSummary` (totals, daily series, top-5 shares).
- `data/`:
  - Room `RecordEntity`/`RecordDao`.
  - `SettingsRepository` (theme, budget).
  - `Backup` (JSON, photos embedded as Base64).
  - `ImageStore`: record photos in `files/images`, imported scaled to 1600px JPEG; orphans pruned after a day.
  - `DemoData` (debug builds only; rows flagged `demo`).
  - `SharkImport`: the 鲨鱼记账 CSV (GB18030/UTF-8) → `SharkPlan` (categories, times, duplicates); pure Kotlin, unit-tested. Preview form is `ui/forms/ImportForm.kt`; `LedgerViewModel.importShark` writes it in one transaction.
  - `Updater` + `UpdateController` (`vm.updater`): the only network code. It checks the latest GitHub Release of `jizhang.updateRepo`, downloads the APK, verifies package name, higher versionCode and the same signing cert, then installs through `PackageInstaller`. Sheet and 我的 rows are in `ui/update/`; the release steps are in README "Updates".
- `LedgerViewModel`: single ViewModel. `AppContainer` in `JizhangApp` is the only DI.
- `ui/theme/`:
  - `LedgerColors` holds the pearl/glacier/dusk tokens, copied from the approved prototype `D-Theme.dc.html`; matcha/sakura/abyss/ink follow the same structure. `ThemeId` keys are stored, so never rename one.
  - A theme switch swaps the whole colour set in one step; never lerp colours frame by frame (it recomposes the whole app). `ThemeReveal` freezes the screen into a bitmap and uncovers the new theme with a circle growing from the tapped swatch.
  - `Motion`, `Haptics`, `LedgerIcons` (prototype SVG paths).
- `ui/components/`:
  - `glass()` modifier.
  - Ambient background, which is the only `hazeSource`.
  - Odometer, common pieces.
- Screens: `ui/home` (with search), `ui/recurring` (定时记账; engine in `domain/Recurring`, run by `vm.runRecurring()` from `MainActivity.onResume`), `ui/add`, `ui/detail`, `ui/stats` (`domain/Trend`: `Period` is a year, month or ISO-8601 Monday-start week, `Trend` its buckets, average and ranking; the period dropdown is an in-layout panel, not a popup), `ui/budget` (the 预算 tab), `ui/me`. The root is `ui/LedgerApp.kt`.
- `ui/theme/IconLibrary` holds the icons for custom categories. Their keys are stored in the database, so never rename one.

## Rules (from the owner — keep them)
1. **Edge-to-edge.** `enableEdgeToEdge()`; backgrounds extend under the status bar and gesture bar. Content adds insets itself, and the nav bar sits above the gesture inset.
2. **Animation specs.** Interaction animations use `Motion.layout()` = `spring(DampingRatioLowBouncy, StiffnessMediumLow)`. No `tween()` for interactions. Two exceptions:
   - Colour/alpha uses the non-bouncy `Motion.fade()`, because a bouncy spring overshoots colours.
   - Endless ambient loops use `infiniteRepeatable(tween)`, because springs cannot repeat.
3. **Odometer amounts.** Use `AnimatedContent` + `slideInVertically` + `fadeIn`, one character per slot, so only changed digits roll.
   - Totals are keyed from the right.
   - Typed input is keyed from the left.
   - Numbers use tabular figures (`numberStyle`).
4. **List row → detail.** Uses `SharedTransitionLayout` + `sharedBounds`. The icon (`sharedElement`) and the amount (`sharedBounds` + scaleToBounds) fly across. Keys are in `SharedKeys`.
5. **Charts.** Use Vico or ComposeCharts (growth animation, gradient fill, drag crosshair). No hand-drawn Canvas charts. The budget ring is a progress indicator, not a chart.
6. **Haptics.** Call `Haptics.tick()` (`CLOCK_TICK`) on:
   - every keypad tap,
   - a category switch,
   - the swipe-to-delete threshold.

   Rejected input uses `reject()` (`REJECT` on API 30+).

## Forms and overlays
- Never use `AlertDialog`/`DatePickerDialog`. They open in a separate window, so they can't blur the app, and they don't match the design.
  - Open a glass bottom sheet with `LocalFormSheet.current.open { s -> … }`.
  - Build it from `FormSheet.kt` pieces: `FormTitle`, `FormField`, `Segmented`, `ChoiceChips`, `QuickPicks`, `FormActions`.
  - Ready-made forms live in `ui/forms/Forms.kt`. Dates use `DateWheel`.
- Full-screen overlays (add sheet, form host) are stacked in `LedgerApp`. A later `BackHandler` wins, so any handler under an open form checks `!LocalFormSheet.current.isOpen`. Handlers inside a form that need back first (e.g. note chips in edit mode) take `backClaims`.

## Data
- Room DB v5: `MIGRATION_1_2` adds `budgets`; `MIGRATION_2_3` adds `custom_categories` and `note_chips`; `MIGRATION_3_4` adds record `images`/`ruleId`, custom category `color`, `category_prefs` and `recurring`; `MIGRATION_4_5` adds `recurring.yearDays` and turns monthly `days` into a day-of-month bit set (bit d−1 = day d).
- Recurring rules (`RecurringRule`): weekly `days` = Monday-first bits, monthly `days` = day-of-month bits, yearly dates live in `yearDays` (month×100+day, `days` = 0). Picks past a short month's end fall on its last day; picks landing on the same date write one record.
  - `category_prefs` holds built-in edits (name/icon/hue/color) and every category's dragged grid order. `Category.color` (ARGB) overrides the hue: use `c.hue(category)` / `c.tint(category)`, never `c.hue(category.hue)`.
  - Budget amount chips reuse `note_chips` with scope keys from `LedgerViewModel.budgetChipScope(period)` (`$budget:<period>`).
  - Custom category keys are `"c" + id`. Always resolve keys through `state.catalog` (`CategoryCatalog`), never `Category.of`, which knows only the built-ins.
  - Hiding a note chip stores it with `pinned = false`.
- Every schema change needs a new migration. Never use destructive fallback, because this is the owner's real ledger.
- Backup JSON is versioned (`Backup.VERSION`). `decode` must keep accepting older files.

## Gotchas
- Don't use `rememberSaveable` for per-row UI state in lazy lists (e.g. M3 `SwipeToDismissBox`). A row restored by undo comes back with its saved "dismissed" state and stays invisible or deletes again. `SwipeRow` uses plain `remember`.
- Maven Central TLS handshakes fail intermittently from this machine. Just retry the build.
- Haze glass chooses its light/dark material from `LocalConfiguration.uiMode`. `LedgerTheme` overrides the night bit to follow the app theme, not the system theme.
- `Modifier.dropShadow` draws under translucent content. Only use it where an opaque blur or fill covers it.
- Touch targets must be at least 48dp.
- A pointer handler on a node that moves with the finger (a dragged tile) reads ~zero `positionChange()`. Work out the finger in a stationary space instead (see `GridDrag.laid` in `AddSheet`).

## Releases
- Public repo `wrdsyk/xiaozhangben`; the in-app updater reads its latest GitHub Release (`jizhang.updateRepo` in `gradle.properties`).
- Publish with `scripts/release.sh release-notes/<version>.md` after raising `versionCode`/`versionName` and committing. The GitHub CLI is at `G:	ools\gh\gh.exe` (`GH=/g/tools/gh/gh.exe`); run it outside the sandbox, where the login lives.
- Releases are signed with this machine's `~/.android/debug.keystore`. Losing it means the phone can't take updates without uninstalling (and losing data), so keep a copy of it.
