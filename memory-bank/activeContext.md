# Active context

**Branch / PR:** `cursor/initial-kotlin-durpalla-d9f4` → [PR #1](https://github.com/jewel-rana/durpalla-customer-kotlin/pull/1) (base `main`).

**Just done**
- Correction: client omit/`provider` missing → **`webrtc`** (matches driver). Server still sends `provider: agora` at launch by default; clients do not assume agora.
- Docs + memory bank updated; channel `call_{callId}` fallback and `callLog.provider` unchanged.

**Current focus for next agents**
- Remaining `MASTER_PORT_TODOS.md` assets/ops; live calling when backend ready (`USE_MOCK_CALLS=false`).

**Do not reinvent:** `calling/CallTransport*` + existing WebRTC engine; screens/components in `codeMap.md`.
