# In-app calling contract (driver ⇄ customer)

Audio-only **WebRTC** calls between the driver and the customer of a trip. **Firebase Firestore** carries signaling. This contract is shared with the customer apps (Android and iOS), so field names, statuses and audio settings must match exactly.

- Android driver library: `io.getstream:stream-webrtc-android` (the `org.webrtc` API).
- Code: `app/src/main/java/com/durpalla/driver/call/`.

## 1. REST endpoints

- Base URL: `API_BASE_URL`.
- Every request sends `Authorization: Bearer <access token>`.
- Request and response bodies are JSON.

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/calls` | `{ "tripId": "t_123" }` | `201 { "callId": "c_456" }` |
| POST | `/calls/{callId}/accept` | `{}` | `200 {}` |
| POST | `/calls/{callId}/decline` | `{}` | `200 {}` |
| POST | `/calls/{callId}/end` | `{ "reason": "hangup" \| "cancelled" \| "missed" \| "failed" }` | `200 {}` |
| GET | `/calls/ice-servers` | – | `200 [ { "urls": ["turn:…"], "username": "…", "credential": "…" } ]` (or `{ "iceServers": [...], "ttl": 3600 }`) |
| POST | `/devices/voip` | `{ "voipToken": "…", "platform": "ios", "userId": "…" }` | `200 {}` (iOS PushKit only) |
| GET | `/auth/firebase-token` | – | `200 { "token": "<Firebase custom token>" }` (uid = Durpalla userId) |

### What the endpoints do

- **There is no `rtcToken` and no `channel`.** The `callId` is the only handle.
- **POST /calls** checks that the caller is a party to the trip and that the trip is calling-eligible (`UPCOMING`, `ON_THE_WAY`, `ARRIVED` or `IN_PROGRESS`). It then:
  - creates the Firestore doc `calls/{callId}` with `status: "ringing"`;
  - rings the callee;
  - starts a server-side **30 s missed timeout**. If the doc is still `ringing` at 30 s, the server sets `status: "missed"`, `reason: "no_answer"` and `endedAt`, and sends `call.missed` to both parties.
- **accept** sets `status: "accepted"` and `answeredAt`.
- **decline** sets `status: "declined"`, `endedAt`, `endedBy` and `reason: "declined"`.
- **end** sets `status: "ended"` (or `"missed"` when `reason` is `missed`), plus `endedAt`, `endedBy` and `reason`.
- The server writes the call-log entry on the trip (section 6).
- **ice-servers:**
  - TURN credentials are short-lived. `ttl` is in seconds; clients cache for at most `ttl/2`.
  - `urls` may be a string or an array.
  - If the call fails or returns an empty list, clients fall back to `stun:stun.l.google.com:19302`.

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
  { "event": "call.incoming", "callId": "c_456", "tripId": "t_123", "callerName": "Rahim" }
  ```
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
  offer:       { type: "offer",  sdp: string }    // written by the caller
  answer:      { type: "answer", sdp: string }    // written by the callee
  status:      "ringing" | "accepted" | "declined" | "ended" | "missed"
  createdAt:   timestamp        // server
  answeredAt:  timestamp | null
  endedAt:     timestamp | null
  endedBy:     string | null    // userId
  reason:      string | null    // hangup | cancelled | declined | no_answer | missed | failed | busy
}
calls/{callId}/callerCandidates/{auto} { candidate: string, sdpMid: string, sdpMLineIndex: number }
calls/{callId}/calleeCandidates/{auto} { candidate: string, sdpMid: string, sdpMLineIndex: number }
```

### Call flow

1. **Caller:**
   1. `POST /calls` returns the `callId`.
   2. `GET /calls/ice-servers`.
   3. Create the PeerConnection (unified plan, audio track only).
   4. `createOffer`, apply the audio SDP munging (section 7), `setLocalDescription`.
   5. Write `offer`.
   6. Add local ICE candidates to `callerCandidates`.
   7. Listen to the doc and to `calleeCandidates`.
2. **Callee:**
   1. On `call.incoming`, ring.
   2. On Accept, `POST /calls/{id}/accept`.
   3. Read `offer`, `setRemoteDescription`.
   4. `createAnswer`, apply the munging, `setLocalDescription`.
   5. Write `answer` and `answeredAt`.
   6. Add candidates to `calleeCandidates`. Listen to `callerCandidates` and the doc.
3. **Remote candidates:** candidates that arrive before the remote description is set are queued and added afterwards.
4. **Connected:** the UI shows the timer when ICE is `CONNECTED`.
5. **End:** either side calls `POST /calls/{id}/end`. When a client sees a terminal `status` in the doc, it tears down and shows "Call ended", "Declined" or "No answer".
6. **ICE restart:**
   - The **caller** drives it, on a network change (Wi-Fi ⇄ mobile) or on ICE `DISCONNECTED`: `restartIce()`, then a new offer with `IceRestart`, munged, written to `offer`.
   - The callee answers any new `offer` with a new `answer`.
   - New candidates go into the same subcollections.
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
  { "callId": "c_456", "callerRole": "driver", "status": "ended",
    "createdAt": 1760960000000, "answeredAt": 1760960004000, "endedAt": 1760960070000,
    "durationSec": 66, "endedBy": "u_customer", "reason": "hangup" }
]
```

- `status` takes the same values as the Firestore doc.
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
