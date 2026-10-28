package com.jizhang.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.pm.PackageInfoCompat
import com.jizhang.BuildConfig
import com.jizhang.JizhangApp
import com.jizhang.domain.AppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException

/** A failure with a message fit to show as is; [retryable] false when trying again can't help. */
class UpdateException(message: String, val retryable: Boolean = true) : Exception(message)

/** The newest GitHub release: tag "v<versionName>", Chinese markdown notes, one signed APK. */
data class Release(
    /** Tag without the leading "v", e.g. "0.5.1". */
    val version: String,
    val title: String,
    /** Markdown, as written on GitHub; see [com.jizhang.domain.ReleaseNotes.plain]. */
    val notes: String,
    val pageUrl: String,
    val publishedAt: String,
    /** Empty when the release has no .apk asset (yet). */
    val apkUrl: String,
    val apkSize: Long,
)

/**
 * The only network code in the app: asks GitHub for the latest release of [BuildConfig.UPDATE_REPO],
 * downloads its APK into the cache, checks that it really is an update of this app and hands it
 * to the system installer. State and sequencing live in [UpdateController].
 */
class Updater(private val context: Context) {
    val repo: String = BuildConfig.UPDATE_REPO.trim()

    /** False for a blank or malformed repo; then nothing here touches the network. */
    val configured: Boolean = REPO.matches(repo)

    private val dir get() = File(context.cacheDir, "updates")

    /** Sessions this app abandoned itself; the "aborted" they report back is not the owner's doing. */
    private val abandoned: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    fun isOwnAbandon(sessionId: Int) = sessionId in abandoned

    fun fileFor(release: Release) = File(dir, "jizhang-${release.version.filter { it.isLetterOrDigit() || it in "._-" }}.apk")

    // Check -------------------------------------------------------------------------------------

    suspend fun fetchLatest(): Release = withContext(Dispatchers.IO) {
        val conn = connect("https://api.github.com/repos/$repo/releases/latest", "application/vnd.github+json")
        try {
            when (val code = conn.responseCode) {
                200 -> parseRelease(conn.inputStream.bufferedReader().use { it.readText() })
                404 -> throw UpdateException("还没有发布版本")
                403, 429 -> throw UpdateException("检查太频繁了，请过一会儿再试")
                else -> throw UpdateException("服务器返回了 $code，请稍后再试")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRelease(json: String): Release {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) throw UpdateException("还没有发布版本")
        val tag = o.text("tag_name")
        if (tag.isBlank()) throw UpdateException("更新信息不完整")
        var url = ""
        var size = 0L
        val assets = o.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val link = a.text("browser_download_url")
                if (a.text("name").endsWith(".apk", ignoreCase = true) && link.startsWith("https://")) {
                    url = link
                    size = a.optLong("size")
                    break
                }
            }
        }
        return Release(
            version = AppVersion.clean(tag),
            title = o.text("name").ifBlank { tag },
            notes = o.text("body"),
            pageUrl = o.text("html_url"),
            publishedAt = o.text("published_at"),
            apkUrl = url,
            apkSize = size,
        )
    }

    /** Android's optString turns a JSON null into the text "null". */
    private fun JSONObject.text(name: String) = if (isNull(name)) "" else optString(name)

    /** Deletes downloaded APKs, except [keep] (null = all). */
    fun prune(keep: File? = null) {
        dir.listFiles()?.forEach { if (it != keep) it.delete() }
    }

    // Download ----------------------------------------------------------------------------------

    /**
     * Downloads [release]'s APK and returns the file. A complete earlier download is reused.
     * [onProgress] runs on the IO thread with (bytes so far, total or 0 if unknown).
     */
    suspend fun download(release: Release, onProgress: (done: Long, total: Long) -> Unit): File = withContext(Dispatchers.IO) {
        val target = fileFor(release)
        prune(keep = target)
        if (target.isFile && target.length() > 0 && (release.apkSize <= 0 || target.length() == release.apkSize)) {
            onProgress(target.length(), target.length())
            return@withContext target
        }
        dir.mkdirs()
        val part = File(dir, target.name + ".part")
        part.delete()
        var finished = false
        try {
            coroutineScope {
                val conn = connect(release.apkUrl, "application/octet-stream", identity = true)
                // A blocked read can't be interrupted; closing the connection from the side ends it.
                val closer = launch(Dispatchers.Unconfined) {
                    try {
                        awaitCancellation()
                    } finally {
                        conn.disconnect()
                    }
                }
                try {
                    if (conn.responseCode != 200) throw UpdateException("下载失败（服务器返回 ${conn.responseCode}）")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                    var done = 0L
                    var emitted = 0L
                    conn.inputStream.use { input ->
                        part.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                done += n
                                ensureActive()
                                val now = SystemClock.elapsedRealtime()
                                if (now - emitted >= 80) {
                                    emitted = now
                                    onProgress(done, total)
                                }
                            }
                        }
                    }
                    if (total > 0 && done != total) throw UpdateException("下载不完整，请重试")
                    onProgress(done, done)
                } catch (e: IOException) {
                    ensureActive() // closed on purpose: surface the cancellation, not the socket error
                    throw e
                } finally {
                    closer.cancel()
                    conn.disconnect()
                }
            }
            target.delete()
            if (!part.renameTo(target)) throw UpdateException("安装包保存失败，请检查手机存储空间")
            finished = true
            target
        } finally {
            if (!finished) part.delete()
        }
    }

    /** Opens [url] and follows redirects by hand (GitHub hands downloads to another host), https only. */
    private fun connect(url: String, accept: String, identity: Boolean = false): HttpURLConnection {
        var next = url
        repeat(MAX_REDIRECTS + 1) {
            if (!next.startsWith("https://")) throw UpdateException("更新地址不安全，已拒绝")
            val conn = URL(next).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "jizhang")
            conn.setRequestProperty("Accept", accept)
            // Raw bytes, so the length GitHub reports is the length of the file.
            if (identity) conn.setRequestProperty("Accept-Encoding", "identity")
            val code = conn.responseCode
            if (code !in 301..308 || code == 304) return conn
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (location.isNullOrBlank()) throw UpdateException("更新地址无法打开")
            next = URL(URL(next), location).toString()
        }
        throw UpdateException("更新地址跳转次数过多")
    }

    // Verify and install ------------------------------------------------------------------------

    /**
     * Refuses anything that isn't a newer build of this app signed with the same key as the one
     * installed: Android would reject a mismatched update anyway, but only after the owner has
     * been through the confirmation, and a stranger's APK shouldn't get that far.
     */
    fun verify(file: File) {
        val pm = context.packageManager
        val archive = archiveInfo(pm, file.path) ?: throw UpdateException("安装包已损坏，请重新下载")
        if (archive.packageName != context.packageName) {
            throw UpdateException("这个安装包不是「记账」的，已拒绝安装", retryable = false)
        }
        val installed = installedInfo(pm)
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            throw UpdateException("安装包的版本不比现在这个新，已拒绝安装", retryable = false)
        }
        val theirs = signers(archive)
        if (theirs.isEmpty() || theirs != signers(installed)) {
            throw UpdateException("安装包的签名和手机上的「记账」不一致，已拒绝安装（硬装会丢失账本数据）", retryable = false)
        }
    }

    /** Android 9's archive parser only reads certificates when GET_SIGNATURES is asked for too. */
    @Suppress("DEPRECATION")
    private fun signatureFlags() =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? {
        val flags = signatureFlags()
        return try {
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
            else pm.getPackageArchiveInfo(path, flags)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager): PackageInfo {
        val flags = signatureFlags()
        return try {
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            else pm.getPackageInfo(context.packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
            throw UpdateException("读不到当前安装的版本信息", retryable = false)
        }
    }

    /** SHA-256 of each current signing certificate (hex). */
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val current: List<Signature> = if (Build.VERSION.SDK_INT >= 28) {
            val si = info.signingInfo
            when {
                si == null -> emptyList()
                si.hasMultipleSigners() -> si.apkContentsSigners.toList()
                else -> listOfNotNull(si.signingCertificateHistory?.lastOrNull())
            }
        } else {
            emptyList()
        }
        val sigs = current.ifEmpty { info.signatures?.toList().orEmpty() }
        val sha = MessageDigest.getInstance("SHA-256")
        return sigs.mapTo(HashSet()) { s -> sha.digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The 允许安装未知应用 page of this app's settings; the caller starts it from an Activity. */
    fun installSettingsIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * Streams [file] into a PackageInstaller session and commits it. The outcome arrives later at
     * [InstallResultReceiver]. Once this app is the installer of record Android 12+ can update it
     * without asking; until then the system shows its usual confirmation.
     */
    suspend fun install(file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        // Sessions from an earlier attempt would only pile up. Abandoning a committed one makes the
        // system report it as aborted; [isOwnAbandon] lets that pass unnoticed.
        installer.mySessions.forEach {
            abandoned += it.sessionId
            runCatching { installer.abandonSession(it.sessionId) }
        }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("jizhang.apk", 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                session.commit(resultSender())
            }
        } catch (e: Exception) {
            abandoned += id
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    /** The result is filled in by the system, so the PendingIntent has to be mutable on Android 12+. */
    private fun resultSender(): IntentSender {
        val intent = Intent(context, InstallResultReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 0, intent, flags).intentSender
    }

    companion object {
        private val REPO = Regex("""[A-Za-z0-9._-]+/[A-Za-z0-9._-]+""")
        private const val TIMEOUT_MS = 10_000
        private const val MAX_REDIRECTS = 5

        /** A message for the owner from whatever went wrong. */
        fun describe(e: Throwable): String = when (e) {
            is UpdateException -> e.message.orEmpty()
            is UnknownHostException, is SocketTimeoutException, is java.net.ConnectException ->
                "连不上网络，请检查网络后再试"
            is SSLException -> "安全连接失败，请检查手机的日期和网络"
            is IOException -> "网络出错了，请稍后再试"
            is JSONException -> "更新信息读取失败"
            is SecurityException -> "系统不允许安装，请到设置里允许「记账」安装应用"
            else -> "出错了：${e.message ?: e.javaClass.simpleName}"
        }

        /** What a failed PackageInstaller status means for the owner. */
        fun installFailure(status: Int, systemMessage: String?): String = when (status) {
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "安装包和已装的「记账」不兼容（签名或版本不符）"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "和手机上的「记账」冲突，请稍后再试"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "手机存储空间不足，清理后再试"
            PackageInstaller.STATUS_FAILURE_INVALID -> "安装包无效，请重新下载"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "系统阻止了这次安装，请检查「安装未知应用」的权限"
            else -> "安装没有成功" + (systemMessage?.takeIf { it.isNotBlank() }?.let { "：$it" } ?: "")
        }
    }
}

/** Receives the package installer's verdict on an update the owner started in 我的. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        (context.applicationContext as JizhangApp).container.updater.onInstallStatus(status, message, confirm, sessionId)
    }
}
