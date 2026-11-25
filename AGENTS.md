# AGENTS.md

Grabbit is an Android media downloader built on yt-dlp.
Stack: Kotlin, Jetpack Compose (Material 3), Hilt, Room, Coroutines/Flow.

These rules apply to every change in this repository. Follow them when writing new code, and bring any file you touch into line with them (boy-scout rule). Do not do unrelated refactors in the same change.

## 1. Project layout

```
com.grabbit
├── core/      pure helpers, no Android UI: Config, AppLog, Formatters, Links, Errors, Languages
├── data/      repositories and engines: settings, db, ytdlp, download, convert, media
├── service/   foreground download service
├── di/        Hilt modules
└── ui/        theme/, components/, and one package per screen (XxxScreen + XxxViewModel)
```

- Dependencies point one way: `ui → data/core`, `data → core`. `core` never imports `ui` or `data`.
- One screen = `XxxScreen.kt` + `XxxViewModel.kt`. Shared widgets go to `ui/components/`.
- A screen file over ~300 lines must be split into section composables (~150 lines each) in the same package.
- File order: constants → public API → private details → previews.
- `Routes` live in `ui/Routes.kt`.

## 2. No magic numbers

Every number except `0`, `1` and obvious math gets a name. Units go in the name (`_MS`, `_SECONDS`, `_BYTES`, `_KBPS`) or use `Duration` (`60.seconds`).

Where a constant lives:
- used in one file: `private const val` at the top of that file;
- used in 2+ files: `core/Config.kt`, grouped by meaning (`Timing`, `Defaults`, `Limits`, ...);
- UI sizes: only `ui/theme/Dimens.kt` (see section 3).

Never duplicate a value. If you need the same number twice, it is a shared constant.

```kotlin
object Timing {
    val FETCH_TIMEOUT = 60.seconds
    val PROGRESS_THROTTLE = 250.milliseconds
    val SERVICE_STOP_GRACE = 1500.milliseconds
    val UPDATE_CHECK_INTERVAL = 1.days
    val FLOW_SUBSCRIPTION_GRACE = 5.seconds
    val SEARCH_DEBOUNCE = 150.milliseconds
}
object Defaults {
    const val AUDIO_BITRATE_KBPS = 192
    const val MAX_AUDIO_BITRATE_KBPS = 320
    const val VIDEO_HEIGHT = 1080
}
object Limits {
    const val MAX_PLAYLIST_ITEMS = 200
    const val MIX_PLAYLIST_END = 50
    const val ERROR_TECHNICAL_TAIL_CHARS = 1200
}
```

Typical offenders to always name: timeouts, retries, delays, debounce, buffer sizes, list limits, bitrates, thresholds, notification IDs, `100` in progress math, `1024` in size math.

## 3. UI: one size grid

Never write `N.dp` in a screen. Use `Dimens`:

```kotlin
object Dimens {
    val xxs = 4.dp; val xs = 8.dp; val sm = 12.dp
    val md = 16.dp; val lg = 24.dp; val xl = 32.dp
    val iconSm = 18.dp; val iconMd = 28.dp; val iconLg = 56.dp
}
```

- Spacing is on a 4dp grid. Do not invent 6, 10, 18 and similar values; snap to the nearest grid step.
- A size used by only one component may be a private constant of that component.
- Line thickness (1dp, 2dp) is allowed as a named constant.
- Corner radii come from `MaterialTheme.shapes`, not from `RoundedCornerShape(12.dp)`.
- Colors come only from `MaterialTheme.colorScheme`. No `Color(0x...)` outside `ui/theme/`.
- Text styles come only from `MaterialTheme.typography`. No `fontSize = N.sp` outside `ui/theme/Type.kt`.
- Reuse components from `ui/components/` before writing a new one. If two screens build the same thing, extract it.

## 4. Logging and debug code

`if (BuildConfig.DEBUG)` is forbidden outside `AppLog`. Debug output goes through the facade, the message is a lambda so it is not computed in release:

```kotlin
object AppLog {
    inline fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag, message())
    }
    fun e(tag: String, message: String, t: Throwable? = null) { Log.e(tag, message, t) }
}

AppLog.d(TAG) { "fetch command: ${request.buildCommand()}" }
```

- Tags are `private const val TAG = "ClassName"`. No string-literal tags inline.
- No direct `Log.*`, `println` or `printStackTrace` outside `AppLog`.
- Never log secrets (cookies, proxy credentials, tokens).

## 5. Error handling

- `catch (Throwable)` is allowed only at the boundary with yt-dlp/ffmpeg, and always after `catch (CancellationException) { throw e }`.
- Do not use `runCatching` in suspend code, it swallows `CancellationException`. Use try/catch or `suspendRunCatching` (rethrows cancellation).
- `!!` is forbidden. Model the state so the value is non-null, or handle null explicitly.
- User-facing errors are `AppError` with a `@StringRes` message. Raw yt-dlp/ffmpeg text goes only into `technical`.
- No empty `catch` blocks. At least log through `AppLog.e`.
- `@Suppress` and `@SuppressLint` need a comment explaining why.

## 6. Reusable patterns (do not copy-paste)

- ViewModel state: `flow.stateInUi(viewModelScope, initial)` (uses `Timing.FLOW_SUBSCRIPTION_GRACE`). No hand-written `stateIn(..., WhileSubscribed(5_000), ...)`.
- Percent: only `Formatters.percent`. No manual `(x * 100).toInt()`.
- No alias functions that only forward to another function.
- If you write the same 3+ lines in a third place, extract a helper.

## 7. Database

- No `fallbackToDestructiveMigration`. History must survive updates.
- `exportSchema = true`, schemas are committed. Any schema change bumps the version and ships an explicit `Migration`.

## 8. Localization

Everything the user sees comes from string resources. No literals in `Text(...)`, `contentDescription`, notifications or toasts, and no string interpolation that builds a sentence. Use format strings:

```xml
<string name="percent_format">%1$d%%</string>
<string name="name_and_size">%1$s · %2$s</string>
```

### 8.1 Folder structure

All translations live in one root folder, one subfolder per language:

```
app/src/main/i18n/
  en/strings.xml        (default language)
  ru/strings.xml
```

- The language folder is the ISO code (`en`, `ru`, region as `pt-BR`), never `eng`. No `values/` or `values-xx/` folders in the source tree.
- Android needs `values`/`values-<code>` in the final resources, so the Gradle task `generate<Variant>I18nRes` copies every `i18n/<code>/*.xml` into `build/generated/i18n/<variant>/res/values[-<code>]/` at build time (default language -> `values`, others -> `values-<code>`, `pt-BR` -> `values-pt-rBR`). Never edit generated files and never commit them.
- A language may have several files (`strings.xml`, `plurals.xml`), all `.xml` files in the folder are copied.
- `res/values*/strings.xml` must not exist. Other resources (drawables, themes, colors) stay in `res/`.

### 8.2 Adding a language

1. Create `i18n/<code>/strings.xml` with every key from `en`.
2. Nothing else. The supported list is derived from the folders (see 8.3).

### 8.3 Single source of truth for languages

- The language list is derived from the `i18n/` folders. No language code is written by hand in Kotlin or XML.
- `locales_config` is generated by Gradle (`generateLocaleConfig = true` on AGP 8.1+, otherwise a Gradle task). `Languages.SUPPORTED` reads `BuildConfig.SUPPORTED_LANGUAGES`.
- `Languages.DEFAULT_LANGUAGE = "en"` is the only named default and matches `unqualifiedResLocale`.

### 8.4 String rules

- Only the app's own strings go in `strings.xml`. Do not paste library resources (`abc_*`, `m3c_*`, `call_notification_*`, ...). Libraries ship their own translations.
- Key naming: `<area>_<meaning>`, with areas such as `settings_`, `error_`, `action_`, `notif_`, `status_`, `nav_`, `convert_`, `history_`, `queue_`, `unit_`.
- Every key exists in every language with the same positional placeholders. Use positional placeholders only (`%1$s`, `%1$d`), never `%s %s`.
- Counts use `<plurals>` with all CLDR categories of the language (ru: one/few/many/other, en: one/other). Never build plurals with `if (n == 1)`.
- Do not mark user text `translatable="false"` unless it is a brand name or technical token.

### 8.5 Formatting

- File sizes: `Formatters.bytes(resources, bytes)`, base 1024, one decimal, unit labels from `unit_b/unit_kb/unit_mb/unit_gb/unit_tb`, number formatted with the current locale.
- Time (`1:15`) and resolution labels (`1080p`, `4K`) are universal and stay as is.
- The `speed` string from yt-dlp is shown as received.
- Language names in the picker are shown in their own language:

```kotlin
fun displayName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}
```

## 9. Style and tooling

- ktlint and detekt are the default authority: fix findings first. A style finding may be waived only under section 11. `MagicNumber` and `TooGenericExceptionCaught` are enabled; permanent exclusions only for `Dimens.kt`, `Config.kt`, `ui/theme/` and tests.
- Android Lint `MissingTranslation`, `ExtraTranslation` and `PluralsCandidate` are errors.
- Imports: no wildcards, no unused, sorted. Trailing commas. Max 120 characters per line.
- Comments follow section 10.
- Do not add dependencies without a clear need. Say why in the change description.

## 10. Comments

Default: no comments. Code must be readable through names, types and structure. Fix unclear code by renaming or extracting a function, not by explaining it.

A comment is allowed only when the logic is not obvious from the code itself, and it explains why, never what. Typical valid cases:
- a workaround or platform/library quirk (yt-dlp argument order, Android version behavior, a bug in a dependency);
- a non-obvious constraint or invariant that the code cannot express;
- a surprising decision where the obvious approach was tried and does not work;
- a complex regex or algorithm whose intent is not readable from the code;
- the reason for `@Suppress` / `@SuppressLint` (required).

Forbidden:
- comments that restate the code (`// increment counter`, `// returns the user`);
- KDoc that only repeats the function or parameter name. No KDoc by default, including on public API;
- section banners and dividers (`// ---- helpers ----`);
- commented-out code (delete it, git keeps history);
- changelog or author notes in code (`// fixed by ...`, `// added for task 123`);
- TODO/FIXME without a concrete reason.

When you touch a file, delete comments that violate this. Keep the ones that explain a real non-obvious why. Keep a comment short: one or two lines.

## 11. When a rule or a tool may be waived

These rules serve consistency and readability. They are not an end in themselves. If a build tool (ktlint, detekt, Android Lint, compiler warning) complains about how something is written, and fixing it would make the code worse, you may waive that finding instead of contorting the code.

A waiver is allowed only if all of these hold:
1. The finding is about style or form only (naming, formatting, length, structure, "written the wrong way"). Not correctness, behavior, data safety, security or localization.
2. The code stays within the project style after the waiver: same naming, same size grid, same structure as the surrounding code. A waiver must never introduce a new style, only keep the existing one.
3. Following the tool literally is clearly worse (less readable, forces an artificial name or split, or contradicts the surrounding code).

How to waive:
- Narrowest scope first: `@Suppress("RuleName")` on the single line, function or class, with a one-line reason (this is the one place where a comment is required).
- If the same finding is waived 3+ times for the same reason, change the tool config for that rule or path instead of repeating suppressions.
- Never disable a rule globally, lower a severity or edit a baseline just to make the build green without a reason.
- List every waived finding in the final report: rule, place, reason.

Typical acceptable waivers:
- `FunctionNaming` / ktlint function-naming for `@Composable` functions (PascalCase is the Compose convention);
- `LongMethod`, `LongParameterList`, `TooManyFunctions` on composables, `Formatters`-style utility objects and DAO/repository interfaces when splitting would hurt readability;
- `MagicNumber` on trivial math inside an expression (`/ 2`, `0.5f` alpha factors in a single obvious place) where a named constant adds noise;
- max line length for a URL, regex or long resource reference.

Never waivable (these are correctness, not style):
- build or compile errors;
- Lint `MissingTranslation`, `ExtraTranslation`, `PluralsCandidate`;
- swallowing `CancellationException`, `!!`, destructive DB migration, logging secrets;
- breaking the layer direction in section 1;
- hardcoded user-visible text (section 8).

If you are unsure whether a finding is style or correctness, treat it as correctness and fix it.

## 12. Definition of done

Before you finish any change, check:

- [ ] The project builds and lint/ktlint/detekt pass.
- [ ] No new numeric literal outside `Config.kt`, `Dimens.kt` or a named private const.
- [ ] No `.dp`/`.sp`/`Color(0x...)` in screens.
- [ ] No `BuildConfig.DEBUG` or `Log.*` outside `AppLog`.
- [ ] No `!!`, no `runCatching` in suspend code, no swallowed `CancellationException`.
- [ ] Every new user-visible text is in all language folders, with plurals where needed.
- [ ] No comments except for non-obvious why (section 10); no commented-out code.
- [ ] Every waived style finding has a narrow suppression with a reason and is listed in the report (section 11).
- [ ] No behavior changes in refactor-only tasks.
- [ ] New screens follow the `Screen + ViewModel` structure and reuse existing components.

When you finish, report: what changed, what you intentionally left as is and why, which findings you waived and why, and what must be checked manually (Room migrations, each language on a device, screens after snapping to the grid).
