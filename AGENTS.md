# durpalla-customer-kotlin — Agent Rules

- Stack: Native Android (Kotlin, Jetpack Compose, Gradle Kotlin DSL).
- Follow existing folder structure and conventions.

## Token Efficiency

Minimize tokens. Prefer reuse over discovery.

### Context
- Read ≤5 files per step; ≤500 lines total; only the needed function/section.
- Never open `node_modules/`, `.next/`, `dist/`, `build/`, `coverage/`, `vendor/`, `.gradle/`, or `.dart_tool/`.
- Never open `public/` or `assets/` unless assets are requested.
- No repo-wide scans/searches unless the user asks.
- Prefer targeted grep/glob over reading whole files.
- Do not re-read files already in context this turn.

### Edits
- Touch only task-related files; minimal diffs.
- Never regenerate unchanged code or rewrite whole files for small edits.
- Reuse existing components, services, hooks, API clients, and theme tokens.
- Do not invent new UI primitives, colors, fonts, or spacing.
- Never delete, empty, or strip `.cursor/rules/*`, `.agents/rules/*`, or `AGENTS.md` unless the user explicitly asks; add/update only, keep prior content.

### Output
- Brief answers; no file summaries unless asked.
- No repeating prior analysis.
- List changed files only; skip long narration.
- Skip unsolicited plans, TODOs, and markdown docs.

## Memory Bank & Git Pull Rules

1. **Read first** — Before implementing, open the relevant `memory-bank/` files (at least `projectbrief.md` and `activeContext.md`). Prefer `memory-bank/` facts over scanning the repo.
2. **Pull before edit** — Before the first edit in any task, pull from the remote branch first (`git pull`, then `git pull origin master`). Do not start edits until the pull succeeds.
3. **Update always** — After meaningful work, update `memory-bank/activeContext.md` (and `progress.md` when applicable) in the same session.
4. **Synced rules** — Full rule details live in `.cursor/rules/*.mdc` and `.agents/rules/*.md`.
