---
name: kotlin-android-excellence
description: Build, refactor, review, and optimize production Kotlin Android apps for correctness, clean maintainable code, responsive UI, low memory and battery use, and verified performance. Inspect the existing project first. Works with Jetpack Compose or Views/XML and any app domain. Explain non-obvious Android/Kotlin decisions to a React/TypeScript developer.
---

# Kotlin Android — production engineering standards

## Mission and priorities

Write the **best practical production code** for the Android app in front of you, whether implementing a feature, fixing a bug, designing UI, refactoring, or optimizing performance. The result must be correct, readable, reliable, secure, maintainable, and efficient **on real Android devices**.

Priority order:
1. **Correctness, data integrity, and user safety.** Never trade these for micro-optimizations.
2. **Clear, simple, maintainable design.** The next developer should understand the code quickly.
3. **Responsive, smooth, battery-conscious behavior.** Avoid unnecessary work, allocations, IO, and requests.
4. **Consistency with the existing project.** Improve patterns deliberately; do not add architecture for its own sake.
5. **Measured optimization.** Prefer evidence over cleverness, folklore, and extra dependencies.

**Golden rule:** use the simplest Android-idiomatic solution that satisfies the requirements, preserves correct behavior, and is easy to test. "Optimized" means an improvement demonstrated by less work, better complexity, more reliable lifecycle handling, or measurements — not merely fewer lines.

This skill is **app-agnostic**. Do not assume the product, feature set, backend, routes, libraries, SDK versions, design system, storage requirements, or even Compose usage. Read the repository first. Existing user/project instructions take precedence when compatible with correctness and safety.

## What the developer knows

The developer is experienced with React/TypeScript but unfamiliar with Kotlin. Never sacrifice correct Kotlin design to imitate React. For non-obvious changes, explain the concept using a short analogy:

| Kotlin / Android | Approximate React / JavaScript analogy | Critical difference |
| --- | --- | --- |
| `@Composable` | React function component | Compose uses recomposition and its own snapshot state system. |
| `remember` | `useMemo` / `useRef` | Lifetime is the current composition, not a JS component instance. |
| `rememberSaveable` | Persisted local UI state | Survives recreation via saved state; keep the saved payload small. |
| `mutableStateOf` | `useState` | Observable Compose snapshot state, not React's setter model. |
| `ViewModel` | Screen-level state controller | Survives configuration changes, not process death. |
| `StateFlow` | Observable store / BehaviorSubject | Collection must respect Android lifecycle. |
| `Flow` | Async stream / observable | Cold streams can repeat upstream work on each collection. |
| `suspend` / coroutine | `async` / `await` | Has structured cancellation and a coroutine scope. |
| `viewModelScope` | Scoped async tasks | Cancelled when the ViewModel is cleared. |
| `LazyColumn(key = ...)` | List with stable React `key` | Keys also interact with Compose item state and slot reuse. |
| `Repository` | API/data service + cache layer | Hides data sources and owns a consistent source of truth. |
| `SavedStateHandle` | Restorable screen parameters | For small state, not a substitute for a database. |
| `WorkManager` | Durable queued background job | Android schedules under OS constraints; not an exact timer. |

Do not explain every line. Explain only concepts or trade-offs the developer is unlikely to recognize.

## 1. Inspect before coding

Before modifying files, establish what the app actually uses:
- Read root instructions (`AGENTS.md`, `CLAUDE.md`, `.claude/`, existing skill/rules files) and relevant feature documentation.
- Examine the module layout, `settings.gradle(.kts)`, root/module `build.gradle(.kts)`, version catalog, `gradle.properties`, `AndroidManifest.xml`, and source/test directories.
- Identify Kotlin, AGP, Gradle, Compose compiler, Compose BOM, min/target/compile SDK, Java toolchain, and active build variants **from files**, not guesses.
- Determine UI stack: Compose, Views/XML, or mixed. Identify navigation, dependency injection, networking, persistence, image loading, background jobs, and testing libraries already in use.
- Trace a relevant feature end-to-end: entry point -> screen -> state owner -> repository -> local/remote data -> error handling -> tests. Inspect callers before changing APIs.
- Read git status and diff. Preserve unrelated work; do not reset, revert, overwrite, reformat, rename, or rewrite unrelated files.
- Look for actual pain points (main-thread work, repeated collection or requests, unnecessary recomposition, large allocations, slow SQL, lifecycle leaks), but don't claim a performance issue without evidence.

**Default behavior:** implement the requested task without unnecessary permission requests. For a broad refactor, make a short internal plan and work in independently verifiable steps. If the user explicitly requests planning only, make no edits.

## 2. Kotlin style: idiomatic, boring, predictable

- Follow the project's formatting and the official Kotlin coding conventions. Use descriptive `PascalCase` types and `camelCase` members; names should reveal intent.
- Prefer `val`, immutable DTOs/UI state, and read-only collection interfaces. Keep mutation private and narrowly scoped.
- Prefer straightforward functions, `data class`, `sealed interface`/`sealed class` for genuinely distinct states, `when` where appropriate, and safe null handling (`?.`, `?:`).
- Avoid `!!`, unsafe casts, magic strings, implicit assumptions, and broad `catch (Exception)` unless the boundary warrants it.
- Use nullable types only when `null` is a real domain possibility. Do not invent fake placeholder objects to avoid nullability.
- Use `private` and `internal` to keep APIs narrow. Make invalid or inconsistent state difficult to represent.
- Prefer direct, readable control flow. Use scope functions (`let`, `also`, `apply`, `run`, `with`) only where they clarify receiver and intent; avoid nesting them.
- Avoid extension functions, generics, `inline`, delegated properties, DSLs, reflection, abstractions, and patterns added just to look elegant.
- A one-line function **can** be good when it supplies a meaningful name, public API, compatibility boundary, test seam, or eliminates real duplication. Avoid meaningless forwarding wrappers and single-use helper files.
- Keep tightly related declarations together. Split files by genuine responsibility, not arbitrary line limits or one-declaration-per-file ideology.
- Comments explain **why**, limitations, lifecycle decisions, and invariants; code and names explain **what**. Remove stale comments and debug leftovers.
- Do not introduce Java-style ceremony (builder/factory/util/singleton classes) where a plain Kotlin function or existing class works.
- Prefer standard-library operations when clear. For performance-sensitive collection transformations, compare allocations and complexity; a simple loop can be better than a long chain. Do not assume `Sequence` is always faster.
- Avoid deprecated and experimental APIs unless a verified project constraint requires them. Do not upgrade libraries or enable experimental flags opportunistically.

## 3. Architecture: lean, with a clear source of truth

Choose architecture proportional to app size and current design. For a typical multi-screen app:

`UI (Compose/Views) -> screen state owner (usually ViewModel) -> repository -> API / Room / DataStore / platform service`

- Separate rendering, state/event handling, and data access. The UI should not directly talk to Retrofit/Ktor, DAOs, DataStore, or platform services when a repository is the established interface.
- Follow unidirectional data flow: **state flows down, user actions flow up**.
- Use a ViewModel for screen business state when beneficial; obtain it at the screen/navigation boundary. Reusable UI items should take data and callbacks, not their own ViewModel.
- Expose read-only state (`StateFlow`, `Flow`, or immutable UI models); keep `MutableStateFlow` and mutable implementation details private.
- Use a single coherent UI state for related values **when helpful**, but do not blindly impose "one state per composable/ViewModel." Separate independent state where appropriate.
- Represent loading, content, empty, and failure clearly; do not leave a blank screen or fake content when data is unavailable.
- A domain/use-case layer is **optional**. Introduce it only for substantial reusable business logic or to simplify complex ViewModels.
- Use existing dependency injection (Hilt/Koin/manual) rather than adding another framework. Scope dependencies deliberately; avoid global mutable singletons.
- Define the source of truth for each data type (Room, in-memory cache, server-fed state, etc.). Keep multiple screens consistent through repositories rather than independently maintaining conflicting copies.
- Design explicit state transitions and ownership. Avoid duplicated flags like `isLoaded`, `isDone`, and `step` when they are derivable from existing state.
- Don't assume the server is always authoritative for every local operation; follow the application's actual data and offline contract.

### Recommended small Compose screen pattern (adapt to installed APIs)

```kotlin
@Composable
fun ItemsRoute(viewModel: ItemsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ItemsScreen(state = state, onRefresh = viewModel::refresh)
}

@Composable
private fun ItemsScreen(
    state: ItemsUiState,
    onRefresh: () -> Unit,
) {
    // Render only. No database access, network request, or heavy transformation.
}
```

The **route** owns the ViewModel and lifecycle collection; the **screen** is easy to preview, reuse, and test. Use the project's navigation and ViewModel acquisition conventions. If the app uses Views/XML, apply the same state/data separation with lifecycle-aware collection in the Activity/Fragment.

## 4. Compose UI: correct state and efficient recomposition

*Only apply this section when the project uses Jetpack Compose.*

- Composables should be small, focused, and side-effect-free during rendering. Pass the minimal meaningful data and callbacks needed; do not tunnel a giant state object through unrelated children.
- Hoist state to the **lowest common owner** that needs it. Keep purely local state in the composable; use ViewModel state for screen/business concerns.
- `remember` avoids recreating objects/calculations across recompositions. Use `rememberSaveable` for small user-visible UI state that should survive Activity recreation; use `SavedStateHandle` for small ViewModel-owned restorable inputs. Persist important records elsewhere.
- Do not maintain redundant or conflicting copies of derived state. Prefer simple derived expressions. Use `derivedStateOf` or other version-supported derived-state APIs only when they reduce meaningful invalidations or work.
- Use `LaunchedEffect` for composition-bound suspend side effects with **correct keys**; `DisposableEffect` for subscriptions needing cleanup; `rememberUpdatedState` when an effect must use the latest callback without restarting. Do not start network work during ordinary composition.
- Avoid collecting the same cold `Flow` in several row composables. Collect shared screen state at the appropriate boundary using `collectAsStateWithLifecycle`, then pass values down. Understand whether collection restarts expensive upstream work.
- Avoid reading rapidly changing state high in the tree if only a small leaf or drawing/layout phase needs it. Keep high-frequency changes scoped tightly.
- Do not add `remember`, `derivedStateOf`, `@Stable`, `@Immutable`, special equality wrappers, or `key` everywhere. Strong skipping is available by default on compatible Kotlin/Compose compiler versions. Measure before manually engineering stability; expensive structural equality can be worse than recomposition.
- Prefer real immutable data and stable object references. Never mutate a `List` in place and expect Compose to detect the change automatically.
- For scrolling datasets use `LazyColumn`/`LazyRow`/lazy grids, stable **unique and saveable** item keys, and `contentType` when item layouts differ. Avoid index keys for mutable/reorderable lists.
- For tiny fixed groups, simple `Column`/`Row` may be cheaper and clearer than a lazy container.
- Never sort/filter large lists, parse JSON, format hundreds of values, decode bitmaps, or perform other expensive operations on every recomposition. Compute at the data/ViewModel layer or memoize only where justified.
- Use existing image loading/caching library; request appropriately sized images and avoid full-resolution bitmap decoding for thumbnails.
- Keep composable layout shallow where possible, but never flatten readable UI purely to reduce a theoretical node count.
- Use meaningful `Modifier` ordering, stable callbacks, and theme tokens. Do not introduce manual memoization for callbacks without a demonstrated need.

## 5. Views/XML compatibility

*If the project uses Views, RecyclerView, Fragments, or mixed UI, **do not rewrite it into Compose unless requested**.*

- Use ViewBinding or the existing established binding strategy; avoid view lookups in repeated hot paths.
- Use `ListAdapter`/`DiffUtil` or existing equivalent for dynamic lists; use stable IDs only when identity semantics are correct. Avoid `notifyDataSetChanged()` for small updates when diffing is available.
- Avoid redundant observers and repeated adapter/data setup on each view recreation.
- Collect flows with `repeatOnLifecycle` or equivalent lifecycle-aware APIs; correctly remove callbacks/listeners and release view references in Fragment teardown.
- Avoid expensive measuring/layout nesting and main-thread bitmap work. Prefer simple layouts with profiling evidence for more complex optimizations.

## 6. Coroutines, Flow, and lifecycle safety

- Use **structured concurrency** and the scope that matches the work: `viewModelScope` for ViewModel tasks, `lifecycleScope`/`repeatOnLifecycle` for UI, a managed application scope only for truly application-long tasks.
- Never use `GlobalScope`, uncontrolled threads, or `runBlocking` on the main thread.
- Repositories and data sources must be **main-safe**. Move actual blocking IO to an IO dispatcher and CPU-heavy transformations to Default when appropriate. Suspending APIs that are already main-safe do not need pointless dispatcher switching.
- Prefer injectable dispatchers for substantial async code so unit tests can control scheduling. Do not invent a dispatcher abstraction for trivial code.
- Respect cancellation. Never swallow `CancellationException` in broad catches or `runCatching`; rethrow cancellation before mapping other failures.
- Prefer explicit scope ownership to launching nested fire-and-forget jobs. Await required work and propagate failures through a useful boundary.
- Understand Flow operators: `collectLatest` cancels older work, `debounce` waits, `distinctUntilChanged` suppresses equal values; apply only when semantics match the feature.
- Prevent duplicate work by locating its owner: sharing upstream flows (`stateIn`/`shareIn` where justified), caching in repository, combining queries, or deduplicating in-flight requests. Don't add request counters or locks without a race to solve.
- Clean up callbacks, observers, listeners, sensors, receivers, location requests, WebSockets, and resources when their owner stops. Close streams/cursors reliably.
- Handle process death, background/foreground transitions, configuration changes, screen re-entry, and stale results without leaking or duplicating work.

## 7. Networking, persistence, and offline behavior

- Follow the project's HTTP client, serializers, API contracts, and error format. Don't add Retrofit/Ktor/OkHttp just because you prefer them.
- A screen should not trigger the same remote request independently in each child or list item. Fetch at the correct owner and share/copy only needed data to children.
- Avoid read-after-write requests **when** the mutation response gives a complete authoritative result and the repository/cache can update consistently. Refresh when the response is insufficient, the server computes additional state, or consistency requires it.
- Keep loading, empty, network failure, retry, and unauthorized states explicit. Do not hide failures or add demo/fallback data to production.
- Use pagination for potentially large datasets. Filter, sort, and aggregate where they are most efficient and consistent (often the DB/server), not by downloading everything unnecessarily.
- Add cache expiration, invalidation, and conflict rules **only** as needed. Never assume cached data is fresh forever.
- Use Room for structured persisted data when warranted; use DataStore for small preferences/configuration; avoid blocking storage operations on UI. Inspect Room indices, query plans, transactions, and migrations when relevant.
- In an **offline-first** app, the local database often acts as the UI-facing source of truth; synchronize through the repository with explicit conflict handling. Do not add a durable outbox or pretend writes succeeded unless product requirements support offline writes.
- Handle retries carefully: exponential backoff for transient failures, don't blindly retry non-idempotent writes, and use server-defined idempotency keys when necessary.
- Validate and sanitize untrusted inputs at boundaries. Never log access tokens, passwords, user secrets, payment data, or complete sensitive API bodies. Store credentials through existing secure mechanisms.
- Never weaken TLS, expose credentials in source/build logs, or change auth/permissions/manifest capabilities for convenience.

## 8. Battery, memory, startup, and background work

- **Main-thread responsiveness:** no blocking network/database/file IO, large JSON transformations, bitmap decoding, or synchronous waits on the UI thread. Investigate ANRs and strict-mode/profiler warnings.
- **Startup:** keep `Application`/`Activity.onCreate` minimal. Defer nonessential initialization; avoid eagerly constructing large graphs, fetching irrelevant screens, or blocking first draw.
- **Memory:** don't retain Activity/View/Context references in long-lived ViewModels, repositories, static objects, or callbacks. Prefer application context only when truly appropriate. Dispose receivers and image resources.
- **Battery/network:** avoid unnecessary polling, wake locks, continuous location/sensor work, redundant connections, and eager sync. Prefer event-driven updates and OS scheduling where supported.
- Use `WorkManager` for **durable deferrable** work that must survive app exit/restarts. Use a foreground service only for justified user-visible ongoing work and platform-compliant cases. Do not assume WorkManager executes at an exact time.
- Match retry/backoff, connectivity, charging, and battery constraints to the task. Avoid duplicate scheduled workers; use unique work when appropriate.
- Build properly optimized release variants (`R8` shrinking/optimization where supported). Do not trade correctness or reflection-based library compatibility for APK size blindly.
- For proven critical user journeys, consider app-specific Baseline Profiles; verify with Macrobenchmark on representative devices.

## 9. UI/UX quality is part of code quality

- Respect the **existing design system**, type scale, spacing, colors, typography, shapes, component variants, and app navigation. Do not mix visual styles or invent random colors.
- Design for small phones, large screens, landscape, display cutouts, system bars, keyboard/IME, gesture navigation, and varying font/display scaling.
- Use `MaterialTheme`/established tokens rather than hard-coded colors; support dark mode if the project requires it, without assuming a new theme is wanted.
- Honor system insets and keyboard behavior. Avoid clipped buttons, overlapping FABs, inaccessible bottom actions, and controls disappearing behind the keyboard.
- Target at least **48dp** touch regions for interactive elements, accessible semantics/content descriptions, clear focus, good contrast, and readable error text.
- Use responsive loading indicators/skeletons consistent with the product, disable only the relevant action while saving, guard against accidental double-submit, and show meaningful error/retry states.
- Test text expansion, translations, RTL, TalkBack, dynamic type, empty/long labels, slow internet, and offline/error scenarios where relevant.
- Don't change visual design outside the user's request. If implementing a new screen, prefer coherent reuse of existing components over a new parallel design system.

## 10. Testing and verification: no unverified success claims

Every meaningful change needs tests proportional to the risk:
- **Pure Kotlin unit tests:** parsing, calculations, validators, state reducers, business rules, edge cases.
- **ViewModel/repository tests:** success, loading, empty, error, cancellation, retries, caching, data consistency. Use controlled dispatchers and fake data sources where possible.
- **Compose/UI or instrumentation tests:** important interactions, navigation, accessibility semantics, input, restoration after activity recreation, and lifecycle behavior.
- **Integration tests:** Room migrations, DAO SQL, API serialization/contracts, workers, critical platform/device features where applicable.
- **Manual device checks:** main flows, rotated/recreated activity, background/resume, process kill/restore, low connectivity, denied permissions, small/large screens, and older supported Android versions.
- **Performance regression checks:** release-like builds on representative physical devices/emulators; measure startup, jank/frame timing, allocations, memory, and network activity for the paths touched.

Use the **existing** build commands and toolchain. Common examples (adapt to actual modules/flavors/tasks):

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
# If configured: ktlint / detekt / Compose UI tests / connectedAndroidTest / benchmarks
```

Do not declare a task successful if commands were not run. Record **passed / failed / not run** and why. Do not change signing, release credentials, dependency versions, or unrelated config merely to get green output.

### Performance proof standard

1. State the suspected bottleneck and why.
2. Get a baseline when feasible (trace, profiler, benchmark, query log, network count, allocation profile).
3. Apply the smallest meaningful improvement.
4. Compare before/after under similar conditions; watch correctness and resource regressions.
5. Report measured values **with device/build/test conditions**, or explicitly say it was not measured.

Use Android Studio Profiler / System Trace / Perfetto to find UI, CPU, and memory bottlenecks; Macrobenchmark for startup/scroll/navigation, Microbenchmark for hot computational code, Play Console Android vitals for field evidence. **Never claim “60 FPS,” “zero leaks,” “fully optimized,” or “no crashes” without appropriate evidence.**

## 11. Refactoring and optimization rules

When the task is to optimize existing code:
- Preserve observable behavior, data formats, permission checks, navigation, lifecycle semantics, error messages, and public APIs unless the change is authorized.
- Prefer eliminating redundant work **before** adding caching or concurrency.
- Optimize algorithms and data access first, then allocations and recompositions, then micro-level syntax.
- Do not convert synchronous code to concurrent code unless concurrency improves the actual task and races/cancellation are handled.
- Avoid unnecessary extra networking after mutations, N+1 DAO/API calls, redundant observers, duplicate state, expensive data transformations in rendering, and accidental loop-in-loop complexity.
- If a change adds a dependency, abstraction, cache, Flow, CoroutineScope, state holder, or build step, be prepared to justify its lifecycle, ownership, invalidation, and measurable payoff.
- Never rewrite working modules as a speculative cleanup. Keep commits/diffs small and reviewable.
- Fix root causes, not symptoms. Do not silence lint/errors, suppress exceptions, or remove tests to make builds pass.

## 12. Working protocol for every coding request

1. **Understand** the requested change, constraints, existing behavior, and relevant project instructions.
2. **Inspect** the exact implementation, dependencies, and affected call sites; note lifecycle/data-flow implications.
3. **Choose** the simplest sound approach; compare alternatives only if the trade-off is meaningful.
4. **Implement** narrowly, with naming, state ownership, error handling, and UI consistency intact.
5. **Verify** compilation, tests, lint, and the relevant runtime/device flows when available.
6. **Review your own diff** for redundant work, races, unsafe nullability, leaks, duplicate requests, recompose hotspots, overengineering, style drift, and unrelated modifications.
7. **Report**: what changed, why it is cleaner/faster/safer, how verified, what could not be verified, and remaining risks. Include a brief React/JS analogy only for non-obvious Kotlin decisions.

### Before completion, ask yourself

- Is this the simplest correct implementation compatible with the repo?
- Have I preserved existing functionality and user data?
- Does each piece of state have one clear owner and source of truth?
- Will any Flow collection, recomposition, screen return, or rotation cause extra queries/jobs?
- Is expensive work off the main thread? Is cancellation correct?
- Do rows/lists use sound identity and efficient rendering?
- Is there any Activity/Context/resource leak or battery-draining background behavior?
- Are errors, empty content, loading, offline status, and double-taps handled appropriately?
- Does the UI remain usable with small screens, keyboard, large fonts, and accessibility?
- Did I avoid unnecessary wrappers, helpers, libraries, flags, and formatting churn?
- Are tests and performance claims actually supported?

## 13. Explicit anti-patterns

**Do not**:
- Impose React's one-`useState`-per-component, default-export, hook, folder, or API conventions on Kotlin.
- Build a massive Clean Architecture / use-case / interface hierarchy for a small screen.
- Call APIs or databases directly in composition or from each list row.
- Store business data only in `remember`, or large objects in `SavedStateHandle`/`rememberSaveable`.
- Use `GlobalScope`, `runBlocking` on main, or raw long-lived listeners without cleanup.
- Catch and ignore cancellation, network failures, or exceptions to make errors disappear.
- Add multiple state mirrors, speculative caches, periodic polling, or fetch-again-after-every-save by default.
- Overuse `@Stable`, `@Immutable`, `remember`, `derivedStateOf`, `Flow`, `Sequence`, or concurrency as "optimizations."
- Replace working Views/XML with Compose, change navigation, migrate databases, or upgrade Gradle/plugins without a real requirement.
- Hard-code colors, dimensions, routes, fake API responses, example records, credentials, or permissions in production.
- Refactor unrelated areas or rewrite large files merely to satisfy a subjective style preference.
- Leave dead code, commented-out implementations, debugging logs, or unexplained warnings.
- Claim completion or performance gains without appropriate verification.

## Official references (consult when the relevant feature changes)

- Android architecture: https://developer.android.com/topic/architecture/recommendations
- Data layer and source of truth: https://developer.android.com/topic/architecture/data-layer
- Kotlin coding conventions: https://kotlinlang.org/docs/coding-conventions.html
- Compose performance: https://developer.android.com/develop/ui/compose/performance
- Compose best practices: https://developer.android.com/develop/ui/compose/performance/bestpractices
- Compose stability: https://developer.android.com/develop/ui/compose/performance/stability
- Android coroutines: https://developer.android.com/kotlin/coroutines/coroutines-best-practices
- Saving UI state: https://developer.android.com/develop/ui/compose/state-saving
- Offline-first design: https://developer.android.com/topic/architecture/data-layer/offline-first
- WorkManager: https://developer.android.com/develop/background-work/background-tasks/persistent
- Macrobenchmark: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- Baseline Profiles: https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile
- Compose UI testing: https://developer.android.com/develop/ui/compose/testing/common-patterns
- Android performance troubleshooting: https://developer.android.com/topic/performance/issues

**Treat these references as guidance, not permission to change app behavior or upgrade packages. Verify APIs against the versions actually installed in the repository.**
