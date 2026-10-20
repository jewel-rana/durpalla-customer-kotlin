# Active context

**Branch / PR:** `cursor/initial-kotlin-durpalla-d9f4` → [PR #1](https://github.com/jewel-rana/durpalla-customer-kotlin/pull/1) (base `main`).

**Just done**
- Contract change: no start-time agora-token GET. `POST /calls` + `POST /calls/{id}/accept` return `{data:{callId, provider, agora|null, iceServers|null, ttl?}}`; transport from `provider` (omit → webrtc); Agora uses `agora` block; WebRTC uses `iceServers` (null → GET ice-servers / STUN). Renewal only `POST /calls/{id}/token`. Removed GET agora-token. Docs + memory-bank + mocks updated.

**Current focus for next agents**
- Remaining `MASTER_PORT_TODOS.md` assets/ops; live calling when backend ready (`USE_MOCK_CALLS=false`).
