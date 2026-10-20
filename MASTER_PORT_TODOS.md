# Master port TODOs (assets / ops not available in this environment)

From Flutter `master` @ `8ee2bed` vs `main`. Items below need files or console setup that are not in the Kotlin zip / this agent environment.

| Item | Spec | Status |
|---|---|---|
| `assets/certs/api_cert.pem` | Cert pin for `apigw.durpalla.com` (P1 §2.3) | **TODO** — `network_security_config.xml` documents the pin-set; add PEM to `app/src/main/res/raw/` and wire pin digest |
| SolaimanLipi Bangla font | Bundled TTF (P2 §10.4, 2026-10-19) | **TODO** — copy from Flutter `assets/fonts` / `google_fonts`; theme already has Inter |
| Full ARB → strings (~512 keys) | `app_en.arb` / `app_bn.arb` (P1 §10.1) | Partial — auth + core strings present; regenerate remaining from ARB |
| Brand / home hero assets | `assets/logos/*`, home heroes (P2 §10.5) | **TODO** — copy from Flutter master; current drawables kept |
| Pusher / reCAPTCHA keys out of source | `.env` removed on master (P2 §1.6) | Keys still in `BuildConfig` with TODO comments; prefer `local.properties` |
| Firebase App Check + Crashlytics Gradle plugins | P1 §7.4 | Analytics + Remote Config deps added; App Check / Crashlytics plugins not wired |
| Language onboarding + `locales_config.xml` | P1 §10.3 | **TODO** screen + Android 13 per-app language config |
| Remote l10n `GET localizations/customer-app/{code}` | P1 §10.2 | **TODO** overlay + RTL locales |
| Home redesign (~2650 lines) | P1 §7.3 | Partial — boat/entertainment top lists can be wired; full re-skin pending assets |
| `event_reminder_voice.mp3` | FCM custom sound | Still not bundled (unchanged) |

## Applied in this port (see commit / API_MAP.md)

P0 HTTP headers (X-Guest-Id, Accept/Content-Language), assets rewrite, my/bookings/android, cart sync + charge/VAT/expiry, booking amount, hotel multi-room hold/release, tour/entertainment/boat-rental real APIs, cancel rules, email+mobile auth (prior), payment URL hints, gateway RC filter, invoice link, hotel_review_prompt push.
