# durpalla-customer-kotlin

Durpalla Customer Kotlin app — native Android (Jetpack Compose) rewrite of the Durpalla customer app (Flutter repo `Durpalla/durpalla-flutter-app`) implementing the 2026 Figma redesign.

* Package / applicationId / namespace: **`com.durpalla.booking`** (matches the Flutter app's Firebase registration)
* minSdk 24, targetSdk/compileSdk 35, Kotlin 2.0.21, AGP 8.7.3, Gradle 8.10.2, JDK 17
* Jetpack Compose + Material 3, MVVM (ViewModels + StateFlow), manual DI (`AppContainer`), Retrofit + OkHttp + kotlinx.serialization (`JsonObject`), Coroutines/Flow, Navigation Compose, Coil, DataStore (token + user), ZXing (ticket QR), Pusher Java client, Firebase Messaging/Analytics, AndroidX WebKit.

## Build
```
export JAVA_HOME=/path/to/jdk17
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
```
Debug talks to `https://dev-apigw.durpalla.com/api/v1/`, release to `https://apigw.durpalla.com/api/v1/` (`BuildConfig.BASE_URL`, app/build.gradle.kts).

Copy `google-services.json` and API/Pusher/reCAPTCHA keys from the Flutter app (see Config below). The google-services plugin is applied **only if** `app/google-services.json` exists, so the project still compiles without it.

## Config ported from the Flutter repo
| Item | Source in Flutter repo | Here |
|---|---|---|
| API base | `.env` API_BASE_URL | `BuildConfig.BASE_URL` |
| Pusher key/cluster | `.env` PUSHER_KEY / PUSHER_CLUSTER | `BuildConfig.PUSHER_KEY/PUSHER_CLUSTER` |
| reCAPTCHA v3 site key | `.env` RECAPTCHA_SITE_KEY | `BuildConfig.RECAPTCHA_SITE_KEY` (+ `RECAPTCHA_ORIGIN`) |
| Firebase | `android/app/google-services.json` (package com.durpalla.booking) | `app/google-services.json` |

### How the integrations are wired
* **Pusher** (`realtime/PusherManager.kt`): connects on app start; Trip details subscribes to `trip.{id}` and reacts to `cart.item.locked/booked/released` (live seat greying + snackbar, same messages as Flutter). Socket id is sent as `X-Socket-Id` on `cart/add`.
* **FCM** (`push/DurpallaMessagingService.kt`, `push/Notifications.kt`, `DurpallaApp.kt`): token stored as `device_id`, sent as `device_id`/`fcm_token` on login/register and bound via `POST auth/push/bind` (on start, after login, on token refresh). Channels `trip_reminder_channel_v2` ("Trip Reminders") and `default_channel` ("General Notifications"); `data.is_reminder=="true"` → reminder channel, default titles/bodies identical to Flutter. Optional `data.route` opens that in-app route (Flutter has no deep links; this is an addition). No topics (Flutter uses none). Android 13 notification permission requested on launch. The Flutter custom sound `res/raw/event_reminder_voice.mp3` is not bundled (binary couldn't be pulled) — drop it into `app/src/main/res/raw/` and it's picked up automatically.
* **reCAPTCHA** (`security/RecaptchaProvider.kt`): Flutter uses `g_recaptcha_v3` (web-only plugin, so on Android it actually sends an empty token). Here the same v3 site key is executed in a hidden WebView on `RECAPTCHA_ORIGIN` (action `login`) and sent as `recaptchaToken` on auth/check, otp/send, verify, login, register, reset; on failure/timeout (8 s) it sends `""` like Flutter. If the key's domain list doesn't include `durpalla.com`, change `RECAPTCHA_ORIGIN` or switch to the native Android reCAPTCHA SDK with an Android key.

## Architecture
```
core/            Json helpers (loose Map-like access), ApiException
data/net/        DurpallaApi (Retrofit, raw bodies) + ApiClient (headers, _decode semantics, 401 → logout)
data/session/    SessionStore (DataStore: token, auth_user, device_id, lang, dark)
data/repo/       Auth, Home, Trip, Booking, Payment, Hotel, Profile repositories (real API), ExperienceRepository (+Mock)
realtime/ push/ security/
ui/theme         Brand palette (#106E9D, 50/100/300/600/700, navy #0A3550, amber #F59E0B), Inter variable font, light/dark
ui/components    SoftCard (blue-tinted shadow), GradientButton (amber glow), FrostedIcon, Pill, Skeleton shimmer, EmptyState…
ui/nav           Routes, AppNavigator (auth guard), bottom nav (5 tabs, saveState/restoreState), Sign-in-required sheet
ui/screens       All screens
```

See [API_MAP.md](API_MAP.md) for the Flutter → Kotlin endpoint map.

### Guest mode / auth guard
Splash → Guest Home. Search, results, trip details/seat map, hotels, tours/events, offers are public. `Routes.isProtected` covers Tickets tab, checkout, payment, payment WebView/result, ticket, profile, saved, notifications and experience booking; in-page actions (hotel Book now, favourite) call `ensureAuth`.

Auth matches Flutter **dev/master** email+mobile (`channel` + `email`|`mobile`):
- **Mobile | Email** segmented tabs (explicit; switching clears the field — no auto-detect).
- `auth/check` → login (password + Use OTP / Forgot) | otp (manual Send OTP, 60s resend, exactly 6 digits) | register (may skip OTP).
- `use_email: true` switches to the Email tab with the international-SMS warning.
- Register: name required; password ≥ 8 + confirm; per-channel mobile/email rules (see API_MAP / Flutter E4).
- Forgot/reset: **BD mobile only** (no email).
- Country picker defaults to +880 and helps compose the `mobile` string (BD `01XXXXXXXXX`, intl as typed `^\+?[1-9]\d{7,14}$`). No `country_code`/`dial_code` fields.

### Screens
Splash · Home (guest & signed-in; 6-service grid, upcoming ticket, offers carousel, popular routes, top hotels, explore) · Search (Launch/Bus/Boat/Hotel, ghat & city autocomplete, swap, Today/Tomorrow/date picker) · Results (date strip, sort/AC filter, seat-availability bars, skeletons) · Trip details (cabin/seat/sofa tabs, floors, legend, live Pusher locking, sticky summary) · Checkout (stepper, passenger, price breakdown, release item) · Payment methods · Payment WebView · Payment result (verify, confetti) · Tickets (Upcoming/Past/Cancelled) · Ticket (QR, cancel item) · Offers · Account (guest & signed-in) · Edit profile · Saved hotels · Settings (dark mode, English/বাংলা) · Support · FAQ · About · Terms · Notifications · Sign in · Password · OTP · Register · Hotel results · Hotel details (rooms → quote → hold → confirm → payment) · Tour/Entertainment list · Experience details · Experience booking (mock).

## Real vs mock
* **Real:** everything in API_MAP.md (auth, search, suggestions, trip layout, cart lock/unlock, booking confirm, gateways, payment make/verify, bookings, cancel, profile, hotels, favourites, home feeds).
* **Mock / TODO:** Tour & Entertainment (`MockExperienceRepository`, labelled in UI), Notifications inbox, Terms CMS page, coupons, avatar upload UI.

## Known gaps
* Runtime not exercised on a device/emulator (built only). Search/popular endpoints returned empty lists on dev, so result cards and seat map are coded against the Flutter field names, not live data.
* Response field names for hotel rooms/hold/confirm and booking-details wrappers are inferred from the Flutter code with fallbacks; verify against real responses.
* Bus "ladies seat" and per-seat passenger forms aren't modelled (Flutter doesn't either).
* Cart is in memory (like Flutter CartProvider); held locks are released when items are removed but not on app kill.
Photo credits: [PHOTO_CREDITS.md](PHOTO_CREDITS.md) (Wikimedia Commons). Inter font: SIL OFL.
