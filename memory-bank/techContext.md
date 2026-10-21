# Tech context

## Stack
| Item | Value |
|---|---|
| Package / applicationId | `com.durpalla.booking` |
| minSdk / target / compile | 24 / 35 / 35 |
| Kotlin / AGP / Gradle / JDK | 2.0.21 / 8.7.3 / 8.10.2 / 17 |
| UI | Compose + Material 3, Navigation Compose |
| Net | Retrofit, OkHttp, kotlinx.serialization |
| Session | DataStore + EncryptedSharedPreferences |
| Media / QR | Coil, ZXing |
| Realtime | pusher-java-client |
| Firebase | Messaging, Analytics, Remote Config, Firestore, Auth (BOM) |
| WebRTC | `io.getstream:stream-webrtc-android` |
| Agora Voice | `io.agora.rtc:voice-sdk` 4.6.4 — App ID from API only |
| Versions catalog | `gradle/libs.versions.toml` |

## BuildConfig flags (`app/build.gradle.kts`)
| Field | Notes |
|---|---|
| `BASE_URL` | debug `dev-apigw…/api/v1/`; release `apigw…` |
| `AUTH_BASE_URL` | empty → same as BASE_URL |
| `ASSETS_BASE_URL` | `dev-assets.durpalla.com` |
| `PUSHER_KEY` / `PUSHER_CLUSTER` | in source (TODO: local.properties) |
| `RECAPTCHA_SITE_KEY` / `RECAPTCHA_ORIGIN` | WebView v3; empty token fallback |
| `GHAT_SUGGEST_API_PATH` | `trip/ghat-suggestions` |
| **`USE_MOCK_CALLS`** | debug **true**, release **false** — mock calling until backend live |
| `WEBRTC_*` | ICE from create/accept `iceServers`; null → GET `/calls/ice-servers` / STUN |
| Agora | create/accept `agora:{appId,channel,token,uid,expiresAt}`; renew `POST /calls/{id}/token`; channel `call_{callId}`; omit provider → `webrtc` |

## Config files
- `app/google-services.json` — optional; google-services plugin applied only if present.
- `local.properties` — `sdk.dir` (not committed).
- `app/src/main/res/xml/network_security_config.xml` — pin-set documented; PEM still TODO.
- `app/src/main/res/xml/locales_config.xml` — per-app languages.
- Strings: `res/values` (en), `res/values-bn`.

## Docs
`README.md`, `API_MAP.md`, `MASTER_PORT_TODOS.md`, `docs/calling-contract.md`, `docs/firestore.rules`, `PHOTO_CREDITS.md`.
