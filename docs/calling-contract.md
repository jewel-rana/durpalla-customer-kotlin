# In-app calling contract (driver ⇄ customer)

Audio-only calls between the driver and the customer of a trip. The server selects the **media transport** (`webrtc` or `agora`). **Firebase Firestore** carries the shared call document (status, parties, WebRTC SDP/ICE when applicable). Ringing (FCM / CallStyle), UI, call log, and status flow are the same for both transports.

> **Confirmed** by the driver team (including Agora). At launch the server default `provider` is **`agora`**. Clients treat a missing `provider` as `agora`.

- Android WebRTC: `io.getstream:stream-webrtc-android` (`org.webrtc`).
- Android Agora: `io.agora.rtc:voice-sdk` (Voice SDK; appId from the token endpoint — never hard-coded).
- Driver code: `app/src/main/java/com/durpalla/driver/call/`.
- Customer code: `app/src/main/java/com/durpalla/booking/calling/` (`CallTransport`: `WebRtcTransport` | `AgoraTransport`).

## 1. REST endpoints

- Base URL: `API_BASE_URL`.
- Every request sends `Authorization: Bearer <access token>`.
- Request and response bodies are JSON.

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/calls` | `{ "tripId": "t_123" }` | `201 { "callId": "c_456", "provider": "webrtc" \| "agora" }` (`provider` optional → default `"agora"` at launch) |
| POST | `/calls/{callId}/accept` | `{}` | `200 {}` |
| POST | `/calls/{callId}/decline` | `{}` | `200 {}` |
| POST | `/calls/{callId}/end` | `{ "reason": "hangup" \| "cancelled" \| "missed" \| "failed" }` | `200 {}` |
| GET | `/calls/ice-servers` | – | `200 [ { "urls": ["turn:…"], "username": "…", "credential": "…" } ]` (or `{ "iceServers": [...], "ttl": 3600 }`) — **WebRTC only** |
| GET | `/calls/{callId}/agora-token` | – | `200 { "appId", "channel", "token", "uid", "expiresAt" }` — **Agora only** (`channel` is normally `call_{callId}`) |
| POST | `/devices/voip` | `{ "voipToken": "…", "platform": "ios", "userId": "…" }` | `200 {}` (iOS PushKit only) |
| GET | `/auth/firebase-token` | – | `200 { "token": "<Firebase custom token>" }` (uid = Durpalla userId) |

### What the endpoints do

- The `callId` is the handle for status/REST. WebRTC uses Firestore SDP/ICE; Agora uses the Voice SDK channel from `agora-token`.
- **POST /calls** checks that the caller is a party to the trip and that the trip is calling-eligible (`UPCOMING`, `ON_THE_WAY`, `ARRIVED` or `IN_PROGRESS`). It then:
  - creates the Firestore doc `calls/{callId}` with `status: "ringing"` and `provider` (default **`agora`** at launch);
  - rings the callee (FCM includes the same `provider`);
  - starts a server-side **30 s missed timeout**. If the doc is still `ringing` at 30 s, the server sets `status: "missed"`, `reason: "no_answer"` and `endedAt`, and sends `call.missed` to both parties.
- **accept** sets `status: "accepted"` and `answeredAt`.
- **decline** sets `status: "declined"`, `endedAt`, `endedBy` and `reason: "declined"`.
- **end** sets `status: "ended"` (or `"missed"` when `reason` is `missed`), plus `endedAt`, `endedBy` and `reason`.
- The server writes the call-log entry on the trip (section 6), including `provider`.
- **ice-servers** (WebRTC):
  - TURN credentials are short-lived. `ttl` is in seconds; clients cache for at most `ttl/2`.
  - `urls` may be a string or an array.
  - If the call fails or returns an empty list, clients fall back to `stun:stun.l.google.com:19302`.
- **agora-token:** short-lived Agora RTC token + `appId` / `channel` / `uid`. Channel name is `call_{callId}`; clients use the server `channel` when present and non-blank, otherwise fall back to `call_{callId}`. Clients renew via the same endpoint on `onTokenPrivilegeWillExpire`.

### Errors

Error responses have the shape `{ "error": { "code": "CALL_BUSY", "message": "…" } }`.

| HTTP | code | Meaning |
|---|---|---|
| 401 | `UNAUTHORIZED` | Bad or expired bearer token |
| 404 | `CALL_TRIP_NOT_FOUND` | Unknown trip |
| 403 | `CALL_NOT_PARTICIPANT` | Caller is not the trip's driver or customer |
| 422 | `CALL_TRIP_NOT_ACTIVE` | Trip is completed or cancelled |
| 409 | `CALL_BUSY` | Callee already has a ringing or accepted call |
| 404 | `CALL_NOT_FOUND` | Unknown callId |
| 409 | `CALL_ALREADY_ENDED` | accept, decline or end on a terminal call |
| 429 | `RATE_LIMITED` | Too many call attempts |

## 2. Ringing the callee

- **Primary path (Android and iOS):** a high-priority FCM **data** message, `priority: high`, with no `notification` block:
  ```json
  { "event": "call.incoming", "callId": "c_456", "tripId": "t_123", "callerName": "Rahim", "provider": "agora" }
  ```
  - `provider` is `"webrtc"` or `"agora"` (omit → `"agora"` at launch).
  - The fields may also be sent as a JSON string in `payload`.
  - Android shows a full-screen `CallStyle.forIncomingCall` notification that rings for up to 30 s.
- **iOS:** the server also sends a **PushKit VoIP** push, which is reported to CallKit, to the token registered with `POST /devices/voip {voipToken, platform:"ios", userId}`.
- **Termination events:** FCM data `call.ended`, `call.declined` and `call.missed` `{ callId, status }` stop the ringing on the other device.
- **Pusher** (`private-user-{userId}`, the same event names) is only a secondary, in-app path.

## 3. Firestore signaling: `calls/{callId}`

```
calls/{callId} {
  callId:      string
  tripId:      string
  callerId:    string           // Durpalla userId (= Firebase auth uid)
  callerRole:  "driver" | "customer"
  calleeId:    string
  calleeRole:  "driver" | "customer"
  provider:    "webrtc" | "agora"   // server-set; omit → agora (launch default)
  offer:       { type: "offer",  sdp: string }    // WebRTC only — written by the caller
  answer:      { type: "answer", sdp: string }    // WebRTC only — written by the callee
  status:      "ringing" | "accepted" | "declined" | "ended" | "missed"
  createdAt:   timestamp        // server
  answeredAt:  timestamp | null
  endedAt:     timestamp | null
  endedBy:     string | null    // userId
  reason:      string | null    // hangup | cancelled | declined | no_answer | missed | failed | busy
}
calls/{callId}/callerCandidates/{auto} { candidate: string, sdpMid: string, sdpMLineIndex: number }  // WebRTC
calls/{callId}/calleeCandidates/{auto} { candidate: string, sdpMid: string, sdpMLineIndex: number }  // WebRTC
```

### Call flow — shared

1. Caller `POST /calls` → `{ callId, provider }`. Listen to the Firestore doc for status.
2. Callee rings on `call.incoming` (same FCM/CallStyle / Pusher path for both providers).
3. Accept / decline / end use the same REST endpoints; terminal `status` tears down UI and media.
4. **Busy** and **30 s missed** behaviour are unchanged (section below).

### Call flow — WebRTC (`provider: "webrtc"`)

1. **Caller:** `GET /calls/ice-servers` → PeerConnection (unified plan, audio) → `createOffer` + SDP munging (section 7) → write `offer` → `callerCandidates`; listen to doc + `calleeCandidates`.
2. **Callee (on Accept):** `POST accept` → read `offer` → `createAnswer` (munged) → write `answer` → `calleeCandidates`.
3. Remote candidates arriving before the remote description are queued.
4. UI timer when ICE is `CONNECTED`.
5. **ICE restart (caller only)** on network change / ICE `DISCONNECTED`: `restartIce()` + new offer; callee answers each new offer.

### Call flow — Agora (`provider: "agora"`)

1. **Caller** after POST /calls (and **callee** on Accept): `GET /calls/{callId}/agora-token` → `{ appId, channel, token, uid, expiresAt }`.
2. Join channel = server `channel` if non-blank, else `call_{callId}`. Audio-only Voice SDK: `CHANNEL_PROFILE_COMMUNICATION`, `AUDIO_PROFILE_SPEECH_STANDARD`, publish mic / subscribe audio.
3. On `onTokenPrivilegeWillExpire` / `onRequestToken`, fetch a new token from the same endpoint and `renewToken`.
4. Map local `onNetworkQuality` (tx/rx ≥ poor) to the same **"Weak connection"** UI hint as WebRTC.
5. On end / terminal status: `leaveChannel` and destroy the engine. No Firestore SDP/ICE writes.

### Busy / timeout

7. **Busy:** a client that is already in a call declines a new `call.incoming` automatically (the server returns `CALL_BUSY` to new callers).
8. **Client 30 s timeout:** clients also stop ringing after 30 s. The caller sends `end {reason:"missed"}`. The server timeout is authoritative.

## 4. Security rules

- Only `callerId` or `calleeId` may read or write `calls/{callId}` and its subcollections.
- Clients sign in with the backend-issued Firebase custom token, so that `request.auth.uid` equals the Durpalla userId.
- The backend creates the doc with the Admin SDK.
- Parties may update only `offer`, `answer`, `status`, `answeredAt`, `endedAt`, `endedBy` and `reason`.
- Each party may create candidates only in its own subcollection.
- Sample rules: [`docs/firestore.rules`](firestore.rules).

## 5. Client behaviour (driver app)

- **CallProvider interface:**
  - `WebRtcCallProvider` is used when Firebase is configured (`google-services.json` present).
  - Otherwise `FakeCallProvider` is used, an offline simulator: an outgoing call connects after 2 s, and an incoming call times out after 30 s.
- **Permissions:**
  - `RECORD_AUDIO` is requested at the first call or accept. If denied, a toast is shown and no call is placed.
  - `MODIFY_AUDIO_SETTINGS` and `POST_NOTIFICATIONS`.
  - `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE`.
- **During the call:**
  - Foreground `CallService` (`foregroundServiceType="microphone"`) with a `CallStyle.forOngoingCall` notification (chronometer and hang up).
  - Incoming calls use `CallStyle.forIncomingCall` on the high-importance `calls_incoming` channel, with the ringtone and a full-screen intent.
- **UI:**
  - A full-screen call overlay with Accept, Decline, Mute, Speaker and End, plus a "Weak connection" hint.
  - The Call button is on Trip detail and Live trip.
  - "Calls on this trip" appears on Trip detail.
  - All strings are in English and Bangla (`values-bn`); durations use Bangla digits in Bangla.

## 6. Call log on the trip

The trip payload carries `callLog`, oldest first:

```json
"callLog": [
  { "callId": "c_456", "callerRole": "driver", "status": "ended", "provider": "agora",
    "createdAt": 1760960000000, "answeredAt": 1760960004000, "endedAt": 1760960070000,
    "durationSec": 66, "endedBy": "u_customer", "reason": "hangup" }
]
```

- `status` takes the same values as the Firestore doc.
- `provider` is `"webrtc"` or `"agora"` (omit → `"agora"`).
- `durationSec` is `endedAt - answeredAt`, or 0 if the call was never answered.

## 7. Audio quality

Both apps **must** apply the same settings.

- **Codec:** Opus only, mono.
  - After `createOffer` and `createAnswer`, the SDP is munged before `setLocalDescription`:
    - the `m=audio` line keeps only the Opus payload type;
    - every `a=rtpmap`, `a=fmtp` and `a=rtcp-fb` line for other payload types is removed (red, G722, PCMU, PCMA, CN, telephone-event);
    - `a=maxptime` is dropped;
    - `a=ptime:20` is set.
  - The Opus fmtp is replaced (or added) as exactly:
    ```
    a=fmtp:<opus-pt> useinbandfec=1;usedtx=1;stereo=0;maxaveragebitrate=24000;maxplaybackrate=16000;ptime=20
    ```
  - Non-audio sections are not touched. Munging is idempotent.
- **Sender cap:** `RtpSender.parameters.encodings[*].maxBitrateBps` is set to the current level (24 000 bps at start).
- **Adaptive bitrate (`BitrateController`):**
  - Every **2 s**, read `getStats`:
    - `remote-inbound-rtp` (kind audio): `fractionLost`, and `roundTripTime` in seconds × 1000;
    - fallback RTT from the nominated `candidate-pair`'s `currentRoundTripTime`.
  - Levels are **24 000 → 12 000 → 8 000 bps**.
  - **Step down** one level per bad sample: loss **> 5 %** or RTT **> 400 ms**.
  - **Step up** one level after **3 consecutive good samples**: loss < 2 % and RTT < 250 ms. A sample between good and bad resets the streak.
  - Below 24 kbps, the UI shows **"Weak connection" / "দুর্বল সংযোগ"**.
- **Audio processing:**
  - Hardware AEC and NS are used when the device has them (`JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()` and `isBuiltInNoiseSuppressorSupported()`).
  - The WebRTC software APM is always requested as the fallback: `googEchoCancellation`, `googNoiseSuppression`, `googAutoGainControl`, `googHighpassFilter`, all `true`.
  - Mono input and output.
- **Audio routing:** `AudioManager.mode = MODE_IN_COMMUNICATION` for the duration of the call, restored to `MODE_NORMAL` at teardown. Speaker is off by default.
- **Network changes:** on a change of the default network, or on ICE `DISCONNECTED`, the caller does an ICE restart (section 3, step 6).
- **iOS:** use the same SDP munging, the same `maxBitrate`, the same controller thresholds, and `AVAudioSession` `.playAndRecord` / `.voiceChat`.
