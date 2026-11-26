# Progress

Target: Flutter master `@8ee2bed`. Tracker detail: `MASTER_PORT_TODOS.md`.

## Done
- **Supplier customer policies (2026-11-26)**: Display supplier terms and cancellation policy links on trip details, checkout, payment and ticket details when configured.
- **Booked seat AC label (2026-11-26)**: Show AC or Non-AC on ticket items only when the gateway provides an explicit flag.
- **Booking payment window (2026-11-26)**: Show Pay now on ticket details for pending and reserved bookings within the gateway payment window. Refresh the booking at the deadline.
- **Messenger support link (2026-11-26)**: Request device unlock when needed before handing the Messenger support conversation to an external browser. Show phone/email guidance when the link cannot launch.
- **Gateway payment verification (2026-11-26)**: The payment result screen displays success only when the gateway API returns verified paid status. Pending or unavailable verification can be rechecked without restarting payment.
- Select Paribahan seats together and hold them in one supplier request so the cart can check out with one reservation. Release an existing hold before changing its seats.
- Transport seats can be held as a guest. Checkout prompts for sign-in. The gateway supplies guest contact details for Paribahan holds.
- **Mobile sign-in cursor (2026-11-25)**: Keep phone formatting separate from editable digits.
- **Transport seat hold sign-in (2026-11-25)**: Prompt for sign-in when a guest selects a seat and use the registered profile for supplier holds.
- **Ticket details and language (2026-11-25)**: Show booking items and boarding points, support eligible cancellation, and update bundled labels on language change.
- **Home service shortcuts (2026-11-25)**: Use the selected service when opening Search from Home.
- **Ghat suggestion visibility (2026-11-25)**: Fit the list in the resized search viewport and scroll it into view with the field.
- **Checkout policy consent (2026-11-25)**: Carry the checkout checkbox state into payment.
- **Customer booking UX (2026-11-25)**: Inline ghat suggestions, clearer seat states and cabin labels, and native handling of storefront payment returns.
- **Seat realtime (2026-11-25)**: Android handles native gateway seat events and refreshes Paribahan layouts on supplier availability broadcasts.
- **Paribahan partner trips (2026-11-23)**: string trip ids (`pb:`), `book_token` through search → trip → `cart/add` JSON, supplier-aware `cart/unlock`, ISO departure clocks — parity with durpalla-web.
- **Password Change & Show/Hide Toggle (2026-11-22)**:
  - Fixed password change API payload (`confirm_password` required by backend validator).
  - Added show/hide password toggle for all three password fields in `ChangePasswordScreen`.
- **Boat My Trip Requests enhancements (2026-11-22)**:
  - Auto-cancelled expired trip request items with localized `auto_cancelled` pill.
  - Removed "Cancel Request" button for expired/cancelled/awarded items.
  - Human-friendly date & time formatting for trip requests (`d MMM yyyy, h:mm a`).
- **Ticket / Booking Details enhancements (2026-11-22)**:
  - Hide items section when booking has no items.
  - Service name badge on top right corner of booking header card.
  - QR code displayed only on successful/confirmed bookings; empty placeholder box for unpaid/pending/failed.
  - Highlighted prominent download invoice button with `Brand.Primary`.
  - Payment method and gateway info rows displayed in booking details card.
- **Debug APK builds** (`./gradlew assembleDebug`): compile fixes landed; debug NDK `abiFilters` = `arm64-v8a` (artifact size). APK: `/opt/cursor/artifacts/durpalla-customer-debug.apk` and `app/build/outputs/apk/debug/app-debug.apk`.
- P0 HTTP: `X-Guest-Id`, language headers, assets rewrite, `my/bookings/android`, cart sync (charge/VAT/expiry), hotel multi-room, tour/entertainment/boat APIs, cancel rules, email+mobile auth.
- P1 partial: ApiUserMessage, secure token, payment WebView hints, RC gateway filter, invoice link, `hotel_review_prompt`, `app-config?platform=android`.
- L10n §10.2/§10.3: remote packs, language onboarding, settings picker, RTL for ar/ur/fa, bundled en/bn.
- Hard-coded English audit: **173 found → 173 fixed** (0 intended leftovers for migration). Left intentionally: routes, API keys/paths, logs, place names, wire protocol strings, emails, date patterns.
- Calling: WebRTC + Firestore contract copy; weak-network Opus/bitrate/ICE; driver PR #6 parity (firebase-token, ICE shapes/ttl/2, caller-only restart, `/end` reasons, CallApiErrors EN/BN, trip `callLog[]`, Pusher private user channel, FCM `event`).
- **Agora / WebRTC media bootstrap** (Jewel contract): create + accept return `{callId, provider, agora|null, iceServers|null, ttl?}`; no GET agora-token; renew via `POST /calls/{id}/token`; **client omit → webrtc**; channel `call_{callId}`; `callLog.provider`; CHATROOM + weak≥4/rtc stats; `CALL_PROVIDER_*` EN/BN.
- Memory bank + agent rules (`token-saving`, `memory-bank`).

## Partial / TODO (see MASTER_PORT_TODOS)
| Item | Status |
|---|---|
| API cert pin PEM | TODO |
| SolaimanLipi Bangla font | TODO |
| Full ARB → strings (~512) | Partial |
| Brand / home hero assets | TODO |
| Secrets → local.properties | TODO |
| Firebase App Check + Crashlytics | TODO |
| Home redesign (~2650 lines) | Partial |
| `event_reminder_voice.mp3` | TODO |
| Live calling (`USE_MOCK_CALLS=false` + backend) | Blocked on env |

## Known contract deltas (calling)
- Wire `tripId` string vs local `Int` (POST `toString()`; push parse fallback).
- FCM prefers `event`, still accepts `type`.
- Pusher auth URL = customer `{BASE_URL}realtime/auth`.
- Calling media bootstrap on create/accept; client missing-`provider` → **webrtc**; renew via `POST …/token`.
