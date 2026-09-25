package hu.elmdash.media

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Build
import android.os.Process
import android.service.media.MediaBrowserService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import hu.elmdash.connection.DashboardGraph
import hu.elmdash.connection.ObdService
import hu.elmdash.connection.AutoObd
import hu.elmdash.graphics.DashboardTiles
import kotlinx.coroutines.*

/** Real radio playback plus an experimental OBD metadata/artwork surface.
 * Media state follows the actual player; pausing radio never stops OBD collection.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class DashboardMediaService : MediaBrowserService() {
    private val controller by lazy { DashboardGraph.get(this) }
    private val radio by lazy { RadioGraph.get(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var mediaSession: MediaSession
    private lateinit var player: ExoPlayer
    private var page = MediaPage.ENGINE
    private var published: MediaFrame? = null
    private var publishedRadio: RadioState? = null
    private var publishedSubtitle: String? = null
    private val subtitleStartedAt = android.os.SystemClock.elapsedRealtime()
    private var artwork: Bitmap? = null
    private var foreground = false
    private var error: String? = null
    private lateinit var recovery: RadioRecovery
    private lateinit var network: RadioNetwork
    private var carOwned = false
    internal val wantsPlayback: Boolean get() = ::recovery.isInitialized && recovery.wanted
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Internetes rádió", NotificationManager.IMPORTANCE_LOW))
        mediaSession = MediaSession(this, "ELM-Dashboard-Media")
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (::recovery.isInitialized && !destroyed) recovery.playerState(player.isPlaying,
                        player.playbackState == Player.STATE_BUFFERING,
                        player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                        player.playbackState == Player.STATE_ENDED)
                }
                override fun onPlayerError(e: PlaybackException) {
                    if (::recovery.isInitialized && !destroyed) recovery.failure(RadioErrors.retryable(e))
                }
                override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                    android.util.Log.i("ELMRadio", "playWhenReady=$ready reason=$reason")
                    if (!ready && ::recovery.isInitialized && recovery.wanted && !destroyed && reason in setOf(
                            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
                            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) pauseRadio()
                }
            })
        }
        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { playRadio(radio.state.value.selected.id) }
            override fun onPause() { pauseRadio() }
            override fun onStop() { pauseRadio(stop = true) }
            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                val station = radio.state.value.stations.firstOrNull { RadioStore.matches(it.name, query.orEmpty()) }
                if (station != null) playRadio(station.id)
            }
            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) { select(mediaId) }
            override fun onSkipToNext() { changeStation(1) }
            override fun onSkipToPrevious() { changeStation(-1) }
            override fun onCustomAction(action: String, extras: Bundle?) { select(action) }
        })
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            mediaSession.setSessionActivity(PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        network = RadioNetwork(this) { available ->
            scope.launch { if (!destroyed) recovery.networkChanged(available) }
        }
        recovery = RadioRecovery(scope, network.online, connect = {
            // Replace the source to rejoin a live stream instead of replaying an old buffer.
            player.setMediaItem(MediaItem.fromUri(radio.state.value.selected.url))
            player.prepare()
            player.play()
        }, changed = { syncPlayback() })
        network.start()
        AutoRadio.service = this
        sessionToken = mediaSession.sessionToken
        mediaSession.isActive = true
        publish(force = true)
        scope.launch { while (isActive) { delay(2_000); publish() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.i("ELMRadio", "command=${intent?.action}")
        when (intent?.action) {
            ACTION_PLAY -> playRadio(intent.getStringExtra("station") ?: radio.state.value.selected.id)
            ACTION_AUTO_PLAY -> {
                if (AutoRadio.connected && radio.state.value.autoPlayOnAa && !radio.pausedForAa && !wantsPlayback)
                    playRadio(radio.state.value.selected.id, automatic = true)
                else if (!foreground) stopSelf(startId)
            }
            ACTION_PAUSE -> pauseRadio()
            ACTION_STOP -> pauseRadio(stop = true)
            ACTION_NEXT -> changeStation(1)
            ACTION_PREVIOUS -> changeStation(-1)
        }
        return START_NOT_STICKY
    }

    private fun playRadio(id: String, automatic: Boolean = false) {
        val station = radio.state.value.stations.firstOrNull { it.id == id } ?: radio.state.value.selected
        try {
            error = null
            if (!automatic) AutoRadio.explicitPlay()
            carOwned = automatic || (carOwned && AutoRadio.connected)
            radio.select(station.id)
            // Foreground status must precede audio focus on Android 15+.
            enterForeground()
            startService(Intent(this, DashboardMediaService::class.java).setAction(ACTION_KEEP))
            player.stop() // Release the previous station even if the next one must wait for internet.
            recovery.start()
        } catch (_: Exception) {
            recovery.pause(); player.stop()
            error = "A rádió nem indítható. Ellenőrizd a háttérengedélyt, majd indítsd újra."
            syncPlayback()
        }
    }

    private fun pauseRadio(stop: Boolean = false, userInitiated: Boolean = true) {
        android.util.Log.i("ELMRadio", "pause stop=$stop userInitiated=$userInitiated")
        if (userInitiated) AutoRadio.manualPause()
        recovery.pause() // Cancel timers before player callbacks can arrive.
        if (stop) player.stop() else player.pause()
        error = null
        syncPlayback()
    }

    internal fun endCarPlayback() {
        if (carOwned) { carOwned = false; pauseRadio(stop = true, userInitiated = false) }
    }

    private fun syncPlayback() {
        if (destroyed || !::mediaSession.isInitialized || !::recovery.isInitialized) return
        error = recovery.error ?: error
        val pending = recovery.wanted && !player.isPlaying
        radio.status(player.isPlaying, pending, error, recovery.message)
        // Network loss is waiting, not a user pause: keep the service and network callback alive.
        if (!recovery.wanted) {
            if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false }
            stopSelf()
        }
        publish(force = true)
    }

    private fun enterForeground() {
        val notification = notification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION_ID, notification)
        foreground = true
    }

    private fun notification(): Notification {
        val pause = PendingIntent.getService(this, 41, Intent(this, DashboardMediaService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(radio.state.value.selected.name).setContentText(radio.state.value.recoveryMessage ?: "ELM Dash • internetes rádió")
            .setContentIntent(mediaSession.controller.sessionActivity).setOnlyAlertOnce(true)
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0))
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, "Rádió szünet", pause).build())
            .build()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        if (!MediaBrowserAccess.allowed(clientPackageName, clientUid, Process.myUid(), packageManager.getPackagesForUid(clientUid))) return null
        return BrowserRoot(ROOT, Bundle().apply {
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
        })
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        val s = controller.state.value
        val frame = MediaDashboard.frame(s)
        val items = mutableListOf<MediaBrowser.MediaItem>()
        fun add(id: String, title: String, subtitle: String, browsable: Boolean = false, image: Bitmap? = null) {
            items.add(MediaBrowser.MediaItem(MediaDescription.Builder().setMediaId(id).setTitle(title).setSubtitle(subtitle)
                .setIconBitmap(image).build(), if (browsable) MediaBrowser.MediaItem.FLAG_BROWSABLE else MediaBrowser.MediaItem.FLAG_PLAYABLE))
        }
        when (parentId) {
            ROOT -> {
                add(FAVORITES, "Kedvenc rádiók", "${radio.state.value.favorites.size} mentett állomás", true)
                add(RADIOS, "Rádióállomások", radio.state.value.selected.name, true)
                add(PAGES, "Műszerek / fogyasztás", frame.title, true)
                add(if (s.active) STOP else DEMO, if (s.active) "OBD / demó leállítása" else "Demó indítása",
                    if (s.active) "A rádió tovább szól" else "Szimulált adatok, adapter nélkül")
            }
            RADIOS, FAVORITES -> radio.state.value.stations.filter { parentId != FAVORITES || it.id in radio.state.value.favorites }.forEach { add("radio:${it.id}", it.name, "Internetes rádió • lejátszás") }
            PAGES -> MediaPage.entries.forEach { item ->
                val image = if (item == MediaPage.ENGINE) DashboardTiles.compactOverview(s)
                    else DashboardTiles.indicator(s, if (item == MediaPage.FUEL) 2 else 3)
                add(item.id, item.label, frame.subtitles[item.ordinal], image = Bitmap.createScaledBitmap(image, 128, 128, true))
            }
        }
        result.sendResult(items)
    }

    private fun select(id: String?) {
        when {
            id?.startsWith("radio:") == true -> { playRadio(id.removePrefix("radio:")); return }
            id == DEMO -> if (!controller.state.value.active) {
                AutoObd.pause(this); stopService(Intent(this, ObdService::class.java)); controller.startDemo()
            }
            id == STOP -> {
                AutoObd.pause(this); controller.stop(); stopService(Intent(this, ObdService::class.java))
            }
            id == PAGE -> page = MediaPage.entries[(page.ordinal + 1) % MediaPage.entries.size]
            else -> page = MediaPage.fromId(id)
        }
        publish(force = true)
    }

    private fun changeStation(delta: Int) {
        val state = radio.state.value
        val index = state.stations.indexOf(state.selected)
        playRadio(state.stations[(index + delta + state.stations.size) % state.stations.size].id)
    }

    private fun publish(force: Boolean = false) {
        val s = controller.state.value
        val frame = MediaDashboard.frame(s)
        val rs = radio.state.value
        val subtitle = MediaDashboard.subtitle(frame, page, rs, android.os.SystemClock.elapsedRealtime() - subtitleStartedAt)
        if (!force && frame == published && rs == publishedRadio && subtitle == publishedSubtitle) return
        if (artwork == null || frame != published) artwork = DashboardTiles.compactOverview(s)
        mediaSession.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "radio:${rs.selected.id}")
            .putString(MediaMetadata.METADATA_KEY_TITLE, frame.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, frame.status)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, frame.title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, error ?: rs.recoveryMessage ?: frame.status)
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork).build())
        mediaSession.setPlaybackState(playbackState(frame.active, rs.playing, rs.buffering, error))
        if (foreground) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        val stationsChanged = publishedRadio?.stations != rs.stations
        published = frame; publishedRadio = rs; publishedSubtitle = subtitle
        notifyChildrenChanged(ROOT); notifyChildrenChanged(PAGES)
        if (stationsChanged) notifyChildrenChanged(RADIOS)
        notifyChildrenChanged(FAVORITES)
    }

    override fun onDestroy() {
        destroyed = true
        if (AutoRadio.service === this) AutoRadio.service = null
        network.close()
        recovery.close()
        scope.cancel()
        player.release()
        radio.status(false, false, error)
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        internal const val ROOT = "dashboard"
        internal const val RADIOS = "radios"
        internal const val FAVORITES = "favorites"
        internal const val PAGES = "pages"
        internal const val DEMO = "elmdash.demo"
        internal const val STOP = "elmdash.stop"
        internal const val PAGE = "elmdash.page"
        internal const val ACTION_AUTO_PLAY = "hu.elmdash.radio.AUTO_PLAY"
        const val ACTION_PLAY = "hu.elmdash.radio.PLAY"
        const val ACTION_PAUSE = "hu.elmdash.radio.PAUSE"
        const val ACTION_STOP = "hu.elmdash.radio.STOP"
        const val ACTION_NEXT = "hu.elmdash.radio.NEXT"
        const val ACTION_PREVIOUS = "hu.elmdash.radio.PREVIOUS"
        private const val ACTION_KEEP = "hu.elmdash.radio.KEEP"
        private const val CHANNEL = "radio"
        private const val NOTIFICATION_ID = 328
        internal fun playbackState(active: Boolean, playing: Boolean = false, buffering: Boolean = false, error: String? = null): PlaybackState {
            val state = when { error != null -> PlaybackState.STATE_ERROR; playing -> PlaybackState.STATE_PLAYING;
                buffering -> PlaybackState.STATE_BUFFERING; else -> PlaybackState.STATE_PAUSED }
            return PlaybackState.Builder().setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f)
                .setErrorMessage(error)
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_FROM_SEARCH or PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or
                    PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP)
                .addCustomAction(PlaybackState.CustomAction.Builder(PAGE, "Műszerlap váltása", R.drawable.ic_page_data).build())
                .addCustomAction(PlaybackState.CustomAction.Builder(if (active) STOP else DEMO,
                    if (active) "OBD / demó leállítása" else "Demó indítása",
                    if (active) R.drawable.ic_stop_data else R.drawable.ic_demo_data).build()).build()
        }
    }
}
