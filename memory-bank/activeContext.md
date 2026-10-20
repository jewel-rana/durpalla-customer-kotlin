# Active context

**Branch / PR:** `cursor/initial-kotlin-durpalla-d9f4` → [PR #1](https://github.com/jewel-rana/durpalla-customer-kotlin/pull/1) (base `main`).

**Just done**
- Matched driver/apigw §0: agora-token `{data:{…}}` + `expiresAt` seconds; uid = customer id; caller joins after POST, callee after accept; accept/decline/end apply `provider`; `CALL_PROVIDER_MISMATCH` / `CALL_PROVIDER_UNAVAILABLE` EN/BN; CHATROOM scenario + weak quality (≥4 / rtc stats); client omit → webrtc; channel `call_{callId}`; `callLog.provider`.

**Current focus for next agents**
- Remaining `MASTER_PORT_TODOS.md` assets/ops; live calling when backend ready (`USE_MOCK_CALLS=false`).

**Do not reinvent:** `calling/CallTransport*` + existing WebRTC engine; screens/components in `codeMap.md`.
