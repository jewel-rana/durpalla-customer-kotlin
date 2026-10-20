# Active context

**Branch / PR:** `cursor/initial-kotlin-durpalla-d9f4` → [PR #1](https://github.com/jewel-rana/durpalla-customer-kotlin/pull/1) (base `main`).

**Just done**
- Agora as second audio transport beside WebRTC (`CallTransport`: `WebRtcTransport` / `AgoraTransport` / `MockCallTransport`).
- Server `provider` on POST /calls, FCM `call.incoming`, and Firestore doc (default `webrtc`).
- `GET /calls/{id}/agora-token` → appId/channel/token/uid (no hard-coded App ID).
- Contract + memory bank updated (Agora section marked pending driver confirmation).

**Current focus for next agents**
- Confirm Agora contract with driver team; keep ringing/UI/callLog/status shared.
- Remaining `MASTER_PORT_TODOS.md` assets/ops; live calling when backend ready (`USE_MOCK_CALLS=false`).

**Do not reinvent:** `calling/CallTransport*` + existing WebRTC engine; screens/components in `codeMap.md`.
