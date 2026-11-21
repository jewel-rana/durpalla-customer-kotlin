# Active context — durpalla-customer-kotlin

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
