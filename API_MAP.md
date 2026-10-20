# API map — Flutter → Kotlin

Base URL: `BuildConfig.BASE_URL` = `https://dev-apigw.durpalla.com/api/v1/` (debug) / `https://apigw.durpalla.com/api/v1/` (release; Flutter `.env` API_BASE_URL).
Auth endpoints use the same base (Flutter `.env` sets `API_AUTH_BASE_URL` = API_BASE_URL).

Common headers (ApiClient interceptor, from `ApiService._defaultHeaders`): `Accept: application/json`, `Cache-Control: no-cache, no-store, must-revalidate`, `Pragma: no-cache`, `Expires: 0`, `Authorization: Bearer <token>` when signed in.
Response handling (from `ApiService._decode`): non-JSON → `{success:false, message:"Invalid server response"}`; HTTP 401 → `success:false, unauthorized:true` and forced logout if a token existed; `success` defaults to `status == true`. No token refresh exists in the Flutter app (401 = logout), so none is implemented.

## Auth — email + mobile (Flutter `dev`/`master`)

Every auth call (except forgot / push-bind) sends **`channel`** = `mobile` | `email` plus exactly one of **`email`** or **`mobile`**. There is no `identifier` field and **no** `country_code` / `dial_code`.

| Step | Endpoint | channel=mobile | channel=email |
|---|---|---|---|
| Check | POST `auth/check` (form) | channel, mobile, recaptchaToken | channel, email, recaptchaToken |
| Send OTP | POST `auth/otp/send` (form) | channel, mobile, recaptchaToken (SMS) | channel, email, recaptchaToken (email) |
| Verify | POST `auth/verify` (form) | channel, mobile, **otp**, type=register, recaptchaToken | channel, email, **otp**, type=register, recaptchaToken |
| Login | POST `auth/login` (form) | channel, mobile, password, device_id, device_platform, fcm_token, recaptchaToken | same with `email` instead of `mobile` |
| Register | POST `auth/register` (form) | channel, name, mobile, [email], password, confirm_password, **otp_code**, device_*, fcm_token, recaptchaToken, [nid] | same; email required; **mobile still required** |
| Forgot | POST `auth/forgot` → verify (type=forgot, **otp_code**) → reset (JSON) | **mobile only** (no channel, no recaptcha on forgot) | **not supported** |
| Push bind | POST `auth/push/bind` (JSON) | token, platform | token, platform |

Kotlin: `AuthRepository` (`checkStep`, `sendOtp`, `verifyOtp`, `login`, `register`, `forgotStart` / `forgotVerify` / `forgotReset`, `bindPushToken`).

### Identifier values

- **Email:** trimmed lowercase; validator `^[^@]+@[^@]+\.[^@]+`.
- **BD mobile:** `normalizeBdMobile` → `01XXXXXXXXX`.
- **International mobile:** sent **as typed** (trimmed), matching `^\+?[1-9]\d{7,14}$`. The country picker (default +880) only helps compose that string in the UI — it is not sent as separate fields.

### Response signals

- `step`: login | otp | register | reset (same `_inferStep` as Flutter).
- `exists` / `found` / `account_exists`.
- **`use_email`** true/`1`: show *"We can’t send SMS to international numbers yet. Please use Email to continue."* (or server `message`), switch to Email tab, stop.
- Token: `token` / `access_token` (top-level or under `data`).

### Client behaviour (matched)

- Tabs are explicit; switching clears the field — **no auto-detect**.
- OTP is not auto-sent; **exactly 6 digits**; 60s resend.
- Check → login | otp | **register** (register can skip OTP with empty `otp_code`).
- Password ≥ 8 characters on register/reset (not on login).
- Forgot password is **BD mobile only**; never pass an email into it.
- reCAPTCHA token kept (empty on Android, same as Flutter).

## Other endpoints

| Feature | Method & path | Body / query | Flutter source | Kotlin |
|---|---|---|---|---|
| App config | GET `public/app-config` | | home_screen | HomeRepository.appConfig |
| Popular trips | GET `public/popular-upcoming-trips` | | home_screen | popularTrips |
| Top hotels | GET `hotel/home/top` | limit | home_screen | topHotels |
| Offers | GET `offers` | | home_screen | offers |
| Search trips | GET `search` | type (launch/bus/boat), trip_from, trip_to, trip_date=YYYY-MM-DD | search/kind_*.dart | TripRepository.search |
| Ghat suggestions | GET `trip/ghat-suggestions` | query, exclude, type (+ local fallback list) | `fetchGhatSuggestions` | ghatSuggestions |
| City suggestions | GET `city/suggestion` | q | `fetchCitySuggestions` | citySuggestions |
| Trip layout | GET `trip/{id}` | → cabins/seats/sofas… | trip_details_screen | tripDetails |
| Lock item | POST `cart/add` (form) | trip_id, item_id, type + X-Idempotency-Key + X-Socket-Id | `lockItem` | lockItem |
| Release lock | POST `cart/unlock` (JSON) | lock_id, item_id | `releaseLock` | releaseLock |
| Cart | GET `cart` | | cart_provider | cart (in-memory in UI) |
| Confirm booking | POST `booking/confirm` (JSON) | items[…], amount | checkout_screen | BookingRepository.confirm |
| Gateways | GET `gateway` | booking_id | payment_options_screen | PaymentRepository.gateways |
| Start payment | POST `payment/make` (JSON) | order_id, booking_id, gateway_id, amount | payment_options_screen | make |
| Verify payment | GET `payment/verify` | booking_id | global_payment_page | verify |
| My bookings | GET `my/bookings` | page | my_bookings_screen | myBookings |
| Booking details | GET `my/booking/android/{id}` | | my_booking_details_screen | bookingDetails |
| Cancel item | POST `booking/item/{itemId}/cancel` | | my_bookings_screen | cancelItem |
| Profile | GET `my/profile`; POST multipart `_method=PUT` | name, email, phone, avatar | api_service | ProfileRepository |
| Hotel search / details / rooms / quote / hold / confirm / favourites | see Flutter hotel APIs | | | HotelRepository |

Payment WebView finishes on `/payment/status` or success/fail URL hints, then `payment/verify`. Pusher: public `trip.{id}` with `cart.item.locked|booked|released`.

Mock: Tour & Entertainment → `MockExperienceRepository`.
