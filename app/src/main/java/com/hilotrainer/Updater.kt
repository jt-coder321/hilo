package com.hilotrainer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * In-app updates from GitHub. The repository's "updates" branch holds latest.json
 * (version, notes, APK link and its SHA-256) and the newest APK. The app reads the
 * manifest, downloads the APK, checks its fingerprint, then opens Android's installer.
 * Android always asks you to confirm the install.
 */
object Updater {
    const val REPO = "jt-coder321/hilo"
    const val MANIFEST = "https://raw.githubusercontent.com/$REPO/updates/latest.json"

    data class Release(val versionCode: Long, val versionName: String, val apkUrl: String, val sha256: String, val notes: String, val sizeBytes: Long)

    fun parse(json: String): Release {
        val o = JSONObject(json)
        return Release(
            versionCode = o.getLong("versionCode"),
            versionName = o.getString("versionName"),
            apkUrl = o.getString("apkUrl"),
            sha256 = o.optString("sha256", "").lowercase(),
            notes = o.optString("notes", ""),
            sizeBytes = o.optLong("sizeBytes", 0),
        )
    }

    fun installed(ctx: Context): Pair<Long, String> {
        val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()
        return code to (p.versionName ?: "?")
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000; readTimeout = 30000
        instanceFollowRedirects = true
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("User-Agent", "HiLoTrainer-updater")
    }

    /** The newest release on GitHub. Throws on network trouble. */
    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val c = open("$MANIFEST?t=${System.currentTimeMillis()}")
        try {
            if (c.responseCode != 200) throw IllegalStateException("GitHub answered ${c.responseCode}")
            parse(c.inputStream.bufferedReader().readText())
        } finally { c.disconnect() }
    }

    /** Downloads the APK into the app's cache, reporting progress from 0 to 1, and checks its SHA-256. */
    suspend fun download(ctx: Context, r: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, "HiLoTrainer-update.apk")
        val c = open(r.apkUrl)
        try {
            if (c.responseCode != 200) throw IllegalStateException("Download failed (${c.responseCode})")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.sizeBytes
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            c.inputStream.use { inp ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) progress((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (r.sha256.isNotEmpty() && hex != r.sha256) { out.delete(); throw IllegalStateException("The download was damaged. Try again.") }
            out
        } finally { c.disconnect() }
    }

    /** True when Android lets this app open the installer. */
    fun canInstall(ctx: Context): Boolean = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Opens the screen where you allow Hi-Lo Trainer to install updates. */
    fun askPermission(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Hands the APK to Android's installer. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
