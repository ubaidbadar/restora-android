# Restora mobile product design

The app serves people working through a restaurant service. Screens prioritize the next decision and expose secondary controls when requested. Existing permissions, server records, payment rules and durable drafts remain authoritative.

## Primary journeys

| Task | Information to scan | Main action | Secondary actions |
| --- | --- | --- | --- |
| Start service | Restaurant and branch, daily sales and collections | New order | Change date, inspect orders or guest balances |
| Work a ticket | Token, service/table, guest, items, kitchen state, payment, total | Open ticket, advance kitchen state | Record payment, edit, print, share, cancel |
| Seat guests | Availability, table name/capacity, open orders, requests | Open table | Resolve requests, share menu, manage table |
| Take an order | Search, categories, dish/price, basket total | Review basket, then checkout | Size, add-ons, quantity, notes |
| Recognize a guest | Name, mobile, visits, spend, khata | Open profile | Order history, edit profile, record payment |

## Visual rules

- Preserve Restora's existing indigo identity and serving-dish mark from the web product. Color identifies actions and selection; large financial values use neutral ink.
- White surfaces on a quiet neutral canvas. Fine boundaries group related information; avoid nesting colored cards or treating every label as a heading.
- Use the shared theme type scale. Sales have the strongest hierarchy; order token, guest and amount follow; metadata remains secondary.
- Keep the shape vocabulary to shared theme tokens. Interactive controls retain at least 48dp targets.
- Status always has a text label: amber for waiting/preparing/occupied, green for ready/paid/available/completed, red for cancellation/error, neutral for other states.
- One vertical scroll owner per dataset, stable IDs for records. The basket remains reachable outside the scrolling menu. Long ticket summaries truncate; totals and status can wrap at large font scales.
- Large windows constrain the reading width to 760dp. Auth constrains its form width to 460dp. Both use platform insets rather than assuming fixed status or keyboard heights.

## Product honesty

No fabricated revenue trends, stock photography masquerading as real menu images, fake live status or production sample orders. The dashboard uses its existing one response. Local previews and UI tests use explicitly isolated fixtures.

## Changed in this pass

Quieter sales summary and grouped collections; navigable outstanding balances; compact tickets; differentiated table availability; icon refresh action; workspace selector and account action; selected More tab for secondary routes; back navigation; consistent control edges; shorter task-specific copy; conditional Google sign-in separator.

## Verification

Run the existing unit tests, lint, debug build and UI-test build. Run the inline-auth, form restoration, large-font ticket and native component capture tests when an emulator is available. Before release, review authenticated end-to-end flows on a real small phone, RTL, keyboard, TalkBack and landscape. Component previews cannot substitute for real account and printer/payment tests.

## Review build

`./gradlew :app:assembleDebug -Prestora.designPreview=true` produces **Restora Design** (`app.restora.live.design`) for a side-by-side installation. It uses the same real backend and a separate local session/draft store. The preview is optional; ordinary debug/release builds keep the existing application ID and label. This avoids requiring an uninstall of the previous debug app when a restored build environment uses a new debug signing key.
