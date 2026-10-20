# Code map (reuse before inventing)

Package root: `app/src/main/java/com/durpalla/booking/`

## App shell
| What | Where |
|---|---|
| Application + FCM/Pusher boot | `DurpallaApp.kt` |
| Manual DI | `AppContainer.kt` |
| Entry / theme host | `MainActivity.kt` |

## Screens (`ui/screens/`)
| Feature | File | Reuse |
|---|---|---|
| Splash | `SplashScreen.kt` | — |
| Language onboarding | `LanguageSelectScreen.kt` | `SupportedLocales` |
| Home | `HomeScreen.kt` | SoftCard, SectionHeader, PhotoBox |
| Search | `SearchScreen.kt` | PhoneInput patterns N/A; ghat suggest via trips repo |
| Results | `ResultsScreen.kt` | Skeleton, SoftCard, money() |
| Trip + seats + Pusher | `TripDetailsScreen.kt` | CallDriverButton, CallLogSection |
| Checkout / payment / result | `PurchaseScreens.kt` | GradientButton, SoftCard |
| Tickets / ticket detail | `TicketScreens.kt` | CallDriverButton, CallLogSection |
| Hotels / tours / events | `HotelExperienceScreens.kt` | SoftCard, PhotoBox |
| Boat list/detail/bids | `BoatScreens.kt` | CallDriverButton, CallLogSection |
| Auth (login/OTP/password/register/forgot) | `AuthScreens.kt` | PhoneInput, GradientButton |
| Account / settings / support / FAQ / about / terms / offers / notifications / profile / saved | `MoreScreens.kt` | SoftCard, Menu patterns in-file |
| Call UI | `CallScreens.kt` | CallDriverButton, CallLogSection, Outgoing/Incoming |
| Shared load helpers | `Common.kt` | **`rememberLoad`**, `LoadVm`, `todayIso`, `prettyDate` |

## Components (`ui/components/`)
`SoftCard`, `GradientButton`, `PrimaryButton`, `DTopBar`, `FrostedIcon`, `Pill`, `SectionHeader`, `PhotoBox`, `Skeleton`, `LoadingList`, `EmptyState`, `ErrorState`, `money()`, `LogoHorizontal` — `Components.kt`  
`PhoneInput` — `PhoneInput.kt`

## Nav / theme
| What | Where |
|---|---|
| Routes, AppNavigator, bottom nav, auth sheet | `ui/nav/AppNav.kt` |
| Brand colors, DurpallaTheme | `ui/theme/Theme.kt` |

## Data
| What | Where |
|---|---|
| HTTP client / headers / decode | `data/net/ApiClient.kt`, `DurpallaApi.kt` |
| Session / token / locale prefs | `data/session/SessionStore.kt` |
| Auth | `data/repo/AuthRepository.kt` |
| Home, Trip, Booking, Payment, Hotel, Profile | `data/repo/Repositories.kt` |
| Tour, Entertainment, BoatRental | `data/repo/VerticalRepositories.kt` |
| Experiences facade | `data/repo/ExperienceRepository.kt` |
| Cart | `data/repo/CartStore.kt` |
| Domain models | `data/repo/Models.kt` |
| Remote Config gateways | `data/remote/RemoteAppConfig.kt` |

## Calling (`calling/`)
| What | Where |
|---|---|
| Interface | `CallRepository.kt` |
| Prod / mock | `ApiCallRepository.kt`, `MockCallRepository.kt` |
| Models / timeouts / `CallProvider` / callLog.`provider` | `CallModels.kt` (client omit → webrtc) |
| **Media transports** | `CallTransport.kt`, `WebRtcTransport.kt`, `AgoraTransport.kt`, `MockCallTransport.kt` |
| Agora token REST | `AgoraTokenClient.kt` (`GET` → `{data:{appId,channel,token,uid,expiresAt}}`, seconds) |
| Firestore signaling | `FirestoreSignalingRepository.kt`, `SignalingRepository.kt` |
| WebRTC engine + Opus / ICE restart | `WebRtcEngine.kt`, `OpusSdpMunger.kt` |
| ICE REST + cache | `IceServersClient.kt` |
| Firebase custom token | `FirebaseCallAuth.kt` |
| API error → l10n | `CallApiErrors.kt` |
| FCM/Pusher payload parse | `IncomingCallPayload.kt` (includes `provider`) |
| Notifications / FGS / incoming UI | `CallNotifications.kt`, `CallForegroundService.kt`, `IncomingCallActivity.kt`, `CallActionReceiver.kt` |

## Cross-cutting
| What | Where |
|---|---|
| Loose JSON helpers | `core/Json.kt` |
| AppCtx.str | `core/AppCtx.kt` |
| Auth validators / phone / identifier | `core/Auth*.kt`, `PhoneNumbers.kt` |
| Cancel rules, MediaUrl, BanglaDigits, ApiUserMessage | `core/*` |
| `t()` + remote overlay | `l10n/L10n.kt`, `RemoteLocalizationService.kt`, `SupportedLocales.kt`, `LocaleHelper` |
| Pusher (+ `private-user-{id}`) | `realtime/PusherManager.kt` |
| FCM | `push/DurpallaMessagingService.kt`, `Notifications.kt` |
| reCAPTCHA WebView | `security/RecaptchaProvider.kt` |

## Tests
`app/src/test/.../calling/OpusSdpMungerTest.kt`
