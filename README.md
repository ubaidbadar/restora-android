# Restora for Android

Native Kotlin and Jetpack Compose client for the working Restora backend. This repository started as a native Android Studio template; there was no Expo application to remove. The web and backend repositories are not modified.

## Build

Open the project in Android Studio with JDK 25 and install Android SDK 37.0. The checked-in wrapper uses Gradle 9.6.0 and AGP 9.4.1, matching the starter.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Release APKs are unsigned until you configure your own signing key. Do not put signing credentials or keystores in Git.

The API defaults to `https://restora.live/api/v1`. Override the origin at build time (HTTPS only):

```sh
./gradlew :app:assembleDebug -Prestora.apiUrl=https://your-restora-server.example
```

For Google sign-in, configure an Android OAuth client for `app.restora.live` and the signing certificate, then supply the **web OAuth client ID** that matches the backend's `GOOGLE_CLIENT_ID`:

```sh
./gradlew :app:assembleDebug -Prestora.googleWebClientId=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

The Google button stays hidden without this value. No Google client secret belongs in the APK. Email/password, sign-up, verification codes, and password reset work independently.

## Design and rules audit

The UI and Android lifecycle/data handling have been revised against the newly added `.agent` rules. See [the audit and verification limits](docs/android-design-and-rules-audit.md). Android Studio debug previews are in `app/src/debug/java/app/restora/live/DesignPreviews.kt`.

## Native workflows

- Email sign-in, registration, email verification, password reset; optional Google Credential Manager sign-in.
- Restaurant creation, invitation acceptance, restaurant and branch switching.
- Dashboard, dated order lists, order details and activity, order creation/editing, sizes and required add-ons, dine-in/takeaway/delivery, status progression, cancellation, payments and khata settlement.
- Customer search/create/edit, profiles, order history and khata entries.
- Table availability, guest calls, create/edit/archive/restore, guest-menu link sharing.
- Reservation creation/editing and status changes with date/time pickers.
- Menu categories, dish prices, add-on groups, and branch stock controls. Existing deals remain orderable under their sale-day/time restrictions.
- Staff invitations and access, suspension/activation, role permissions, branches, teams, device lists and removal, print-agent pairing codes.
- Restaurant name, tax and receipt settings, ownership transfer and restaurant deletion, account profile/password/PIN, session review/revocation and account deletion.
- WhatsApp conversation history and replies, connected-number invoice setting.
- Text receipt sharing and printing through already configured Restora PC agents.

## Data and connection behavior

`GET /me` runs once at startup. Authentication and workspace changes use the `me` returned by the backend. Screen data is loaded by its parent, retained per user/restaurant/branch, and updated from write responses rather than re-fetching a list after every save. Refresh controls explicitly reload. Permissions come from `workplace.permissions`; the backend remains authoritative.

Sessions use the existing Better Auth cookies. Cookies and cached business data are encrypted with AES-GCM using a non-exportable Android Keystore key. Backups and cleartext transport are disabled. Explicit sign-out clears local business data and unsent orders after confirmation. Session expiry preserves scoped drafts and unpaid queued orders so the same account can restore them after signing in.

The existing `/orders/stream` SSE connection updates orders and guest calls while the app is foregrounded. It closes in the background and reconnects with reconciliation on return. There is no polling loop or extra `/me` fetch per page.

An unpaid new order that cannot reach the server is saved in an encrypted outbox with a stable `clientId` and `placedAt`. The UI explicitly says it has **not reached the kitchen**. It sends when the app reconnects in the foreground, or through Retry. The backend's `clientId` deduplication handles a lost response. Server-rejected orders remain visible for review and are not automatically retried. Payments, khata and edits are online only. The current basket and checkout fields are also saved as encrypted, scoped drafts. Cached records are available offline; completing a previously unseen form requires its data to have been loaded first.

## Scope and release checks

This is the native operational client. Advanced web administration is not reproduced in this first Android implementation: spreadsheet customer imports/merging, complex deal composition and images, POS kiosk provisioning/idle locking, per-printer routing, appearance themes, Meta Embedded Signup, background push delivery and restaurant deletion recovery. Use the existing web portal for those operations. No placeholder data or pretend success responses are used.

Before production distribution, test a real account on an Android device: sign-in/verification, switching between branches with different access, creating and settling each order type, disconnecting and reconnecting an unpaid order, guest calls, PC printing, and Google sign-in with the release signing certificate. Automated contract tests cover native auth headers/path, cookie persistence and scope/deletion, server error propagation, HTML response rejection and timed deals; they do not replace on-device acceptance testing.
