# In-app calling contract (driver ⇄ customer)

Audio-only calls between the driver and the customer of a trip. The **server picks the media provider per call**: **Agora** (launch default) or **WebRTC** (section 0). **Firebase Firestore** carries call status for both, and SDP/ICE signaling for WebRTC. This contract is shared with the customer apps (Android and iOS), so field names, statuses and audio settings must match exactly.

> **Confirmed** with Durpalla/durpalla-driver `docs/calling-contract.md` §0 and apigw.

- Android WebRTC: `io.getstream:stream-webrtc-android` (`org.webrtc`).
- Android Agora: `io.agora.rtc:voice-sdk` 4.6.x (App ID from the token endpoint — never hard-coded).
- Driver code: `app/src/main/java/com/durpalla/driver/call/`.
- Customer code: `app/src/main/java/com/durpalla/booking/calling/` (`CallTransport`: `WebRtcTransport` | `AgoraTransport`).

## 0. Provider (Agora / WebRTC)

- `provider` is `"agora"` or `"webrtc"`, chosen by the server when the call is created.
- It is carried by: the `POST /calls` response, the FCM `call.incoming` data (which has both `event` and `type`), every Pusher `call.*` event, the Firestore doc `calls/{callId}`, the **accept/decline/end responses**, and every `callLog` entry.
- **Missing or unknown `provider` means `webrtc`.** Clients never assume agora. Both sides use the provider stored on the call.
- Shared for both providers: ringing (FCM/Pusher/CallStyle), Firestore `status`, REST accept/decline/end, 30 s missed timeout, and `callLog`.
- **agora:** no `offer`/`answer`/candidates. Each party calls `GET /calls/{callId}/agora-token` — **caller right after `POST /calls`**, **callee after accept** — and joins channel `call_{callId}`:
  - audio only, `CHANNEL_PROFILE_COMMUNICATION`, role broadcaster, `AUDIO_PROFILE_SPEECH_STANDARD`, `AUDIO_SCENARIO_CHATROOM`;
  - active when the remote uid joins (`onUserJoined`);
  - renew on `onTokenPrivilegeWillExpire` via the same endpoint + `renewToken`;
  - `leaveChannel` on end, decline or failure;
  - weak-network hint from `onNetworkQuality` (local uid 0, tx/rx ≥ 4 = bad) and `onRtcStats` (loss ≥ 5 % or lastmile RTT ≥ 400 ms); clears after 3 good samples;
  - App ID only from the token response.
- Server selection (apigw): overrides → webrtc percent → `CALL_PROVIDER_DEFAULT=agora`. While Agora isn't configured the server picks webrtc.

## 1. REST endpoints

- Base URL: `API_BASE_URL`.
- Every request sends `Authorization: Bearer <access token>`.
- Request and response bodies are JSON. Customer `ApiClient` may surface bodies under `data`.

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/calls` | `{ "tripId": "123" }` | `201 { "callId": "456", "provider": "agora" }` (omit/`provider` unknown → client uses **webrtc**) |
| GET | `/calls/{callId}/agora-token` | – | `200 { "data": { "appId", "channel": "call_456", "token", "uid", "expiresAt" } }` — agora only; `expiresAt` epoch **seconds**; `uid` = customer id (drivers `2000000000+id`) |
| POST | `/calls/{callId}/accept` | `{}` | `200 { "provider": "agora" \| "webrtc" }` (use `provider`) |
| POST | `/calls/{callId}/decline` | `{}` | `200 { "provider": "…" }` (use `provider`) |
| POST | `/calls/{callId}/end` | `{ "reason": "hangup" \| "cancelled" \| "missed" \| "failed" }` | `200 { "provider": "…" }` (use `provider`) |
| GET | `/calls/ice-servers` | – | `200 [ { "urls": ["turn:…"], "username": "…", "credential": "…" } ]` (or `{ "iceServers": [...], "ttl": 3600 }`) — **WebRTC only** |
| POST | `/devices/voip` | `{ "voipToken": "…", "platform": "ios", "userId": "…" }` | `200 {}` (iOS PushKit only) |
| GET | `/auth/firebase-token` | – | `200 { "token": "<Firebase custom token>" }` (uid = Durpalla userId) |

### What the endpoints do

- The `callId` is the call's handle. For agora calls the channel is always `call_{callId}` (use server `channel` when non-blank, else that fallback).
- **agora-token:** RTC AccessToken2; `uid` stable per user (customer = customer id). Parties only.
- **POST /calls** checks party + trip eligibility, creates Firestore doc `status: "ringing"`, rings callee, starts 30 s missed timeout.
- **accept** / **decline** / **end** update Firestore status fields; responses include `provider` — clients apply it.
- The server writes the call-log entry on the trip (section 6), including `provider`.
- **ice-servers** (WebRTC): cache at most `ttl/2`; STUN fallback if empty/fail.

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
| 409 | `CALL_PROVIDER_MISMATCH` | agora-token on a webrtc call |
| 503 | `CALL_PROVIDER_UNAVAILABLE` | Agora isn't configured on the server |
| 429 | `RATE_LIMITED` | Too many call attempts |

## 2. Ringing the callee

- **Primary path (Android and iOS):** a high-priority FCM **data** message, `priority: high`, with no `notification` block:
  ```json
  { "event": "call.incoming", "type": "call.incoming", "callId": "456", "tripId": "123", "callerName": "Rahim", "provider": "agora" }
  ```
  - `provider` is `"webrtc"` or `"agora"`. **Client omit / unknown → `"webrtc"`** (never assume agora).
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
  provider:    "agora" | "webrtc"   // server-set; missing = webrtc. agora: offer/answer/candidates stay empty
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

1. **Caller** right after POST /calls: `GET /calls/{callId}/agora-token` → `{ data: { appId, channel, token, uid, expiresAt } }` (`expiresAt` epoch seconds) and join.
2. **Callee** after accept: same token fetch + join. Channel = server `channel` if non-blank, else `call_{callId}`.
3. Voice SDK: `CHANNEL_PROFILE_COMMUNICATION`, broadcaster, `AUDIO_PROFILE_SPEECH_STANDARD`, `AUDIO_SCENARIO_CHATROOM`.
4. Connected on `onUserJoined`. Renew on `onTokenPrivilegeWillExpire`. Weak hint: quality ≥ 4 or rtc stats (loss ≥ 5% / lastmile RTT ≥ 400ms); clear after 3 good samples.
5. On end / decline / failure: `leaveChannel`. No Firestore SDP/ICE writes.

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
- `provider` is `"agora"` or `"webrtc"` (**client omit → `"webrtc"`**).
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
