package com.durpalla.booking.ui.nav

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.*
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*
import com.durpalla.booking.AppContainer
import com.durpalla.booking.R
import com.durpalla.booking.ui.components.GradientButton
import com.durpalla.booking.ui.screens.*
import kotlinx.coroutines.flow.collectLatest

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("no container") }

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"; const val SEARCH = "search"; const val TICKETS = "tickets"; const val OFFERS = "offers"; const val ACCOUNT = "account"
    const val LOGIN = "login?redirect={redirect}"
    fun login(redirect: String?) = "login?redirect=" + Uri.encode(redirect ?: "")
    fun otp(id: com.durpalla.booking.core.AuthIdentifier, redirect: String) =
        "otp/${Uri.encode(id.encode())}?redirect=" + Uri.encode(redirect)
    fun password(id: com.durpalla.booking.core.AuthIdentifier, redirect: String) =
        "password/${Uri.encode(id.encode())}?redirect=" + Uri.encode(redirect)
    fun register(id: com.durpalla.booking.core.AuthIdentifier, otp: String, redirect: String) =
        "register/${Uri.encode(id.encode())}/${Uri.encode(otp)}?redirect=" + Uri.encode(redirect)
    /** Forgot is BD-mobile only (Flutter E5) — never pass an email. */
    fun forgot(mobile: String = "") = "forgot?mobile=" + Uri.encode(mobile)
    fun forgotOtp(mobile: String) = "forgototp/${Uri.encode(mobile)}"
    fun forgotReset(mobile: String) = "forgotreset/${Uri.encode(mobile)}"
    fun searchFor(service: String) = "search?service=$service"
    fun results(type: String, from: String, to: String, date: String) = "results/$type/${Uri.encode(from)}/${Uri.encode(to)}/$date"
    fun trip(id: Int, type: String) = "trip/$id/$type"
    const val CHECKOUT = "checkout"
    fun payment(bookingId: Int, amount: Double) = "payment/$bookingId/$amount"
    fun payWeb(url: String, bookingId: Int) = "payweb/$bookingId?url=" + Uri.encode(url)
    fun payResult(bookingId: Int, cancelled: Boolean = false) = "payresult/$bookingId/$cancelled"
    fun ticket(id: Int) = "ticket/$id"
    fun hotelResults(city: String, ci: String, co: String) = "hotels/${Uri.encode(city)}/$ci/$co"
    fun hotel(id: Int, ci: String, co: String) = "hotel/$id/$ci/$co"
    fun experiences(service: String) = "exp/$service"
    fun experience(id: Int, service: String = "tour") = "expd/$service/$id"
    fun bookExperience(id: Int, service: String = "tour") = "expbook/$service/$id"
    fun boatList(q: String = "", starts: String = "", ends: String = "") =
        "boats/${Uri.encode(q.ifBlank { "_" })}/${Uri.encode(starts.ifBlank { "_" })}/${Uri.encode(ends.ifBlank { "_" })}"
    fun boatDetails(id: Int, starts: String, ends: String) =
        "boat/$id/${Uri.encode(starts)}/${Uri.encode(ends)}"
    fun boatCustomTrip() = "boatcustom"
    const val PROFILE = "profile"; const val SAVED = "saved"; const val SUPPORT = "support"; const val FAQ = "faq"
    const val ABOUT = "about"; const val TERMS = "terms"; const val SETTINGS = "settings"; const val NOTIFICATIONS = "notifications"

    /** Pages that need a signed-in customer (checkout, payment, tickets, personal pages). */
    private val protectedPrefixes = listOf(TICKETS, CHECKOUT, "payment/", "payweb/", "payresult/", "ticket/", PROFILE, SAVED, "expbook/", NOTIFICATIONS)
    fun isProtected(route: String) = protectedPrefixes.any { route == it || route.startsWith(it) }
}

class AppNavigator(val nav: NavHostController, private val loggedIn: () -> Boolean, private val askAuth: (String) -> Unit) {
    /** Navigate; protected destinations prompt the Sign-in-required sheet for guests. */
    fun go(route: String) { if (Routes.isProtected(route) && !loggedIn()) askAuth(route) else nav.navigate(route) }
    fun back() { nav.popBackStack() }
    /** For in-page actions (book hotel, save, coupon): returns true if signed in, else shows the sheet and returns to [returnTo]. */
    fun ensureAuth(returnTo: String): Boolean { if (loggedIn()) return true; askAuth(returnTo); return false }
    fun tab(route: String) {
        if (Routes.isProtected(route) && !loggedIn()) { askAuth(route); return }
        nav.navigate(route) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true }
    }
    /** After sign-in, drop the auth screens and land on the intended page. */
    fun finishAuth(redirect: String) {
        nav.popBackStack(Routes.LOGIN, inclusive = true)
        if (redirect.isNotBlank()) { if (redirect in tabs.map { it.route }) tab(redirect) else nav.navigate(redirect) }
    }
}

val LocalNav = staticCompositionLocalOf<AppNavigator> { error("no nav") }

data class Tab(val route: String, val label: Int, val icon: ImageVector, val selIcon: ImageVector)
val tabs = listOf(
    Tab(Routes.HOME, R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.SEARCH, R.string.tab_search, Icons.Outlined.Search, Icons.Filled.Search),
    Tab(Routes.TICKETS, R.string.tab_tickets, Icons.Outlined.ConfirmationNumber, Icons.Filled.ConfirmationNumber),
    Tab(Routes.OFFERS, R.string.tab_offers, Icons.Outlined.LocalOffer, Icons.Filled.LocalOffer),
    Tab(Routes.ACCOUNT, R.string.tab_account, Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(container: AppContainer, deepLink: String?) {
    val nav = rememberNavController()
    val loggedIn by container.session.loggedIn.collectAsState()
    var pendingRoute by remember { mutableStateOf<String?>(null) }
    val navigator = remember(nav) { AppNavigator(nav, { container.session.isAuthenticated }, { pendingRoute = it }) }

    LaunchedEffect(Unit) {
        container.session.forcedLogout.collectLatest { nav.navigate(Routes.HOME) { popUpTo(0) } }
    }
    LaunchedEffect(deepLink, loggedIn) { if (deepLink != null && nav.currentDestination?.route != Routes.SPLASH) navigator.go(deepLink) }

    CompositionLocalProvider(LocalContainer provides container, LocalNav provides navigator) {
        val back by nav.currentBackStackEntryAsState()
        val current = back?.destination?.route
        Scaffold(
            bottomBar = {
                AnimatedVisibility(current in tabs.map { it.route } || current?.startsWith("search") == true,
                    enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
                        tabs.forEach { t ->
                            val sel = back?.destination?.hierarchy?.any { it.route?.substringBefore("?") == t.route } == true
                            NavigationBarItem(selected = sel, onClick = { navigator.tab(t.route) },
                                icon = { Icon(if (sel) t.selIcon else t.icon, null) },
                                label = { Text(stringResource(t.label)) })
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets(0),
        ) { pad ->
            NavHost(nav, startDestination = Routes.SPLASH, modifier = Modifier.padding(pad),
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(320)) + fadeIn(tween(320)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(250)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) + fadeOut(tween(300)) },
            ) {
                composable(Routes.SPLASH) { SplashScreen { nav.navigate(Routes.HOME) { popUpTo(Routes.SPLASH) { inclusive = true } } } }
                composable(Routes.HOME, enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(180)) }) { HomeScreen() }
                composable("search?service={service}", arguments = listOf(navArgument("service") { defaultValue = "launch" }),
                    enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(180)) }) { SearchScreen(it.arguments?.getString("service") ?: "launch") }
                composable(Routes.TICKETS, enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(180)) }) { TicketsScreen() }
                composable(Routes.OFFERS, enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(180)) }) { OffersScreen() }
                composable(Routes.ACCOUNT, enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(180)) }) { AccountScreen() }

                val redirectArg = navArgument("redirect") { defaultValue = "" }
                composable(Routes.LOGIN, arguments = listOf(redirectArg)) { LoginScreen(it.arguments?.getString("redirect").orEmpty()) }
                composable("otp/{id}?redirect={redirect}", arguments = listOf(redirectArg)) {
                    OtpScreen(it.arguments!!.getString("id")!!, it.arguments?.getString("redirect").orEmpty())
                }
                composable("password/{id}?redirect={redirect}", arguments = listOf(redirectArg)) {
                    PasswordScreen(it.arguments!!.getString("id")!!, it.arguments?.getString("redirect").orEmpty())
                }
                composable("register/{id}/{otp}?redirect={redirect}", arguments = listOf(redirectArg)) {
                    RegisterScreen(
                        it.arguments!!.getString("id")!!,
                        it.arguments!!.getString("otp")!!,
                        it.arguments?.getString("redirect").orEmpty(),
                    )
                }
                composable(
                    "forgot?mobile={mobile}",
                    arguments = listOf(navArgument("mobile") { defaultValue = "" }),
                ) { ForgotStartScreen(it.arguments?.getString("mobile").orEmpty()) }
                composable("forgototp/{mobile}") { ForgotOtpScreen(it.arguments!!.getString("mobile")!!) }
                composable("forgotreset/{mobile}") { ForgotResetScreen(it.arguments!!.getString("mobile")!!) }

                composable("results/{type}/{from}/{to}/{date}") { e ->
                    val a = e.arguments!!
                    ResultsScreen(a.getString("type")!!, a.getString("from")!!, a.getString("to")!!, a.getString("date")!!)
                }
                composable("trip/{id}/{type}", arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                    TripDetailsScreen(it.arguments!!.getInt("id"), it.arguments!!.getString("type")!!)
                }
                composable(Routes.CHECKOUT) { CheckoutScreen() }
                composable("payment/{bid}/{amount}", arguments = listOf(navArgument("bid") { type = NavType.IntType }, navArgument("amount") { type = NavType.FloatType })) {
                    PaymentMethodsScreen(it.arguments!!.getInt("bid"), it.arguments!!.getFloat("amount").toDouble())
                }
                composable("payweb/{bid}?url={url}", arguments = listOf(navArgument("bid") { type = NavType.IntType }, navArgument("url") { defaultValue = "" })) {
                    PaymentWebViewScreen(it.arguments!!.getString("url")!!, it.arguments!!.getInt("bid"))
                }
                composable("payresult/{bid}/{cancelled}", arguments = listOf(navArgument("bid") { type = NavType.IntType }, navArgument("cancelled") { type = NavType.BoolType })) {
                    PaymentResultScreen(it.arguments!!.getInt("bid"), it.arguments!!.getBoolean("cancelled"))
                }
                composable("ticket/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { TicketScreen(it.arguments!!.getInt("id")) }

                composable("hotels/{city}/{ci}/{co}") { e -> val a = e.arguments!!; HotelResultsScreen(a.getString("city")!!, a.getString("ci")!!, a.getString("co")!!) }
                composable("hotel/{id}/{ci}/{co}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { e ->
                    val a = e.arguments!!; HotelDetailsScreen(a.getInt("id"), a.getString("ci")!!, a.getString("co")!!)
                }
                composable("exp/{service}") { ExperienceListScreen(it.arguments!!.getString("service")!!) }
                composable(
                    "expd/{service}/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.IntType }),
                ) {
                    ExperienceDetailScreen(it.arguments!!.getInt("id"), it.arguments!!.getString("service")!!)
                }
                composable(
                    "expbook/{service}/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.IntType }),
                ) {
                    ExperienceBookScreen(it.arguments!!.getInt("id"), it.arguments!!.getString("service")!!)
                }
                composable("boats/{q}/{starts}/{ends}") {
                    fun decode(s: String?) = Uri.decode(s.orEmpty()).let { if (it == "_") "" else it }
                    BoatListScreen(
                        decode(it.arguments?.getString("q")),
                        decode(it.arguments?.getString("starts")),
                        decode(it.arguments?.getString("ends")),
                    )
                }
                composable("boat/{id}/{starts}/{ends}", arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                    BoatDetailsScreen(
                        it.arguments!!.getInt("id"),
                        it.arguments!!.getString("starts")!!,
                        it.arguments!!.getString("ends")!!,
                    )
                }
                composable("boatcustom") { BoatCustomTripScreen() }

                composable(Routes.PROFILE) { ProfileScreen() }
                composable(Routes.SAVED) { SavedScreen() }
                composable(Routes.SUPPORT) { InfoScreen(InfoKind.SUPPORT) }
                composable(Routes.FAQ) { InfoScreen(InfoKind.FAQ) }
                composable(Routes.ABOUT) { InfoScreen(InfoKind.ABOUT) }
                composable(Routes.TERMS) { InfoScreen(InfoKind.TERMS) }
                composable(Routes.NOTIFICATIONS) { InfoScreen(InfoKind.NOTIFICATIONS) }
                composable(Routes.SETTINGS) { SettingsScreen() }
            }
        }

        pendingRoute?.let { target ->
            ModalBottomSheet(onDismissRequest = { pendingRoute = null }, containerColor = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.logo_mark), null, Modifier.size(64.dp), tint = androidx.compose.ui.graphics.Color.Unspecified)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.sign_in_required), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.sign_in_required_body), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    GradientButton(stringResource(R.string.sign_in), Modifier.fillMaxWidth()) {
                        pendingRoute = null; nav.navigate(Routes.login(target))
                    }
                    TextButton(onClick = { pendingRoute = null }) { Text(stringResource(R.string.not_now)) }
                }
            }
        }
    }
}
