# Active context — durpalla-customer-kotlin

## 2026-11-23 — Call setup failure handling
- Incoming call setup failures are caught in Pusher, FCM, notification, and activity paths; notification Accept no longer starts a duplicate uncaught accept coroutine.
- Local apigw has no `storage/app/firebase/service-account.json`, so `GET /auth/firebase-token` returns `FIREBASE_NOT_CONFIGURED`; calls still need that server credential to work.

## 2026-11-23 — Guest supplier seat holds
- Paribahan seat selection stays guest-accessible. The seat-hold form collects passenger name/mobile, prefilled from the signed-in profile when available and editable; checkout remains sign-in gated.
- Passenger details are kept in the trip screen state until the user leaves it; held supplier bookings receive the values with the lock request.

## 2026-11-23 — Local API debug build
- Debug `.env` points to `http://apigw.durpalla.site/api/v1`; the desktop resolves this hostname to loopback, so `API_DNS_OVERRIDE=192.168.0.136` maps it to the desktop LAN IP in the debug OkHttp client. Debug-only network security permits cleartext for this host; the base policy remains HTTPS-only.
- `./gradlew installDebug` succeeded on the connected RMX3286 (Android 13); app logcat confirmed local API calls return HTTP 200.

## 2026-11-23 — Paribahan (partner bus) parity with durpalla-web `42cbd55`
- **Trip ids are strings** (`pb:123` for Paribahan): `TripSummary.tripId`, `CartItem.tripId`, `TripDetailsVm`, `Routes.trip(id, type, bookToken)` → `trip/{id}/{type}?bt={bt}` (id `Uri.encode`d). Compare trip ids as strings.
- **book_token**: parsed from search rows, passed via nav `bt`, sent as `GET trip/{id}?book_token=`; merged into trip data if API omits it.
- **cart/add**: when trip has `book_token` → JSON body (`SupplierLock`: seat_ids = `supplier_seat_id`, seat_lbls, ticket_prices, passenger name/mobile from session user, boarding point id/name) + `Idempotency-Key`; otherwise unchanged form post. Lock id falls back to `booking_id`; meta stores `trip_id`, `supplier`, `book_token`, `booking_id`, boarding.
- **cart/unlock**: `TripRepository.releaseLock(item)` adds `supplier` + `booking_id` (used by trip screen, checkout remove, expiry).
- **Results**: departure clock from `leaving_time` → ISO `leaving_at` (`displayClock` in `Boarding.kt`); fare also from `base_fare`.
- Checkout `order/confirm` vs `booking/confirm`, boarding picker/lock and cart-meta merge were already ported; added missing `boarding_point`/`boarding_hint`/`boarding_required` strings (EN/BN).

## 2026-11-22 — Password Change & Show/Hide Toggle
- **Password Change API payload**:
  - Backend `MyApiController::changePassword` specifically validates `'password'` and `'confirm_password' => 'required|same:password'`.
  - Updated `ProfileRepository.changePassword` to pass both `confirm_password` and `password_confirmation`.
  - Enhanced `JsonObject.success` helper to recognize `status: "success"` as well as boolean / string `success` and `status`.
- **Show/Hide Password Toggle**:
  - Added visibility toggle icon buttons (`Icons.Default.Visibility` / `Icons.Default.VisibilityOff`) to all three password fields (Current password, New password, Confirm password) in `ChangePasswordScreen`.
  - Updated inputs to switch between `VisualTransformation.None` and `PasswordVisualTransformation()`.
  - Added modern `RoundedCornerShape(14.dp)` matching authentication inputs.
  - Aligned client validation with backend constraints (minimum 6 characters, required fields).

## 2026-11-22 — My Trip Requests & Booking Details Improvements
- **My Trip Requests screen (`BoatMyTripRequestsScreen`)**:
  - Auto-cancelled logic for expired requests: Evaluates `isExpired` against `status == "expired"`, `expires_at`, `ends_at`, or `starts_at` in the past.
  - Automatically marks and labels expired items with an "Auto Cancelled" / "Cancelled" red pill (`Brand.Error` on light red background).
  - Hides the "Cancel Request" action button completely for expired, cancelled, and awarded requests (prevents no-op actions).
  - Disables accepting bids on expired/cancelled trip requests.
  - Human-friendly date/time formatting: Converts raw ISO timestamps (`starts_at`, `ends_at`) to formatted strings like `22 Nov 2026, 9:00 AM – 6:00 PM` or `22 Nov 2026, 9:00 AM`.
  - Added localized string `auto_cancelled` in EN and BN.
- **Booking Details & Ticket Screens Enhancements (`TicketScreens.kt`)**:
- **Items section visibility**: When a booking has no items (`b.items.isEmpty()`), the items section header and card are completely hidden.
- **Service name badge**: Top right corner of the HeroGradient booking card displays the detected service name (Hotel, Boat, Entertainment, Tour, Launch, Bus).
- **Conditional QR rendering**: Live QR code with centered logo renders only when payment/booking status is successful/confirmed/completed. For unpaid, pending, or failed states, an empty QR placeholder box with a QR outline icon, payment status label, and hint is displayed.
- **Highlighted download invoice**: Download invoice button is highlighted as a full-width primary button (`Brand.Primary` container, white text, elevated). String keys `download_invoice` added for EN and BN.
- **Payment method & gateway info**: Shows payment method and gateway rows inside booking details info card when available from API response.

## 2026-11-21 — Full feature & real-API parity with `durpalla-flutter-app`

- **Transport search by Ghat ID (`GhatPlace`):** `GhatPlaceField` loads initial `trip/ghat-suggestions` on focus (`q=""`), filters as user types, clears unpicked text on blur, disables Search until both `fromId > 0` and `toId > 0` are selected, and sends `from_location_id` & `to_location_id` to `GET search`.
- **Seat / cabin grid orientation & chrome (`TripDetailsScreen`):** Renders outer `cabin_row` keys as vertical columns left→right (`Row` of `Column`s), preserves empty `[]` columns as aisle gaps, prefers `seat` tab for bus/train and `cabin` for launch, adds `Door ——— Driver` / `River side ——— Bank side` / `Engine ——— Toilet / Exit` header chrome, and adds launch class filter chips.
- **Results filters:** Added departure time-of-day chips (Morning / Afternoon / Night) and operator chips on `ResultsScreen`.
- **Verticals completed:**
  - **Boats:** Boat type filter chips (`House Boat`, `Speed Boat`, `Steamer`, `Fishing Boat`), user date/time pickers + 6:00 AM–10:00 PM service hours guard (`BoatServiceHours`), and `BoatMyTripRequestsScreen` (`listTripRequests`, `tripRequest`, `bids`, `acceptBid`, `cancelTripRequest`).
  - **Entertainment:** Object-safe label coercion (`city: {id, name}`), `slot_templates` picker when `capacity_mode == "slot"`, per-`ticket_types` quantity steppers (0–20), and venue reviews (`GET/POST entertainment/{id}/reviews`).
  - **Hotels:** Adults/children selectors, multi-room quantity steppers, kind filter chips (`hotel`/`resort`/`homestay`), and hotel review submission dialog (`POST hotel/{id}/reviews`).
- **Profile, Auth, CMS & Checkout compliance:**
  - Interactive circular drag + zoom **100×100 JPEG cropper** (`AvatarCropDialog`) + multipart `avatar` upload to `my/profile`.
  - `ChangePasswordScreen` calling `POST my/password/change`.
  - Real `GET faq`, `GET page/{slug}` (`terms-and-conditions`, `privacy-policy`, `return-and-refund-policy`, `about-us`), and dynamic support contacts/WhatsApp/Messenger/addresses from `site/init` & `public/app-config`.
  - Mandatory Cancellation/Refund + Terms + Privacy agreement checkbox on `PaymentMethodsScreen` and multi-page pagination on `TicketsScreen`.
- **Home & Assets:** Wired `appConfig()` + `site/init` service filtering, maintenance banner, Top Boats & Top Entertainment carousels; copied `SolaimanLipi` fonts, `api_cert.pem`, and hero images from `durpalla-flutter-app/assets/`; made `USE_MOCK_CALLS` default to `false` (configurable via `local.properties`).
