package com.hilotrainer.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import com.hilotrainer.engine.Extra
import com.hilotrainer.engine.Hair
import com.hilotrainer.engine.Hat
import com.hilotrainer.engine.Look
import org.json.JSONObject
import kotlin.math.abs

/**
 * The 3D table. A WebView draws a lit 3D scene (three.js, bundled in assets so it works offline)
 * behind the Compose cards and labels. If WebGL is missing or the web view dies, [failed] flips
 * and the table falls back to the flat Compose version.
 */
object Table3D {
    /** Tests switch this off so screenshots never need a web view. */
    var enabled = true
    var failed by mutableStateOf(false)
    var why by mutableStateOf("")
    /** Tests draw a still picture here instead of a web view. */
    var stub: (@Composable (Modifier) -> Unit)? = null

    private fun hex(c: Color) = String.format("#%06X", c.toArgb() and 0xFFFFFF)
    private fun hexL(v: Long) = String.format("#%06X", v.toInt() and 0xFFFFFF)
    private val EYES = listOf("#3a2a1c", "#4a7f9a", "#3a5a3a", "#5a3a22", "#6a7a8a")

    fun person(look: Look, name: String): JSONObject {
        val fem = look.hairStyle in listOf(Hair.LONG, Hair.BUN, Hair.PONYTAIL, Hair.BOB)
        val style = when (look.hairStyle) {
            Hair.LONG, Hair.PONYTAIL -> "long"
            Hair.BOB -> "bob"
            Hair.SLICK -> "slick"
            Hair.BALD -> "none"
            Hair.BUZZ -> "crop"
            else -> "side"
        }
        return JSONObject()
            .put("skin", hexL(look.skin)).put("hair", hexL(look.hair)).put("suit", hexL(look.shirt)).put("tie", hexL(look.shirtTrim))
            .put("eye", EYES[abs(name.hashCode()) % EYES.size]).put("style", style)
            .put("lip", if (fem) "#a8504f" else "#b06a62")
            .put("glasses", Extra.GLASSES in look.extras || Extra.SUNGLASSES in look.extras)
            .put("shades", Extra.SUNGLASSES in look.extras)
            .put("beard", Extra.BEARD in look.extras)
            .put("hat", if (look.hat == Hat.FEDORA) "fedora" else "")
    }

    fun stage(v: Venue, dealer: RunDealer, seats: List<com.hilotrainer.engine.CastMember>, boss: Boolean, motion: Boolean, quality: Int): String =
        JSONObject()
            .put("bg", hex(v.bg)).put("base", hex(v.base)).put("light", hex(v.light)).put("accent", hex(v.print))
            .put("boss", boss).put("motion", motion).put("q", quality)
            .put("dealer", person(dealer.look, dealer.name))
            .put("bots", org.json.JSONArray().put(person(seats[0].look, seats[0].name)).put(person(seats[1].look, seats[1].name)))
            .toString()
}

private class StageHolder { var ready = false; var drawn = false; var last: String? = null; var web: WebView? = null; var frame by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun Table3DLayer(json: String, onFail: (String) -> Unit, modifier: Modifier = Modifier) {
    Table3D.stub?.let { it(modifier); return }
    val holder = remember { StageHolder() }
    holder.last = json
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var waited = 0
        while (!holder.drawn && waited < 8000) { kotlinx.coroutines.delay(250); waited += 250 }
        if (!holder.drawn) { onFail("no picture after 8 s (page loaded: ${holder.ready})"); return@LaunchedEffect }
        // The page drew. Some phones show a web view as black, so we show a captured picture instead.
        var w2 = 0
        while (holder.frame == null && w2 < 4000) { kotlinx.coroutines.delay(250); w2 += 250 }
        if (holder.frame == null) onFail("3D drew but no picture came back")
    }
    DisposableEffect(Unit) { onDispose { holder.web?.let { it.stopLoading(); it.destroy() }; holder.web = null } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                holder.web = this
                setBackgroundColor(0xFF0A0614.toInt())
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false; overScrollMode = android.view.View.OVER_SCROLL_NEVER
                isClickable = false; isFocusable = false
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false; settings.allowContentAccess = false
                addJavascriptInterface(object {
                    @JavascriptInterface fun fail(why: String) { Handler(Looper.getMainLooper()).post { onFail(why) } }
                    @JavascriptInterface fun ready() {}
                    @JavascriptInterface fun drawn() { holder.drawn = true }
                    @JavascriptInterface fun frame(dataUrl: String) {
                        Thread {
                            try {
                                val b64 = dataUrl.substringAfter(',')
                                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bmp != null) Handler(Looper.getMainLooper()).post { holder.frame = bmp.asImageBitmap() }
                            } catch (e: Throwable) { Handler(Looper.getMainLooper()).post { onFail("picture decode failed") } }
                        }.start()
                    }
                }, "Android")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) { holder.ready = true; holder.last?.let { push(view, it) } }
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean { onFail("web view process died"); return true }
                    override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) { onFail("load error " + error.description + " " + request.url.lastPathSegment) }
                }
                loadUrl("file:///android_asset/table3d/stage.html")
            }
        },
        update = { wv -> if (holder.ready) holder.last?.let { push(wv, it) } },
    )
    holder.frame?.let { f ->
        androidx.compose.foundation.Image(f, null, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}

private fun push(wv: WebView, json: String) {
    wv.evaluateJavascript("window.setStage(" + JSONObject.quote(json) + ")", null)
}
