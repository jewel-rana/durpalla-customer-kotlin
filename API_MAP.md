# API map — Flutter **master** (`8ee2bed`) → Kotlin

Target: `Durpalla/durpalla-flutter-app` **master** @ `8ee2bed` (not `main`).  
See also `MASTER_PORT_TODOS.md` and `/cursor/stores/bot/flutter-master-diff.md`.

Base URL: `BuildConfig.BASE_URL` = `https://dev-apigw.durpalla.com/api/v1/` (debug) / `https://apigw.durpalla.com/api/v1/` (release). Trailing slash kept for Retrofit; request paths are stripped of a leading `/`.

## Common headers

| Header | Notes |
|---|---|
| `Accept: application/json` | |
| `Cache-Control` / `Pragma` / `Expires` | no-cache |
| `Authorization: Bearer <token>` | when signed in (EncryptedSharedPreferences) |
| **`X-Guest-Id`** | captured from response `x-guest-id`, persisted, sent on every call |
| **`Accept-Language`** / **`Content-Language`** | current app locale (`en` / `bn` / …) |

Response: non-JSON → bad-response message; 401 → logout when authenticated → guest Home; `success` from `status==true` if missing.  
Debug: rewrite `https://assets.durpalla.com` → `BuildConfig.ASSETS_BASE_URL` (dev-assets) in JSON bodies.

## Auth (email + mobile)

Every call except forgot / push-bind sends **`channel`** = `mobile`|`email` plus **`email`** or **`mobile`**.

| Step | Endpoint | Payload |
|---|---|---|
| Check | POST `auth/check` | channel, email\|mobile, recaptchaToken → may return `use_email` |
| OTP send | POST `auth/otp/send` | channel, email\|mobile, recaptchaToken |
| Verify | POST `auth/verify` | channel, email\|mobile, **otp**, type=register, recaptchaToken |
| Login | POST `auth/login` | channel, email\|mobile, password, device_*, fcm_token, recaptchaToken |
| Register | POST `auth/register` | channel, name, mobile, [email], password, confirm_password, **otp_code**, device_*, … |
| Forgot | POST `auth/forgot` | **mobile only** (no channel, no recaptcha) |
| Forgot verify | POST `auth/verify` | mobile, **otp_code**, type=forgot, recaptchaToken |
| Reset | POST `auth/reset` JSON | mobile, password, confirm_password, recaptchaToken |
| Push bind | POST `auth/push/bind` JSON | token, platform |

Phone: BD `01XXXXXXXXX`; intl as typed `^\+?[1-9]\d{7,14}$`. No `country_code`/`dial_code`.

## Transport / cart / booking

| Feature | Method & path | Notes |
|---|---|---|
| Search transport | GET `search` | type=launch\|bus\|train\|flight (not boat) |
| Ghat suggestions | GET `trip/ghat-suggestions` | query, exclude, type + local fallback |
| Trip layout | GET `trip/{id}` | Pusher `trip.{id}` lock/booked/released |
| Lock | POST `cart/add` form | X-Idempotency-Key, X-Socket-Id |
| Unlock | POST `cart/unlock` JSON | |
| Cart sync | GET `cart` | startup; empty keeps local; fields total_charge, total_vat, expires_at |
| Confirm | POST `booking/confirm` | amount = subtotal + Σcharge + ΣVAT; for_self 0/1 |
| My bookings | GET `my/bookings/android?page=` then `my/bookings` | |
| Booking details | GET `my/booking/android/{id}` | QR `qr_code`\|`qr`\|`qrCode`; invoice_url |
| Cancel item | POST `booking/item/{id}/cancel` | gated by BookingCancelRules |

## Hotels

| Feature | Path | Notes |
|---|---|---|
| Search / details / rooms | `hotel/search`, `hotel/{id}`, `hotel/{id}/rooms` | kinds hotel\|resort\|homestay |
| Quote | POST `hotel/quote` | |
| Hold | POST `hotel/hold` | body `{lines:[{room_type_id,quantity}], check_in, check_out, adults, children}`; Idempotency-Key `hc…` ≤64 |
| Release | POST `hotel/hold/release` `{hold_id}` (DELETE fallback) | |
| Confirm | POST `hotel/booking/confirm` `{hold_id}` | total from booking.total_payable |
| Favourites | GET/POST/DELETE `my/favourite/hotels` | |

## Tours / entertainment / boat rental (real APIs)

| Vertical | Endpoints |
|---|---|
| **Tour** | GET `tour/search`, `tour/{id}`; POST `tour/quote`, `tour/hold` (Idempotency-Key), `tour/booking/confirm` `{hold_id, platform:android, customer_name, customer_mobile}` |
| **Entertainment** | GET `entertainment/home/top`, `/search`, `/{id}`, `/{id}/reviews`; POST reviews, `entertainment/quote`, `entertainment/hold`, `entertainment/booking/confirm` `{hold_id, platform:mobile, guest, payment:{mode:none}}` |
| **Boat rental** | GET `boat-rental/home/top`, `/search` `{q?, starts_at, ends_at}`, `/{id}`, `/stoppages`, `/trip-requests`, `/trip-requests/{id}`, `/trip-requests/{id}/bids`; POST quote, hold, booking/confirm, trip-requests, cancel, `bids/{id}/accept` |

All vertical checkouts land on the same `PaymentOptionsScreen(bookingId, amount)`.

## Payment

| Step | Path | Notes |
|---|---|---|
| Gateways | GET `gateway?booking_id=` | fee from `meta.fee`; filter by Remote Config payment_* flags |
| Make | POST `payment/make` | |
| Verify | GET `payment/verify` | |
| WebView finish | | `/payment/success`, `/sslcommerz/success`, `/payment/fail`, `/sslcommerz/fail`, `payment_id=`, `result=` plus legacy hints |

## Home / config / l10n

| Feature | Path |
|---|---|
| App config | GET `public/app-config?platform=android` |
| Popular trips | GET `public/popular-upcoming-trips` |
| Offers | GET `offers` |
| Hotel top | GET `hotel/home/top` |
| Remote languages | GET `localizations/customer-app/{code}` → `data.translations` + `data.version` (cached overlay; RTL for ar/ur/fa) |

Firebase Remote Config keys: maintenance_*, booking_enabled, payment_*_enabled, booking_hold_minutes, support_*, show_new_*, minimum/recommended_app_version.

## Push

Channels `trip_reminder_channel_v2`, `default_channel`.  
`data.is_reminder==true` → reminder.  
`data.type=hotel_review_prompt` + `hotel_id` → hotel details deep link.

## Kotlin repositories

`AuthRepository`, `HomeRepository`, `TripRepository`, `BookingRepository`, `PaymentRepository`, `HotelRepository`, `TourRepository`, `EntertainmentRepository`, `BoatRentalRepository`, `ApiExperienceRepository`, `CartStore`, `ProfileRepository`.
