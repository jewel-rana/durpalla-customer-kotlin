# Active context

**Branch / PR:** `cursor/initial-kotlin-durpalla-d9f4` → [PR #1](https://github.com/jewel-rana/durpalla-customer-kotlin/pull/1) (base `main`).

**Just done**
- Driver team **confirmed** Agora contract: launch default `provider=agora`; channel `call_{callId}` (server channel with that fallback); `callLog[]` includes `provider`.
- Docs + memory bank marked confirmed; client defaults / channel resolve / callLog parse updated.

**Current focus for next agents**
- Remaining `MASTER_PORT_TODOS.md` assets/ops; live calling when backend ready (`USE_MOCK_CALLS=false`).

**Do not reinvent:** `calling/CallTransport*` + existing WebRTC engine; screens/components in `codeMap.md`.
