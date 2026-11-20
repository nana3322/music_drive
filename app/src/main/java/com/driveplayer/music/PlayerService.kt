package com.driveplayer.music

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

/** 재생한 곡 저장: 최대 1GB, 30일. 1GB를 넘으면 가장 오래전에 들은 곡부터 지운다(LRU). */
object CacheManager {
    private const val MAX_BYTES = 1024L * 1024L * 1024L
    private const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
    private var cache: SimpleCache? = null

    @Synchronized
    fun get(ctx: Context): SimpleCache {
        cache?.let { return it }
        val app = ctx.applicationContext
        val c = SimpleCache(
            File(app.cacheDir, "audio"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(app)
        )
        cache = c
        return c
    }

    fun factory(ctx: Context): CacheDataSource.Factory {
        // 곡을 받을 때도 앱 정보(패키지 이름·서명 지문)를 같이 보낸다
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setDefaultRequestProperties(AppSig.headers(ctx.applicationContext))
        return CacheDataSource.Factory()
            .setCache(get(ctx))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(ctx.applicationContext, http))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** 30일 넘게 안 들은 곡 삭제 */
    fun purgeOld(ctx: Context) {
        val c = get(ctx)
        val limit = System.currentTimeMillis() - KEEP_MS
        for (key in c.keys.toList()) {
            val newest = c.getCachedSpans(key).maxOfOrNull { it.lastTouchTimestamp } ?: 0L
            if (newest < limit) c.removeResource(key)
        }
    }

    fun used(ctx: Context): Long = get(ctx).cacheSpace

    fun clear(ctx: Context) {
        val c = get(ctx)
        for (key in c.keys.toList()) c.removeResource(key)
    }

    const val MAX = MAX_BYTES
}

class PlayerService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timerRun = Runnable {
        session?.player?.pause()
        publishTimerEnd(0L)
    }
    private var errorStreak = 0

    override fun onCreate() {
        super.onCreate()
        // 곡 전체를 미리 받아 캐시에 넣도록 버퍼를 넉넉히(최대 10분) 잡는다
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 600_000, 2_500, 5_000)
            .build()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(CacheManager.factory(this)))
            .setLoadControl(load)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // 재생 못 하는 곡(드라이브에서 지워짐, 오프라인에서 저장 안 된 곡)은 건너뛴다
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) errorStreak = 0
            }

            override fun onPlayerError(error: PlaybackException) {
                errorStreak++
                if (player.mediaItemCount > 1 && errorStreak <= player.mediaItemCount && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })

        session = MediaSession.Builder(this, player).setCallback(SessionCallback()).build()
        setMediaNotificationProvider(DismissibleProvider(this))
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_TIMER, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_TIMER) {
                setTimer(args.getInt("min", 0))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }

    /** 취침·수업 타이머: 정한 시간이 지나면 재생을 멈춘다(앱 화면을 꺼도 동작) */
    private fun setTimer(min: Int) {
        handler.removeCallbacks(timerRun)
        if (min <= 0) {
            publishTimerEnd(0L)
        } else {
            handler.postDelayed(timerRun, min * 60_000L)
            publishTimerEnd(System.currentTimeMillis() + min * 60_000L)
        }
    }

    private fun publishTimerEnd(t: Long) {
        session?.setSessionExtras(Bundle().apply { putLong("timerEnd", t) })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // 알림을 옆으로 밀어 지우면: 재생 멈추고 서비스 완전 종료
            handler.removeCallbacks(timerRun)
            session?.player?.let {
                it.pause()
                it.stop()
                it.clearMediaItems()
            }
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerRun)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.driveplayer.music.STOP"
        const val CMD_TIMER = "com.driveplayer.music.TIMER"
    }
}

/** 기본 미디어 알림에 '밀어서 지우기(deleteIntent)'를 붙이고 고정(ongoing)을 푼다 */
class DismissibleProvider(private val ctx: Context) : MediaNotification.Provider {
    private val base = DefaultMediaNotificationProvider(ctx)

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val mn = base.createNotification(mediaSession, customLayout, actionFactory, onNotificationChangedCallback)
        val n = mn.notification
        n.deleteIntent = PendingIntent.getService(
            ctx, 99,
            Intent(ctx, PlayerService::class.java).setAction(PlayerService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        n.flags = n.flags and Notification.FLAG_ONGOING_EVENT.inv() and Notification.FLAG_NO_CLEAR.inv()
        return mn
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        base.handleCustomCommand(session, action, extras)
}
