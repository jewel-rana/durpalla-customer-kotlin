# Progress

Target: Flutter master `@8ee2bed`. Tracker detail: `MASTER_PORT_TODOS.md`.

## Done
- P0 HTTP: `X-Guest-Id`, language headers, assets rewrite, `my/bookings/android`, cart sync (charge/VAT/expiry), hotel multi-room, tour/entertainment/boat APIs, cancel rules, email+mobile auth.
- P1 partial: ApiUserMessage, secure token, payment WebView hints, RC gateway filter, invoice link, `hotel_review_prompt`, `app-config?platform=android`.
- L10n §10.2/§10.3: remote packs, language onboarding, settings picker, RTL for ar/ur/fa, bundled en/bn.
- Hard-coded English audit: **173 found → 173 fixed** (0 intended leftovers for migration). Left intentionally: routes, API keys/paths, logs, place names, wire protocol strings, emails, date patterns.
- Calling: WebRTC + Firestore contract copy; weak-network Opus/bitrate/ICE; driver PR #6 parity (firebase-token, ICE shapes/ttl/2, caller-only restart, `/end` reasons, CallApiErrors EN/BN, trip `callLog[]`, Pusher private user channel, FCM `event`).
- **Agora transport** (driver §0 / apigw): token wrapped in `data`, `expiresAt` seconds, caller join after POST / callee after accept, accept/decline/end `provider`, `CALL_PROVIDER_*` errors, CHATROOM + weak≥4/rtc stats; **client omit → webrtc**; channel `call_{callId}`; `callLog.provider`.
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
- Agora contract **confirmed** (driver §0); client missing-`provider` → **webrtc**.
