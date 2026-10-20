# System patterns

## Layers
```
ui/screens + ui/components     Compose UI
ui/nav (Routes, AppNavigator)  Navigation + auth guard
AppContainer                   Manual DI
data/repo + data/session       Repositories / session
data/net (ApiClient)           HTTP
calling / realtime / push / l10n / security / core
```

## MVVM
- Screens use `viewModel` / `rememberLoad` (`ui/screens/Common.kt` → `LoadVm` + `Load.Loading|Ok|Err`).
- Trip details has a dedicated `TripDetailsVm`; most screens collect repo `StateFlow`s or one-shot `LoadVm`.
- Repos expose `suspend` APIs and/or `StateFlow` (session, cart, calls).

## DI
- `DurpallaApp` → `AppContainer` (single instance).
- Compose: `LocalContainer` / `LocalNav` composition locals (`ui/nav/AppNav.kt`).
- No Hilt/Koin.

## Networking
- `ApiClient` + Retrofit `DurpallaApi`: loose `JsonObject` bodies (`core/Json.kt`: `s/i/d/a/o/str/objects`).
- Headers: Bearer, `X-Guest-Id`, Accept/Content-Language; 401 → forced logout.
- Debug: rewrite assets host via `MediaUrl` / `ASSETS_BASE_URL`.
- Endpoint map: `API_MAP.md`.

## Navigation
- `Routes` string routes; `AppNavigator.go` / `ensureAuth` / `back`.
- Bottom nav: Home, Search, Tickets, Offers, Account (save/restore state).
- Protected prefixes in `Routes.isProtected`.

## Theming / UI reuse
- `ui/theme/Theme.kt` → `Brand`, `DurpallaTheme`.
- Prefer existing: `SoftCard`, `GradientButton`, `PrimaryButton`, `DTopBar`, `Pill`, `SectionHeader`, `PhotoBox`, `Skeleton`, `LoadingList`, `EmptyState`, `ErrorState`, `money()`, `LogoHorizontal`, `PhoneInput`.
- Strings: `t(R.string…)` in Compose; `AppCtx.str` outside Compose (repos, FCM, services).

## Calling
- `CallRepository` → `MockCallRepository` or `ApiCallRepository` (`USE_MOCK_CALLS`).
- **Transport:** `CallTransport` — `WebRtcTransport` | `AgoraTransport` | `MockCallTransport`; server sends `provider` (launch default agora); **client omit → webrtc**.
- Shared: FCM/CallStyle ringing, UI, call log (+ `provider`), Firestore status doc. WebRTC-only: SDP/ICE. Agora-only: `agora-token` join (`call_{callId}` channel fallback) / leave.
- Firebase custom token before Firestore; ICE ttl/2 cache; caller-only ICE restart (WebRTC).
- Contract + rules: `docs/calling-contract.md`, `docs/firestore.rules`.
