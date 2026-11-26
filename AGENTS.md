# AGENTS.md

Grabbit is an Android media downloader (Kotlin, Compose Material 3, Hilt, Room, Coroutines/Flow). Apply these rules to all new code and files you touch. Avoid unrelated refactors.

## 1. Architecture
- Packages: `core` (pure helpers), `data` (repositories/DB/engines), `service`, `di`, `ui` (theme/components/screens).
- Dependencies: `ui → data/core`, `data → core`; `core` must not depend on `ui` or `data`.
- One screen per `XxxScreen.kt` + `XxxViewModel.kt`; shared UI in `ui/components/`. Split screen files over ~300 lines into same-package composables.
- `Routes` lives in `ui/Routes.kt`. File order: constants → public API → private details → previews.

## 2. Constants and UI
- No magic numbers except `0`, `1` and obvious math. Name values and units (`_MS`, `_BYTES`, `_KBPS`) or use `Duration` (`60.seconds`).
- One-file constants: top-level `private const val`; shared constants: `core/Config.kt` grouped by purpose; UI dimensions: `ui/theme/Dimens.kt`. Never duplicate values.
- Name timeouts, retries, delays, debounce, buffers, limits, bitrates, thresholds, notification IDs and size/progress conversions.
- No raw `.dp` in screens. Use `Dimens` and a 4dp spacing grid; component-only sizes may be private constants. Line thickness may be a named 1dp/2dp constant.
- Use `MaterialTheme.shapes`, `colorScheme` and `typography`. No custom `Color(0x...)` outside theme or `fontSize = N.sp` outside `ui/theme/Type.kt`. Reuse/extract shared components.

## 3. Logging and errors
- All logs go through `core/AppLog.kt`. No `BuildConfig.DEBUG`, direct `Log.*`, `println` or `printStackTrace` elsewhere. Debug messages use lambdas; tags are `private const val TAG`. Never log secrets.
- Catch `Throwable` only at yt-dlp/ffmpeg boundaries, after `catch (CancellationException) { throw e }`.
- No `runCatching` in suspend code unless the helper rethrows `CancellationException`. Never swallow cancellation.
- No `!!` or empty `catch`; handle null explicitly and log handled errors with `AppLog.e`.
- User-facing errors use `AppError` and `@StringRes`; raw yt-dlp/ffmpeg output belongs only in technical details. Every suppression needs a brief reason.

## 4. Shared patterns and database
- ViewModel flows use `flow.stateInUi(viewModelScope, initial)` with `Timing.FLOW_SUBSCRIPTION_GRACE`; do not duplicate `stateIn(...WhileSubscribed(5_000)...)`.
- Use `Formatters.percent`; no manual `(x * 100).toInt()`. Avoid forwarding-only aliases. Extract a helper when the same 3+ lines recur for a third time.
- Never use `fallbackToDestructiveMigration`: preserve download history. Enable Room `exportSchema`, commit schemas and add an explicit `Migration` for every schema change.

## 5. Localization
- All user-visible text (Compose, descriptions, notifications, toasts, errors) comes from resources. No hardcoded sentences or sentence-building interpolation; use positional formats (`%1$s`, `%1$d`).
- Translation sources: `app/src/main/i18n/<language>/*.xml` (e.g. `en/strings.xml`, `ru/strings.xml`). No `values/` folders there. Gradle generates Android `values*` resources under `build/`; never edit/commit generated files. No `res/values*/strings.xml`.
- Add a language by creating `i18n/<ISO-code>/*.xml` with every key. Folder names drive generated locale config and `BuildConfig.SUPPORTED_LANGUAGES`; do not duplicate language lists in Kotlin/XML. `Languages.DEFAULT_LANGUAGE = "en"` is the sole named default and matches `unqualifiedResLocale`.
- Keep only app-owned strings. Use `<area>_<meaning>` keys (`settings_`, `error_`, `action_`, `notif_`, `status_`, `nav_`, `convert_`, `history_`, `queue_`, `unit_`). Keys and positional placeholders must match across languages.
- Use full CLDR plural categories (`ru`: one/few/many/other; `en`: one/other); never build plurals with conditionals. `translatable="false"` is only for brand names/technical tokens.
- File sizes: `Formatters.bytes(resources, bytes)`, base 1024, one decimal, locale-aware number and translated `unit_b/kb/mb/gb/tb`. Keep time (`1:15`), resolutions (`1080p`, `4K`) and yt-dlp speed unchanged. Show language names in their own locale (`locale.getDisplayName(locale)`).
- Android Lint `MissingTranslation`, `ExtraTranslation` and `PluralsCandidate` are errors.

## 6. Style, comments and exceptions
- ktlint/detekt are default style checks. Keep `MagicNumber` and `TooGenericExceptionCaught` enabled; permanent exclusions only for `Dimens.kt`, `Config.kt`, `ui/theme/` and tests.
- No wildcard/unused imports; sort imports, use trailing commas and keep lines within 120 characters where practical. Explain any added dependency.
- Default: no comments. Add a short comment only for non-obvious **why** (platform/library quirks, workarounds, hidden invariants, surprising constraints, complex regex/algorithms, suppression reasons). No comments restating code, redundant KDoc, banners, commented-out code, author notes or TODO/FIXME without a concrete reason. Remove such comments when touching a file.
- A tool finding may be waived only for style (never correctness, behavior, security, data safety or localization), when the result still matches project style and literal compliance hurts readability. Use the narrowest `@Suppress` with a one-line reason. If repeated 3+ times, adjust scoped config. Never disable rules globally, lower severity or alter baselines just to pass. Report each waiver (rule/location/reason).
- Possible waivers: Compose `FunctionNaming`, `LongMethod`/`LongParameterList` when splitting harms clarity, trivial obvious math, long URLs/regexes. Never waive build errors, translation/plural errors, swallowed cancellation, `!!`, destructive migrations, secret logging, layer violations or hardcoded user text. When unsure, fix it.

## 7. Parallel agents (Git Worktrees)
- Delegate independent tasks when subagents are available. The main agent plans, assigns scopes/dependencies, reviews and integrates. If subagents cannot be launched, say so; never pretend they were started.
- Before delegation, read this file, inspect `git status`, split tasks and avoid overlapping files. Assign each worker a clear goal, file scope and deliverables. Give shared files (Gradle, navigation, `Config`, `Dimens`, DB/DI, `i18n`, shared UI) one owner or handle them sequentially.
- Each worker gets its own worktree and branch `agent/<task>`, created from the same recorded base commit. Never share a working directory/branch or edit another worker's files. Example:
  ```bash
  BASE=$(git rev-parse HEAD)
  git worktree add ../grabbit-worktrees/ui -b agent/ui "$BASE"
  ```
- Workers read `AGENTS.md`, stay within scope, inspect their diff, build/check when practical, commit to their branch and report commit hash, files, status, dependencies and blockers. Workers do not merge.
- The main agent reviews and merges completed branches one at a time, resolves conflicts deliberately, then reviews the combined diff and builds/checks. Never blindly choose a conflict side, reset another worker's work or delete unmerged changes.
- Remove worktrees/branches only after confirming commits are integrated and worktrees are clean.

## 8. Done and final report
- [ ] Build and applicable lint/ktlint/detekt checks pass; report anything not run.
- [ ] No unnamed numeric literals, raw screen dimensions/colors/text styles, logging outside `AppLog`, `!!` or unsafe suspend `runCatching`.
- [ ] New strings exist in every language with matching placeholders/plurals; comments and waivers follow section 6.
- [ ] Refactor-only tasks preserve behavior; architecture and shared components follow these rules.
- [ ] For parallel work, review integrated commits, resolve conflicts and retain all unmerged work.

Report changes, intentional omissions/reasons, waivers, build/check results and manual checks still needed. For parallel work, also report agents/tasks, branches/commits and integration conflicts.
