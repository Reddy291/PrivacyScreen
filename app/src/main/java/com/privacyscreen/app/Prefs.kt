package com.privacyscreen.app

import android.content.Context
import android.content.SharedPreferences

/** All user settings, stored on the phone only. */
class Prefs(context: Context) {

    val raw: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var mode: Mode
        get() = Mode.fromKey(raw.getString(KEY_MODE, Mode.DIM.key))
        set(v) = raw.edit().putString(KEY_MODE, v.key).apply()

    /** 0..80 (%). Capped at 80 so you can still tap through the layer (Android 12+ rule). */
    var darkness: Int
        get() = raw.getInt(KEY_DARKNESS, 60)
        set(v) = raw.edit().putInt(KEY_DARKNESS, v.coerceIn(0, MAX_DARKNESS)).apply()

    /** Spotlight: centre of the visible strip, 0 (top) .. 100 (bottom). */
    var spotPosition: Int
        get() = raw.getInt(KEY_SPOT_POS, 50)
        set(v) = raw.edit().putInt(KEY_SPOT_POS, v.coerceIn(0, 100)).apply()

    /** Spotlight: height of the visible strip, % of the screen. */
    var spotHeight: Int
        get() = raw.getInt(KEY_SPOT_HEIGHT, 25)
        set(v) = raw.edit().putInt(KEY_SPOT_HEIGHT, v.coerceIn(10, 60)).apply()

    /** Black out the whole screen when a second face is seen by the front camera. */
    var faceGuard: Boolean
        get() = raw.getBoolean(KEY_FACE_GUARD, false)
        set(v) = raw.edit().putBoolean(KEY_FACE_GUARD, v).apply()

    enum class Mode(val key: String) {
        DIM("dim"), SPOTLIGHT("spotlight"), PATTERN("pattern");

        companion object {
            fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: DIM
        }
    }

    companion object {
        const val FILE = "privacy_prefs"
        const val KEY_MODE = "mode"
        const val KEY_DARKNESS = "darkness"
        const val KEY_SPOT_POS = "spot_pos"
        const val KEY_SPOT_HEIGHT = "spot_height"
        const val KEY_FACE_GUARD = "face_guard"
        const val MAX_DARKNESS = 80
    }
}
