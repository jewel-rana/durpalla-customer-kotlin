package com.durpalla.booking.security

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.durpalla.booking.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Android equivalent of the Flutter `g_recaptcha_v3` flow: the same reCAPTCHA **v3 web** site key
 * (RECAPTCHA_SITE_KEY from the Flutter .env) is executed in a hidden WebView hosted on
 * RECAPTCHA_ORIGIN, and the token is posted as `recaptchaToken` with action "login".
 *
 * If the token can't be obtained (no network, domain not allow-listed for the key) we send "" —
 * exactly what the Flutter app sends on Android, where g_recaptcha_v3 is web-only and returns null.
 * To move to the native Android SDK (com.google.android.recaptcha) a separate *Android* key is needed.
 */
interface RecaptchaProvider { suspend fun token(action: String): String }

class WebViewRecaptchaProvider(private val context: Context) : RecaptchaProvider {
    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun token(action: String): String {
        val key = BuildConfig.RECAPTCHA_SITE_KEY
        if (key.isBlank()) return ""
        return withTimeoutOrNull(8_000) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val wv = WebView(context.applicationContext)
                    wv.settings.javaScriptEnabled = true
                    wv.addJavascriptInterface(object {
                        @JavascriptInterface fun onToken(t: String?) {
                            if (cont.isActive) cont.resume(t.orEmpty())
                            wv.post { wv.destroy() }
                        }
                    }, "Bridge")
                    val html = """
                        <html><head><script src="https://www.google.com/recaptcha/api.js?render=$key"></script></head><body><script>
                        function run(){ try { grecaptcha.ready(function(){ grecaptcha.execute('$key',{action:'$action'})
                          .then(function(t){Bridge.onToken(t)}, function(){Bridge.onToken('')}); }); } catch(e){ Bridge.onToken(''); } }
                        window.onload = run;
                        </script></body></html>""".trimIndent()
                    wv.loadDataWithBaseURL(BuildConfig.RECAPTCHA_ORIGIN, html, "text/html", "utf-8", null)
                    cont.invokeOnCancellation { wv.post { wv.destroy() } }
                }
            }
        } ?: ""
    }
}
