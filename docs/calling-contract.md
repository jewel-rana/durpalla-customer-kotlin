# Calling contract (customer ↔ driver)

**Source of truth:** `Durpalla/durpalla-driver` → `docs/calling-contract.md`.  
Customer app mirrors these names in mock + interfaces.

## Media & setup

- Audio-only **WebRTC** (not Agora).
- Caller creates the call via HTTP; callee is rung with **high-priority FCM** first.
- **Pusher** is secondary only (`private-customer.{id}` / `private-trip.{tripId}` via `POST /realtime/auth`).
- Signaling (SDP + ICE + status) on Firestore. **Only caller/callee** may read/write the call doc.

## HTTP

| Method | Path | Body / response |
|---|---|---|
| POST | `/calls` | `{ "tripId": <int> }` → `{ "callId": <string> }` |
| POST | `/calls/{id}/accept` | — |
| POST | `/calls/{id}/decline` | `{ "reason": <string> }` |
| POST | `/calls/{id}/end` | — |
| GET | `/calls/ice-servers` | `[{ "urls", "username", "credential" }]` + `ttl`; on failure use `stun:stun.l.google.com:19302` |
| POST | `/devices/voip` | `{ voipToken, platform: 'ios', userId }` — **iOS only** (N/A on Android) |

## FCM (primary ring)

High-priority **data** message:

```
type: call.incoming
callId: <string>
tripId: <string|int>
callerName: <string>
```

## Firestore `calls/{callId}`

| Field | Type / values |
|---|---|
| `callId` | string |
| `tripId` | int |
| `callerId` | string |
| `callerRole` | e.g. `customer` / `driver` |
| `calleeId` | string |
| `calleeRole` | e.g. `driver` / `customer` |
| `offer` | `{ type, sdp }` |
| `answer` | `{ type, sdp }` |
| `status` | **`ringing` \| `accepted` \| `declined` \| `ended` \| `missed`** |
| `createdAt` | timestamp |
| `answeredAt` | timestamp |
| `endedAt` | timestamp |
| `endedBy` | string (user id or `system`) |
| `reason` | string (e.g. decline / timeout) |

### Subcollections

- `callerCandidates/{id}` → `{ candidate, sdpMid, sdpMLineIndex }`
- `calleeCandidates/{id}` → `{ candidate, sdpMid, sdpMLineIndex }`

## Behaviour

- Unanswered for **30s** → `status=missed`.
- Customer UI: Call driver on accepted boat bid, active trip, booking/trip details.
- Android: CallStyle (12+) + full-screen intent, mic FG service, mute/speaker/end, missed notification + trip call log.
- BuildConfig `USE_MOCK_CALLS` selects `MockCallRepository` (fake callId, simulated status machine).

## Weak-network audio (customer ↔ driver parity)

Must match the driver app exactly:

| Knob | Value |
|---|---|
| Codec | **Opus only**, mono (`stereo=0`) |
| SDP `a=fmtp` (Opus) | `useinbandfec=1;usedtx=1;stereo=0;maxaveragebitrate=24000;maxplaybackrate=16000;ptime=20` |
| Sender `maxBitrate` | **24 kbps** initially |
| Adaptive bitrate | Poll `getStats` every **2s**. If packet loss **>5%** or RTT **>400ms**, step down **24 → 12 → 8 kbps**; step back up on recovery. |
| UI | Localized **Weak connection** hint while below 24 kbps |
| Audio processing | Keep **AEC / NS / AGC** on |
| AudioManager | `MODE_IN_COMMUNICATION` for the call |
| Network change | `ConnectivityManager` callback → **ICE restart** (+ renegotiate offer/answer on Firestore) |

Customer implementation: `OpusSdpMunger`, `WebRtcEngine` (JavaAudioDeviceModule + adaptive sender bitrate + network ICE restart).
