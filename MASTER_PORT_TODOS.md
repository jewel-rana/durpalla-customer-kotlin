# Master port TODOs (assets / ops not available in this environment)

From Flutter `master` @ `8ee2bed` vs `main`. Items below need files or console setup that are not in the Kotlin zip / this agent environment.

| Item | Spec | Status |
|---|---|---|
| `assets/certs/api_cert.pem` | Cert pin for `apigw.durpalla.com` (P1 §2.3) | **TODO** — `network_security_config.xml` documents the pin-set; add PEM to `app/src/main/res/raw/` and wire pin digest |
| SolaimanLipi Bangla font | Bundled TTF (P2 §10.4, 2026-10-19) | **TODO** — copy from Flutter `assets/fonts` / `google_fonts`; theme already has Inter |
| Full ARB → strings (~512 keys) | `app_en.arb` / `app_bn.arb` (P1 §10.1) | Partial — auth + core + calling + l10n chrome present; regenerate remaining from ARB |
| Brand / home hero assets | `assets/logos/*`, home heroes (P2 §10.5) | **TODO** — copy from Flutter master; current drawables kept |
| Pusher / reCAPTCHA keys out of source | `.env` removed on master (P2 §1.6) | Keys still in `BuildConfig` with TODO comments; prefer `local.properties` |
| Firebase App Check + Crashlytics Gradle plugins | P1 §7.4 | Analytics + Remote Config deps added; App Check / Crashlytics plugins not wired |
| Language onboarding + `locales_config.xml` | P1 §10.3 | **Done** — `LanguageSelectScreen`, prefs `app_locale` / `language_onboarding_done`, `AppCompatDelegate.setApplicationLocales`, `res/xml/locales_config.xml`, settings picker |
| Remote l10n `GET localizations/customer-app/{code}` | P1 §10.2 | **Done** — `RemoteLocalizationService` caches `translations`+`version`, overlays via `t()` / `LocalL10nOverlay`; RTL for ar/ur/fa; Accept-Language / Content-Language from `app_locale` |
| Home redesign (~2650 lines) | P1 §7.3 | Partial — boat/entertainment top lists can be wired; full re-skin pending assets |
| `event_reminder_voice.mp3` | FCM custom sound | Still not bundled (unchanged) |

## Applied in this port (see commit / API_MAP.md)

P0 HTTP headers (X-Guest-Id, Accept/Content-Language), assets rewrite, my/bookings/android, cart sync + charge/VAT/expiry, booking amount, hotel multi-room hold/release, tour/entertainment/boat-rental real APIs, cancel rules, email+mobile auth (prior), payment URL hints, gateway RC filter, invoice link, hotel_review_prompt push.

Remote languages hi/ar/zh/ur/fa/tr/es/it + first-launch language select + settings picker (§10.2 / §10.3).

## Driver calling

See [docs/calling-contract.md](docs/calling-contract.md). Mock on by default (`USE_MOCK_CALLS=true`). Wire HTTP + Firestore when backend is ready; iOS-only `POST /devices/voip` is N/A here.

**Calling media:** create + accept return `{callId, provider, agora|null, iceServers|null, ttl?}`; renew `POST /calls/{id}/token` only; no GET agora-token; **client omit → webrtc**. See `docs/calling-contract.md`.

## l10n note

Bundled resources remain `values` (en) and `values-bn`. Remote codes use English resources as the base with a key→string overlay (`t(R.string…)`). Prefer `t()` for user-visible chrome that remote packs may translate; `stringResource` still works for bundled en/bn via AppCompat locales.
