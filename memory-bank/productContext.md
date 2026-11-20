# Product context

Durpalla is a Bangladesh travel booking app: **launch / bus / boat / hotel / tour / entertainment**, plus boat rental with driver bids.

**Users:** Guests browse and search; signed-in customers book, pay, manage tickets, call the trip driver.

**Key flows**
1. Splash → optional language onboarding → Home (guest or signed-in).
2. Search → Results → Trip details (seat map + Pusher locks) → Checkout → Payment WebView → Ticket.
3. Hotels / experiences / boat rental → hold → confirm → payment.
4. Auth: email **or** mobile (`channel`); check → password | OTP | register; forgot = BD mobile only.
5. Calling: customer ↔ trip driver; audio via **WebRTC or Agora** (server `provider`); ring via FCM (+ Pusher secondary).

**UX notes:** Brand primary `#106E9D`, amber CTA; EN/BN bundled + remote packs; Bangla digits for durations when locale is bn; guest mode with auth sheet for protected routes.
