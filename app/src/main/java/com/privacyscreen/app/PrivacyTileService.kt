package com.privacyscreen.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/**
 * "Privacy screen" button in the Quick Settings panel (swipe down from the top).
 */
class PrivacyTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile(PrivacyService.running.value)
    }

    override fun onClick() {
        super.onClick()
        if (PrivacyService.running.value) {
            PrivacyService.stop(this)
            updateTile(false)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            openApp(turnOn = false) // permission is set up from inside the app
            return
        }
        try {
            PrivacyService.start(this)
            updateTile(true)
        } catch (e: Exception) {
            // Some Android versions block starting from the background;
            // open the app, which then turns the privacy screen on.
            Log.w("PrivacyTile", "Direct start blocked, opening app", e)
            openApp(turnOn = true)
        }
    }

    private fun updateTile(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (on) "On" else "Off"
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp(turnOn: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_TURN_ON, turnOn)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, 3, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
