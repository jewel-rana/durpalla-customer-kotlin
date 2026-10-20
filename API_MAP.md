# API map — Flutter → Kotlin

Base URL: `BuildConfig.BASE_URL` = `https://dev-apigw.durpalla.com/api/v1/` (debug) / `https://apigw.durpalla.com/api/v1/` (release; Flutter `.env` API_BASE_URL).
Auth endpoints use the same base (Flutter `.env` sets `API_AUTH_BASE_URL` = API_BASE_URL).

Common headers (ApiClient interceptor, from `ApiService._defaultHeaders`): `Accept: application/json`, `Cache-Control: no-cache, no-store, must-revalidate`, `Pragma: no-cache`, `Expires: 0`, `Authorization: Bearer <token>` when signed in.
Response handling (from `ApiService._decode`): non-JSON → `{success:false, message:"Invalid server response"}`; HTTP 401 → `success:false, unauthorized:true` and forced logout if a token existed; `success` defaults to `status == true`. No token refresh exists in the Flutter app (401 = logout), so none is implemented.

| Feature | Method & path | Body / query | Flutter source | Kotlin |
|---|---|---|---|---|
| Account step | POST `auth/check` (form) | mobile, recaptchaToken | auth_api.dart `checkStep` | AuthRepository.checkStep |
| Send OTP | POST `auth/otp/send` (form) | mobile, recaptchaToken | `sendOtp` | sendOtp |
| Verify OTP | POST `auth/verify` (form) | mobile, otp, type=register, recaptchaToken → token | `verifyOtp` | verifyOtp |
| Password login | POST `auth/login` (form) | mobile, password, device_id, device_platform, fcm_token, recaptchaToken | `login` | login |
| Register | POST `auth/register` (form) | name, email, mobile, password, confirm_password, otp_code, device_*, fcm_token, recaptchaToken | `register` | register |
| Forgot pw | POST `auth/forgot`, `auth/verify` (type=forgot), `auth/reset` (JSON) | | forgotPassword* | forgotStart/Verify/Reset (no UI yet) |
| Push bind | POST `auth/push/bind` (JSON) | token, platform | `bindPushToken` | bindPushToken |
| App config | GET `public/app-config` | | home_screen | HomeRepository.appConfig |
| Popular trips | GET `public/popular-upcoming-trips` | | home_screen | popularTrips |
| Top hotels | GET `hotel/home/top` | limit | home_screen | topHotels |
| Offers | GET `offers` | | home_screen | offers |
| Search trips | GET `search` | type (launch/bus/boat), trip_from, trip_to, trip_date=YYYY-MM-DD | search/kind_*.dart | TripRepository.search |
| Ghat suggestions | GET `trip/ghat-suggestions` | query, exclude, type (+ local fallback list) | `fetchGhatSuggestions` | ghatSuggestions |
| City suggestions | GET `city/suggestion` | q | `fetchCitySuggestions` | citySuggestions |
| Trip layout | GET `trip/{id}` | → cabins/seats/sofas.{first_floor..}.{row:[items]}, floors[], stoppages[] | trip_details_screen | tripDetails |
| Lock item | POST `cart/add` (form) | trip_id, item_id, type + `X-Idempotency-Key`, `X-Socket-Id` | `lockItem` | lockItem |
| Release lock | POST `cart/unlock` (JSON) | lock_id, item_id | `releaseLock` | releaseLock |
| Cart | GET `cart` | | cart_provider | cart (not used by UI; cart kept in memory like Flutter) |
| Confirm booking | POST `booking/confirm` (JSON) | items[{item_id,type,price,lock_id,meta,for_self,passengers}], amount | checkout_screen | BookingRepository.confirm |
| Gateways | GET `gateway` | booking_id | payment_options_screen | PaymentRepository.gateways |
| Start payment | POST `payment/make` (JSON) | order_id, booking_id, gateway_id, amount → paymentURL/redirect_url/… | payment_options_screen + PaymentNavigator | make |
| Verify payment | GET `payment/verify` | booking_id | global_payment_page | verify |
| My bookings | GET `my/bookings` | page | my_bookings_screen | myBookings |
| Booking details | GET `my/booking/android/{id}` | | my_booking_details_screen | bookingDetails |
| Cancel item | POST `booking/item/{itemId}/cancel` | | my_bookings_screen | cancelItem |
| Profile | GET `my/profile`; POST `my/profile` multipart `_method=PUT` | name, email, phone, avatar | api_service | ProfileRepository |
| Hotel search | GET `hotel/search` | city, check_in, check_out, adults | `hotelSearch` | HotelRepository.search |
| Hotel details/rooms | GET `hotel/{id}`, GET `hotel/{id}/rooms` | check_in, check_out, adults | | details / rooms |
| Hotel quote/hold/confirm | POST `hotel/quote`, POST `hotel/hold` (`Idempotency-Key`), POST `hotel/booking/confirm` {hold_id}; DELETE `hotel/hold/{id}` | | | quote/hold/confirm/releaseHold |
| Hotel review | POST `hotel/{id}/reviews` | rating, text | | review (no UI) |
| Favourites | GET/POST `my/favourite/hotels`, DELETE `my/favourite/hotels/{id}` | hotel_id | home_screen | favourites/add/remove |

Payment WebView (port of global_payment_page.dart): finishes when the URL path contains `/payment/status`, or matches success hints (`/success`, `status=success`, `payment_status=success`, `paid`, `complete`) or fail hints (`/failed`, `/cancel`, `status=failed`, `declined`); then always calls `payment/verify?booking_id=`. `intent://` and non-http schemes open externally (with `browser_fallback_url` fallback). Back asks "Cancel payment?".

Realtime (Pusher): key `aed7edc1e0e3afd30d65`, cluster `ap2`; public channel `trip.{tripId}`; events `cart.item.locked`, `cart.item.booked`, `cart.item.released` with `{item_id, data:{cabin_no, fare}}`. No private channels / auth endpoint in the Flutter app.

Mock (no backend in Flutter app): Tour & Entertainment listing/details/booking → `MockExperienceRepository` behind `ExperienceRepository`.
Not in the Flutter app's API: coupons, notifications inbox, CMS terms page (marked TODO).

Probed on 2026-10-20 (no credentials): `public/app-config` ✔ `{success, data:{version, sections}}`; `public/popular-upcoming-trips` ✔ `data:[]`; `offers` ✔ `data:[]`; `trip/ghat-suggestions?query=dh` ✔ `[{name,id}]`; `search?type=launch…` ✔ `data:[]`; `hotel/home/top` ✔ hotel objects (id, hotel_id, name, city, photo, stars, price_per_night, amenities…); `my/bookings` → `{"success":false,"message":"Unauthenticated."}`.
