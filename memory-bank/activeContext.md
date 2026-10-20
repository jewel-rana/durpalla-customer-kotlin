# Active context

**Branch / PR:** `cursor/initial-kotlin-durpalla-d9f4` → [PR #1](https://github.com/jewel-rana/durpalla-customer-kotlin/pull/1) (base `main`).

**Just done**
- Merged `origin/main` (added `.cursor/rules/token-saving.mdc`).
- Added `memory-bank/` + `.cursor/rules/memory-bank.mdc`.
- Calling aligned with driver PR #6 / main contract (firebase-token, ICE ttl/2, caller-only ICE restart, end reasons, error l10n, callLog UI, Pusher `private-user-{userId}`).

**Current focus for next agents**
- Close remaining `MASTER_PORT_TODOS.md` items (certs, fonts, ARB completeness, brand assets, secrets out of BuildConfig, App Check/Crashlytics, home re-skin, reminder mp3).
- Flip `USE_MOCK_CALLS` when backend + `google-services.json` are ready; verify live calling against contract.

**Do not reinvent:** screens/components in `codeMap.md`; calling stack under `calling/`; JSON via `core/Json.kt`; strings via `t()` / `AppCtx.str`.
