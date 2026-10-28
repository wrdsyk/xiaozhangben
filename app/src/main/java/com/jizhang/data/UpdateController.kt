package com.jizhang.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.SystemClock
import com.jizhang.BuildConfig
import com.jizhang.domain.AppVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val done: Long, val total: Long) : UpdateState {
        /** 0 while the size is unknown. */
        val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    /** Downloaded and checked; waiting for 允许安装未知应用 when [needsPermission]. */
    data class ReadyToInstall(val release: Release, val needsPermission: Boolean = false) : UpdateState

    /** Handed to the system installer; [needsAction] while its confirmation is still to be answered. */
    data class Installing(val release: Release, val needsAction: Boolean = false) : UpdateState
    data class Failed(val message: String, val release: Release?, val retryable: Boolean = true) : UpdateState
}

/** One-off things the UI has to react to, queued until a screen is there to see them. */
sealed interface UpdateEvent {
    /** Open the update sheet for [release]. */
    data class NewVersion(val release: Release) : UpdateEvent
    data object UpToDate : UpdateEvent
    data object NotConfigured : UpdateEvent
    data class CheckFailed(val message: String) : UpdateEvent

    /** Send the owner to the 允许安装未知应用 page; the check resumes when they come back. */
    data object GrantInstall : UpdateEvent
}

/**
 * Check → download → install, as a state machine that outlives screens (it lives in
 * [com.jizhang.AppContainer]), so a download carries on while the sheet is closed. Everything
 * changes state on the main thread. Offline is normal: a failed automatic check stays silent.
 */
class UpdateController(private val context: Context, private val settings: SettingsRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val updater = Updater(context)

    /** False when no update source was built in; then nothing here goes online. */
    val configured: Boolean get() = updater.configured

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val prefs: StateFlow<UpdatePrefs> = settings.updatePrefs.stateIn(scope, SharingStarted.Eagerly, UpdatePrefs())

    private val _events = Channel<UpdateEvent>(Channel.BUFFERED)
    val events: Flow<UpdateEvent> = _events.receiveAsFlow()

    private var download: Job? = null

    /** When (elapsedRealtime) an automatic check last started, so coming back to the app doesn't retry offline over and over. */
    private var lastAutoAttempt = 0L

    /** A manual check asked for while an automatic one was already running; it reports back too. */
    private var reportResult = false

    /** The installer's own confirmation screen, kept in case it couldn't be shown right away. */
    private var pendingConfirm: Intent? = null

    val installSettingsIntent: Intent get() = updater.installSettingsIntent()

    private fun emit(e: UpdateEvent) {
        _events.trySend(e)
    }

    // Check -------------------------------------------------------------------------------------

    /**
     * When the app opens or comes back: checks if auto-check is on and the last successful check is
     * a day old. At most one attempt per half hour, so being offline costs nothing; a skipped
     * version stays quiet.
     */
    fun autoCheckIfDue() {
        if (!configured) return
        val now = SystemClock.elapsedRealtime()
        if (lastAutoAttempt != 0L && now - lastAutoAttempt < RETRY_MS) return
        lastAutoAttempt = now
        scope.launch {
            val p = settings.updatePrefs.first()
            val age = System.currentTimeMillis() - p.lastCheckAt
            // A negative age means the clock went back; check rather than wait for it to catch up.
            if (p.autoCheck && age !in 0 until DAY_MS) check(manual = false)
        }
    }

    /** [manual] checks always report back (already latest, error); automatic ones only speak up for news. */
    fun check(manual: Boolean) {
        if (!configured) {
            if (manual) emit(UpdateEvent.NotConfigured)
            return
        }
        val known = when (val s = _state.value) {
            UpdateState.Checking -> {
                if (manual) reportResult = true
                return
            }
            is UpdateState.Available -> s.release
            is UpdateState.Downloading -> s.release
            is UpdateState.ReadyToInstall -> s.release
            is UpdateState.Installing -> s.release
            is UpdateState.Failed -> s.release
            else -> null
        }
        // Already found one (or halfway through it): no need to ask GitHub again.
        if (known != null) {
            if (manual) emit(UpdateEvent.NewVersion(known))
            return
        }
        reportResult = manual
        _state.value = UpdateState.Checking
        scope.launch {
            try {
                val release = updater.fetchLatest()
                settings.markUpdateChecked(System.currentTimeMillis())
                val report = reportResult
                when {
                    !AppVersion.isNewer(release.version, BuildConfig.VERSION_NAME) -> {
                        withContext(Dispatchers.IO) { updater.prune() }
                        _state.value = UpdateState.UpToDate
                        if (report) emit(UpdateEvent.UpToDate)
                    }
                    release.apkUrl.isEmpty() -> {
                        _state.value = UpdateState.Idle
                        if (report) emit(UpdateEvent.CheckFailed("新版本 v${release.version} 还没有上传安装包，过一会儿再试"))
                    }
                    else -> {
                        _state.value = UpdateState.Available(release)
                        val skipped = settings.updatePrefs.first().skippedVersion == release.version
                        if (report || !skipped) emit(UpdateEvent.NewVersion(release))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Idle
                if (reportResult) emit(UpdateEvent.CheckFailed(Updater.describe(e)))
            }
        }
    }

    fun setAutoCheck(on: Boolean) {
        scope.launch { settings.setAutoCheckUpdates(on) }
    }

    /** 跳过这个版本: the automatic prompt stays quiet about [release] (a manual check still shows it). */
    fun skip(release: Release) {
        scope.launch { settings.skipUpdateVersion(release.version) }
        if ((_state.value as? UpdateState.Available)?.release == release) _state.value = UpdateState.Idle
    }

    // Download and install ----------------------------------------------------------------------

    /** 立即更新 (also 重试 and 安装): downloads, checks and installs, as far as the owner's answers allow. */
    fun startUpdate() {
        val release = when (val s = _state.value) {
            is UpdateState.Available -> s.release
            is UpdateState.Failed -> s.release ?: return
            is UpdateState.ReadyToInstall -> {
                install()
                return
            }
            else -> return
        }
        download?.cancel()
        download = scope.launch {
            _state.value = UpdateState.Downloading(release, 0, release.apkSize)
            try {
                val file = updater.download(release) { done, total ->
                    _state.update { if (it is UpdateState.Downloading && it.release == release) UpdateState.Downloading(release, done, total) else it }
                }
                withContext(Dispatchers.IO) {
                    try {
                        updater.verify(file)
                    } catch (e: UpdateException) {
                        // A bad file would only be reused by the retry.
                        file.delete()
                        throw e
                    }
                }
                _state.value = UpdateState.ReadyToInstall(release)
                install()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(Updater.describe(e), release, retryable = (e as? UpdateException)?.retryable != false)
            }
        }
    }

    /** 关闭 on a failure: back to the offer if trying again can help, else forget it. */
    fun dismissFailure() {
        val s = _state.value as? UpdateState.Failed ?: return
        _state.value = if (s.release != null && s.retryable) UpdateState.Available(s.release) else UpdateState.Idle
    }

    fun cancelDownload() {
        val s = _state.value as? UpdateState.Downloading ?: return
        download?.cancel()
        download = null
        _state.value = UpdateState.Available(s.release)
    }

    private fun install() {
        val s = _state.value as? UpdateState.ReadyToInstall ?: return
        val file = updater.fileFor(s.release)
        if (!file.isFile) {
            _state.value = UpdateState.Failed("安装包不见了，请重新下载", s.release)
            return
        }
        if (!updater.canInstall()) {
            _state.value = s.copy(needsPermission = true)
            emit(UpdateEvent.GrantInstall)
            return
        }
        _state.value = UpdateState.Installing(s.release)
        scope.launch {
            try {
                updater.install(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(Updater.describe(e), s.release)
            }
        }
    }

    /** Back from the 允许安装未知应用 page: carry on if it was granted. */
    fun onResume() {
        val s = _state.value
        if (s is UpdateState.ReadyToInstall && s.needsPermission && updater.canInstall()) install()
    }

    /** 继续安装: bring the system's confirmation back, or start the install over if it's gone. */
    fun resumeInstall() {
        val s = _state.value as? UpdateState.Installing ?: return
        if (pendingConfirm != null) {
            launchConfirm()
        } else {
            _state.value = UpdateState.ReadyToInstall(s.release)
            install()
        }
    }

    /** The package installer's verdict, from [InstallResultReceiver]. */
    fun onInstallStatus(status: Int, message: String?, confirm: Intent?, sessionId: Int = -1) {
        // A session we abandoned to start a fresh one reports "aborted"; that must not undo the new attempt.
        if (updater.isOwnAbandon(sessionId)) return
        val release = when (val s = _state.value) {
            is UpdateState.Installing -> s.release
            is UpdateState.ReadyToInstall -> s.release
            is UpdateState.Failed -> s.release
            else -> null
        }
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                pendingConfirm = confirm
                if (release != null) _state.value = UpdateState.Installing(release, needsAction = true)
                launchConfirm()
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // Usually the process is replaced before this is seen.
                pendingConfirm = null
                _state.value = UpdateState.Idle
                scope.launch(Dispatchers.IO) { updater.prune() }
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                pendingConfirm = null
                _state.value = if (release != null) UpdateState.ReadyToInstall(release) else UpdateState.Idle
            }
            else -> {
                pendingConfirm = null
                _state.value = UpdateState.Failed(
                    Updater.installFailure(status, message), release,
                    retryable = status != PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                )
            }
        }
    }

    /** From the background Android may refuse to open it; 继续安装 in the sheet tries again. */
    private fun launchConfirm() {
        val intent = pendingConfirm ?: return
        try {
            context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val RETRY_MS = 30L * 60 * 1000
    }
}
