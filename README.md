# Green Wells Loyalty — Pump Attendant App

A native Android/Kotlin/Jetpack Compose implementation of the pump-attendant side of the Green
Wells fuel loyalty cashback system. It is offline-first and talks to the real NestJS backend
documented in `ANDROID-HANDOVER.md`. Built from the "Attendant App" Claude Design mock (Material
3, petrol/teal accent, tabular numerals for money and litres).

This app covers **only** the attendant workflow — login, record a sale, today's sales, the sync
queue, and profile/help. It does not implement the admin dashboard (price configuration,
special-rate approval, customer imports, disbursement, reconciliation oversight); those are the
responsibility of a separate web app talking to the same backend.

## Architecture

Single-module MVVM + repository pattern:

```
core/          Pure Kotlin helpers with no Android UI dependency:
                 money/        KES + litre formatting (tabular numerals)
                 phone/        Kenyan MSISDN normalization/validation
                 cashback/     CashbackCalculator — the one place the cashback formula lives
                 connectivity/ ConnectivityObserver — Flow<Boolean> over ConnectivityManager
                 session/      SessionManager — persisted signed-in attendant/station
                 result/       AppResult<T> — success/failure with a plain-language message

data/
  local/       Room: entities, DAOs, LoyaltyDatabase, TypeConverters
  remote/      api/   Retrofit interfaces (AuthApi, MobileApi) matching the real backend contract
                       in ANDROID-HANDOVER.md
               dto/   Wire types (kotlinx.serialization)
               NetworkErrors.kt — maps the documented error envelope to attendant-facing copy
  repository/  Offline-first repositories: Room is the source of truth for reads; writes go to
               Room first, then attempt the remote API opportunistically

sync/          SyncWorker (WorkManager CoroutineWorker) + SyncScheduler (periodic + on-demand)

di/            Hilt modules: DatabaseModule, NetworkModule, DispatcherModule

ui/
  theme/       Color/Type/Shape translated from the design system's CSS tokens
  components/  Shared building blocks: NumericKeypad, StatusPill, TopStatusBar, SyncBanner,
               AppBottomNav, MoneyText/TabularText, buttons, cards, empty/loading states
  navigation/  5-destination NavHost (login, new sale, today, profile, daily summary) + sync
               queue pushed on top
  auth/        Login screen + ViewModel (employeeId + PIN, session restore/expiry check,
               periodic sync kickoff)
  sale/        The "New sale" flow — one ViewModel (SaleFlowViewModel) driving six screens
               (lookup → create/blocked → entry → review → success). The create step doubles as
               the new-customer registration flow (pending supervisor approval, not a sale).
  today/       Today's sales + stat cards + reconciliation warning
  syncqueue/   Pending / syncing / needs-review / synced / failed / conflict records, with retry
  summary/     End-of-shift daily-summary screen (`/mobile/daily-summary`)
  profile/     Identity, app health, operational help, sign-out (with pending-sales warning)
```

## Running it

Requires the real NestJS backend (see `ANDROID-HANDOVER.md`) running and reachable at the URL
configured in `app/build.gradle.kts`'s `API_BASE_URL` build config field — defaults to
`http://10.0.2.2:8080/api/v1/`, the standard Android-emulator alias for the host machine's
localhost, assuming the backend runs locally on `:8080`. Repoint that value for a real device or
a deployed backend.

Open the project in Android Studio (AGP 9.1.1 / Kotlin 2.2.10 / compile & target SDK 36, min SDK
29) and run the `app` configuration, or from the command line:

```
./gradlew assembleDebug        # build the APK
./gradlew installDebug         # install on a connected device/emulator
./gradlew testDebugUnitTest    # run unit tests (no device needed)
./gradlew connectedAndroidTest # run instrumented + Compose UI tests (needs a device/emulator)
```

Sign in with a real attendant's `employeeId` + PIN (issued by a Station Supervisor/Admin from
the web app) — there is no demo/offline-only mode. Login caches station + current prices via
`GET /mobile/bootstrap` so the rest of the shift can run offline.

To exercise the offline states, turn off the device/emulator's network (airplane mode) and
record a sale — it saves locally, the success screen reads "Saved on this device… will sync when
online", and it shows up as "Waiting to sync" in the Sync Queue tab. Reconnect and either wait
for the periodic worker or tap "Sync now" on the amber banner / Profile screen.

## Where backend endpoints are configured

Every API the app needs is defined as a plain interface in `data/remote/api/Apis.kt`
(`AuthApi` for login, `MobileApi` for every other `/mobile/*` route), matching the real backend
contract in `ANDROID-HANDOVER.md` exactly. `di/NetworkModule.kt` builds the `Retrofit` instance
(base URL from `BuildConfig.API_BASE_URL`, `OkHttpClient` with a bearer-token `AuthInterceptor`
that also clears the session on a `401`, `kotlinx.serialization` converter) and provides both
interfaces via `retrofit.create(...)`.

## Offline-first / sync approach

- **Room is the source of truth.** A sale is written to Room as `PENDING` before any network
  call is attempted — nothing is ever lost because a submit failed or the phone had no signal.
- **Idempotency.** Each sale gets a client-generated UUID (`localSaleId`) that doubles as the
  `idempotencyKey` sent to the backend, enforced locally by a unique index on that column
  (`INSERT OR IGNORE`) and by the server treating a retried key as the original sale. Retried
  sync attempts — from WorkManager backoff, a manual "Sync now", or process death mid-request —
  can never create a duplicate sale. `SaleDaoTest` covers the local half of this.
- **Sync states** (`SyncStatus`): `PENDING` → `SYNCING` → `SYNCED`, or `NEEDS_REVIEW` (recorded,
  but the on-device cached price disagreed with the server's authoritative calculation — not a
  failure) / `FAILED` (retryable, automatic backoff + manual retry) / `CONFLICT` (server rejected
  it — needs a supervisor, never silently discarded or auto-resolved). SMS delivery (`SmsStatus`)
  is tracked completely independently.
- **Background sync**: `SyncWorker` (Hilt-injected `CoroutineWorker`) runs on a 15-minute
  periodic `WorkManager` job constrained to `NetworkType.CONNECTED`, plus an expedited one-shot
  fired immediately after a sale is recorded, when connectivity returns, or on manual "Sync now".
  It flushes queued sales via the batched `POST /mobile/sync` (≤500/call, per-row results) and
  retries pending customer-registration requests one at a time (no bulk endpoint for those).
- **Blocking rule**: if there's no cached price and the device can't reach the server,
  `SaleFlowViewModel` forces the app into the "Cannot record sales right now" screen and disables
  submission — the app never guesses a price.
- **New-customer registration is queueable offline**, unlike a plain customer-creation call —
  it's a pending-approval request (`POST /mobile/customer-registrations`), not a confirmed sale
  or customer, so there's no duplicate-customer risk in queuing it locally.

## Cashback formula

```
litres      = amountPaid / activePricePerLitre
wholeLitres = floor(litres)
cashback    = wholeLitres × cashbackRate
```

Default `cashbackRate` is KES 2 per whole litre. Worked examples (also asserted in
`CashbackCalculatorTest`): 10.34 L → KES 20, 20.67 L → KES 40. All math uses `BigDecimal`, never
`Double`. This client-side calculation is a **preview only** — the server always recalculates
authoritatively at submit/sync time. Some customers have a chairman-approved special rate
(`CustomerEntity.specialRateKesPerLitre`) — it is read-only everywhere in this app; there is no
UI to set or edit it. Every sale stores its own snapshot of the price-per-litre and cashback rate
used at capture time, so later price changes never rewrite history.

**The calculated cashback amount, and the litres→cashback breakdown, are computed and stored on
every sale but are never shown in the attendant UI** (not on the entry screen, review, success,
or Today's stats) — only the customer sees it, by SMS. An attendant who could read back the exact
cashback figure could coordinate with a customer to game the program; this is why the app also
doesn't surface the "loyalty sales vs. total pump sales" comparison used to enforce the
must-not-exceed invariant (`data/repository/SaleRepository`, sync-time `CONFLICT` status) — an
attendant only ever sees a generic "needs review" state and is told to contact their supervisor,
never the numbers behind it.

## Tests

- `core/cashback/CashbackCalculatorTest` — the two worked examples, litre flooring at a whole-
  litre boundary, special-rate override, zero/negative guards.
- `core/phone/PhoneNumberTest` — normalization of `0722…`/`722…`/`+254722…`/`254722…` forms,
  invalid-length/prefix rejection.
- `data/remote/mock/MockBackendStoreTest` — idempotency-key dedup at the (mock) backend layer.
- `data/local/SaleDaoTest` (instrumented) — idempotency-key dedup at the Room layer.
- `ui/NewSaleFlowTest` (instrumented, Compose) — the full new-sale happy path end-to-end, and
  that a seeded offline sale reads as "Saved on phone" rather than silently vanishing.

Unit tests run with `./gradlew testDebugUnitTest` and need no device. The instrumented tests need
a connected device or emulator (`./gradlew connectedAndroidTest`).

## Notable simplifications (called out, not hidden)

- Fonts: the design system specifies Montserrat/Source Sans 3/IBM Plex Mono via Google Fonts.
  This build uses system font families with matching weights instead, so the project builds
  fully offline with no network font fetch — see `ui/theme/Type.kt` for where to swap in bundled
  font files if pixel-exact typography is required.
- Login is PIN + station-code based against the mock attendant roster in `DemoData` — a real
  deployment would likely use SSO or a backend-issued attendant credential instead.
