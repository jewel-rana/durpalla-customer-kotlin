# Active context

**Branch / PR:** `cursor/initial-kotlin-durpalla-d9f4` → [PR #1](https://github.com/jewel-rana/durpalla-customer-kotlin/pull/1) (base `main`).

**Just done**
- Live apigw deltas: parse create/accept `tripId`+`status` (unused OK); `iceServers` null → GET ice-servers then STUN; token renew 409 `CALL_PROVIDER_MISMATCH` / `CALL_ALREADY_ENDED` → stop renew + local end with localized message.

**Current focus for next agents**
- Remaining `MASTER_PORT_TODOS.md` assets/ops; live calling when backend ready (`USE_MOCK_CALLS=false`).
