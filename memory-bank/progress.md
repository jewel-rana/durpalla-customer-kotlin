# Progress

Target: Flutter master `@8ee2bed`. Tracker detail: `MASTER_PORT_TODOS.md`.

## Done
- **Web palette + hero slideshow:** `Brand` tokens remapped (teal `#006D9C`, navy `#06263A`, amber text `#B45309`). Service-tile and payment brand colors unchanged. `HeroBackgroundSlideshow` (bus→launch→hotel→tour→boat, 600ms / 3s, splash vs home overlay). Photos ≤300KB in `drawable-nodpi`. Sources: launch [Sadarghat Launch Terminal](https://commons.wikimedia.org/wiki/File:Sadarghat_Launch_Terminal.jpg) CC BY-SA 4.0; hotel [Grand Hotel Campo dei Fiori bedroom](https://commons.wikimedia.org/wiki/File:A_bedroom_of_the_Grand_Hotel_Campo_dei_Fiori.jpg) CC BY-SA 4.0; tour [Sunset at Sajek Valley](https://commons.wikimedia.org/wiki/File:Sunset_at_Sajek_Valley.jpg) CC BY-SA 4.0; boat [Fishing trawler, Cox's Bazar](https://commons.wikimedia.org/wiki/File:Fishing_trawler_anchored_at_Cox%27s_Bazar_beach,_Bangladesh.jpg) CC BY-SA 4.0. Bus from approved upload.
- **Debug APK builds** (`./gradlew assembleDebug`): compile fixes landed; debug NDK `abiFilters` = `arm64-v8a` (artifact size). APK: `/opt/cursor/artifacts/durpalla-customer-debug.apk`.
- **Device debug scripts:** `./dev.sh` / `./reload.sh` (+ `scripts/dev_common.sh`) — build/install/launch/logcat on connected adb devices.
- P0 HTTP: `X-Guest-Id`, language headers, assets rewrite, `my/bookings/android`, cart sync (charge/VAT/expiry), hotel multi-room, tour/entertainment/boat APIs, cancel rules, email+mobile auth.
- P1 partial: ApiUserMessage, secure token, payment WebView hints, RC gateway filter, invoice link, `hotel_review_prompt`, `app-config?platform=android`.
- L10n §10.2/§10.3: remote packs, language onboarding, settings picker, RTL for ar/ur/fa, bundled en/bn.
- Hard-coded English audit: **173 found → 173 fixed** (0 intended leftovers for migration). Left intentionally: routes, API keys/paths, logs, place names, wire protocol strings, emails, date patterns.
- Calling: WebRTC + Firestore contract copy; weak-network Opus/bitrate/ICE; driver PR #6 parity (firebase-token, ICE shapes/ttl/2, caller-only restart, `/end` reasons, CallApiErrors EN/BN, trip `callLog[]`, Pusher private user channel, FCM `event`).
- **Agora / WebRTC media bootstrap** (Jewel contract): create + accept return `{callId, provider, agora|null, iceServers|null, ttl?}`; no GET agora-token; renew via `POST /calls/{id}/token`; **client omit → webrtc**; channel `call_{callId}`; `callLog.provider`; CHATROOM + weak≥4/rtc stats; `CALL_PROVIDER_*` EN/BN.
- Memory bank + agent rules (`token-saving`, `memory-bank`).

## Partial / TODO (see MASTER_PORT_TODOS)
| Item | Status |
|---|---|
| API cert pin PEM | TODO |
| SolaimanLipi Bangla font | TODO |
| Full ARB → strings (~512) | Partial |
| Brand / home hero assets | TODO |
| Secrets → local.properties | TODO |
| Firebase App Check + Crashlytics | TODO |
| Home redesign (~2650 lines) | Partial |
| `event_reminder_voice.mp3` | TODO |
| Live calling (`USE_MOCK_CALLS=false` + backend) | Blocked on env |

## Known contract deltas (calling)
- Wire `tripId` string vs local `Int` (POST `toString()`; push parse fallback).
- FCM prefers `event`, still accepts `type`.
- Pusher auth URL = customer `{BASE_URL}realtime/auth`.
- Calling media bootstrap on create/accept; client missing-`provider` → **webrtc**; renew via `POST …/token`.
