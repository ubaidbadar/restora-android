# Android design and engineering audit

Reviewed against `.agent/skills/kotlin-android-excellence/SKILL.md`, added to main by the project owner. The rules file is preserved unchanged. This change follows the original operational-app scope; it does not add missing web administration features or change the working backend.

## Design changes

- The auth screen is an inline sign-in/create-account flow with Restora branding. It no longer opens a generic full-screen form immediately or presents four competing primary auth buttons.
- A shared type scale, button controls with at least 48dp touch height, shapes, neutral surfaces, indigo brand accents, status colors, and reusable list/card/form components carry through the app.
- Dashboard sales and collection information have a clear hierarchy. Dates use a picker, rather than requiring a typed timestamp.
- Orders have compact tickets, status chips, and bottom-sheet details with prominent next actions. Guests, menu, bookings, tables and conversations use focused layouts.
- New orders keep the basket total and review action at the bottom. Basket review and checkout are separate, and irrelevant delivery/table/payment fields are hidden.
- Navigation is grouped by task. Secondary forms use sheets instead of full-screen dialogs. Choice sheets support searching long option lists; price editing has actual size/price fields instead of a textual `name = price` interface.
- Debug-only Compose previews and instrumentation tests are included for sign-in and form restoration. Preview fixtures are never used as production fallback data.

## Engineering findings and changes

| Finding in the previous implementation | Change |
| --- | --- |
| Encrypted preferences, key initialization, JSON parsing and commit could run on Main | A repository owns initialization, parsing and persistence on an injected IO dispatcher. Storage writes are serialized. Durable outbox writes are awaited. |
| Requests used blocking execution without cancelling the transport | Cancellation now cancels the underlying OkHttp call, including while receiving a response. |
| Lists eagerly composed every loaded row | Large screen datasets now have one native lazy scroll owner, stable server IDs, and row content types. WhatsApp uses its receiving-number/phone key because that endpoint does not provide a thread `_id`. |
| The basket lived only in `remember` | ViewModel-owned drafts and checkout values persist through the encrypted repository. Only small UI parameters use saved state. Passwords are excluded from form saved-state Bundles. |
| An old branch request could affect a newer context or loading flag | Context generations and job identity protect results and cleanup; replacing a context does not cancel the very coroutine doing that replacement. |
| HTTP 401 cleared unsent orders along with cookies | Session expiration clears authentication while retaining scoped encrypted drafts/outbox. Explicit sign-out/account deletion still clears local data after confirmation. |
| A restored route could show a screen after its permission was removed | Route access is checked against the latest workplace permissions before rendering cached content. |
| A malformed successful API response could be treated as offline and queued | Invalid response format is distinguished from transport failure; only supported unpaid order writes enter the outbox. |
| Offline double taps could race with persistence | Busy state remains active until the queued order is durable. |
| Streams retried every five seconds | One foreground owner closes the stream on stop; retries use capped exponential backoff, reset after a successful connection. |
| Dependency declarations bypassed the catalog and release optimization was disabled | Existing versions moved into the catalog without upgrades; release optimization enabled and checked with an unsigned build. |

The source of truth remains the existing API and its save responses, plus explicitly labelled local drafts and unpaid queued orders. No new periodic polling, analytics, credentials or deployment changes were added. The existing encrypted preference format is retained; this is not a Room/DataStore migration.

## Verification and limits

Run commands and exact pass/fail results are recorded in the PR. Unit coverage includes API cookies/errors, transport cancellation, read deduplication, stale-context races, session expiration with unpaid orders, persisted cart/checkout restoration, explicit retry of rejected outbox entries, finite numeric validation, foreground stream ownership, and offline double-submit protection.

Instrumentation tests are compiled but require a connected Android device/emulator to execute. No device/emulator is available in this session, so the following are **not verified on a device**: visual appearance, rotation and process-kill behavior, TalkBack/RTL, small/large screens and font scaling, keyboard behavior, Google sign-in, real-account payments, and PC printing. No physical-device startup, frame, memory or battery measurements were made; structural reductions in work are not an FPS/latency claim.

Before publishing, complete those device acceptance checks and configure release signing and Google OAuth as documented in the README.
