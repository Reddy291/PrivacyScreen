package com.privacyscreen.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.input.InputManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps the privacy layer on screen above every app until the user turns it off.
 */
class PrivacyService : LifecycleService() {

    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager
    private var overlay: PrivacyOverlayView? = null
    private var params: WindowManager.LayoutParams? = null

    private var faceWatcher: FaceWatcher? = null
    private var cameraInForeground = false
    private var strangerFrames = 0
    private var clearSince = 0L

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.KEY_FACE_GUARD) {
            goForeground()
            syncFaceGuard()
        }
        applySettings()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            // "Turn off" tapped in the notification (sent with startService, so no
            // startForeground call is required here)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        if (overlay == null) {
            addOverlay()
            prefs.raw.registerOnSharedPreferenceChangeListener(prefListener)
        }
        applySettings()
        syncFaceGuard()

        _running.value = true
        refreshTile()
        return START_STICKY
    }

    override fun onDestroy() {
        prefs.raw.unregisterOnSharedPreferenceChangeListener(prefListener)
        faceWatcher?.close()
        faceWatcher = null
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        _running.value = false
        refreshTile()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Phone rotated: resize the layer to the new full-screen size
        val p = params ?: return
        val size = fullScreenSize()
        p.width = size.x
        p.height = size.y
        overlay?.let { runCatching { wm.updateViewLayout(it, p) } }
    }

    // ---------------------------------------------------------------- foreground

    /** Returns false if Android refused to let us run in the foreground. */
    private fun goForeground(): Boolean {
        val wantCamera = prefs.faceGuard && hasCamera()
        return try {
            startForegroundWithType(wantCamera)
            cameraInForeground = wantCamera
            true
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed (camera=$wantCamera)", e)
            if (!wantCamera) return false
            // Usually: started from the tile while the app was in the background,
            // and Android 14+ doesn't allow starting the camera from there.
            try {
                startForegroundWithType(false)
                cameraInForeground = false
                Toast.makeText(this, R.string.face_paused, Toast.LENGTH_LONG).show()
                true
            } catch (e2: Exception) {
                Log.w(TAG, "startForeground failed", e2)
                false
            }
        }
    }

    private fun startForegroundWithType(camera: Boolean) {
        var type = 0
        if (Build.VERSION.SDK_INT >= 34) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (camera && Build.VERSION.SDK_INT >= 30) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(camera), type)
    }

    private fun buildNotification(camera: Boolean): Notification {
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, PrivacyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(if (camera) R.string.notif_text_face else R.string.notif_text))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.turn_off), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        )
    }

    // ------------------------------------------------------------------- overlay

    private fun addOverlay() {
        val size = fullScreenSize()
        val p = WindowManager.LayoutParams(
            size.x, size.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= 28) {
                // also cover the camera-cutout / notch area
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val v = PrivacyOverlayView(this)
        wm.addView(v, p)
        overlay = v
        params = p
    }

    private fun applySettings() {
        val v = overlay ?: return
        val p = params ?: return
        v.mode = prefs.mode
        v.spotCenter = prefs.spotPosition / 100f
        v.spotHeight = prefs.spotHeight / 100f

        // Android 12+ only lets touches pass through a layer that is at most ~80% opaque.
        // During a face-guard blackout we go fully black (touches are blocked, on purpose).
        val wanted = if (v.blackout) 1f else prefs.darkness / 100f
        p.alpha = if (v.blackout) 1f else wanted.coerceAtMost(maxPassThroughAlpha())
        runCatching { wm.updateViewLayout(v, p) }
    }

    private fun maxPassThroughAlpha(): Float =
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
        } else 1f

    @Suppress("DEPRECATION")
    private fun fullScreenSize(): Point {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            Point().also { wm.defaultDisplay.getRealSize(it) }
        }
    }

    // ---------------------------------------------------------------- face guard

    private fun hasCamera() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun syncFaceGuard() {
        val shouldWatch = prefs.faceGuard && cameraInForeground && overlay != null
        if (shouldWatch && faceWatcher == null) {
            faceWatcher = FaceWatcher(this, this,
                onFaces = ::onFaceCount,
                onError = { Log.w(TAG, "Face guard unavailable", it) }
            ).also { it.start() }
        } else if (!shouldWatch && faceWatcher != null) {
            faceWatcher?.close()
            faceWatcher = null
            setBlackout(false)
        }
    }

    /** Called on the main thread with the number of faces in front of the phone. */
    private fun onFaceCount(count: Int) {
        val now = SystemClock.elapsedRealtime()
        if (count >= 2) {
            clearSince = 0L
            strangerFrames++
            // 2 frames in a row (~0.5 s) avoids flicker from a false detection
            if (strangerFrames >= 2) setBlackout(true)
        } else {
            strangerFrames = 0
            if (overlay?.blackout == true) {
                if (clearSince == 0L) clearSince = now
                // stay hidden until the onlooker has been gone for 1.5 s
                if (now - clearSince >= 1500) setBlackout(false)
            }
        }
    }

    private fun setBlackout(on: Boolean) {
        val v = overlay ?: return
        if (v.blackout == on) return
        v.blackout = on
        if (!on) clearSince = 0L
        applySettings()
    }

    private fun refreshTile() {
        runCatching {
            TileService.requestListeningState(this,
                ComponentName(this, PrivacyTileService::class.java))
        }
    }

    companion object {
        private const val TAG = "PrivacyService"
        private const val CHANNEL_ID = "privacy_screen"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "com.privacyscreen.app.STOP"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PrivacyService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PrivacyService::class.java))
        }
    }
}
