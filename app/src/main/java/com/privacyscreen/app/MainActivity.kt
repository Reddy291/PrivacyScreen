package com.privacyscreen.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var switchMain: MaterialSwitch
    private lateinit var switchFace: MaterialSwitch
    private lateinit var status: TextView
    private lateinit var modeGroup: MaterialButtonToggleGroup
    private lateinit var modeHint: TextView
    private lateinit var darknessLabel: TextView
    private lateinit var darkness: Slider
    private lateinit var spotOptions: View
    private lateinit var spotPos: Slider
    private lateinit var spotHeight: Slider
    private lateinit var btnOverlay: Button
    private lateinit var btnNotif: Button
    private lateinit var btnBattery: Button

    /** true while we set widget values from code, so listeners ignore it */
    private var binding = false

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshSetup() }

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                prefs.faceGuard = true
            } else {
                Toast.makeText(this, "Camera permission is needed for Face guard", Toast.LENGTH_LONG).show()
            }
            bindUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        switchMain = findViewById(R.id.switchMain)
        switchFace = findViewById(R.id.switchFace)
        status = findViewById(R.id.status)
        modeGroup = findViewById(R.id.modeGroup)
        modeHint = findViewById(R.id.modeHint)
        darknessLabel = findViewById(R.id.darknessLabel)
        darkness = findViewById(R.id.darkness)
        spotOptions = findViewById(R.id.spotOptions)
        spotPos = findViewById(R.id.spotPos)
        spotHeight = findViewById(R.id.spotHeight)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnNotif = findViewById(R.id.btnNotif)
        btnBattery = findViewById(R.id.btnBattery)

        setupListeners()
        bindUi()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PrivacyService.running.collect { on ->
                    binding = true
                    switchMain.isChecked = on
                    binding = false
                    status.text = if (on) "On. People beside you can't easily read your screen." else "Off"
                }
            }
        }

        handleTurnOnExtra(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTurnOnExtra(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshSetup()
        // If Face guard was paused (privacy screen started from the tile while the app
        // was closed), restarting from here lets Android hand us the camera again.
        if (PrivacyService.running.value && prefs.faceGuard &&
            hasPermission(Manifest.permission.CAMERA)) {
            PrivacyService.start(this)
        }
    }

    private fun handleTurnOnExtra(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_TURN_ON, false) == true) {
            intent.removeExtra(EXTRA_TURN_ON)
            turnOn()
        }
    }

    // ------------------------------------------------------------------ widgets

    private fun setupListeners() {
        switchMain.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            if (checked) turnOn() else PrivacyService.stop(this)
        }

        modeGroup.addOnButtonCheckedListener { _, id, isChecked ->
            if (binding || !isChecked) return@addOnButtonCheckedListener
            prefs.mode = when (id) {
                R.id.modeSpot -> Prefs.Mode.SPOTLIGHT
                R.id.modePattern -> Prefs.Mode.PATTERN
                else -> Prefs.Mode.DIM
            }
            bindUi()
        }

        darkness.addOnChangeListener { _, value, fromUser ->
            if (fromUser) prefs.darkness = value.toInt()
            darknessLabel.text = "Darkness: ${value.toInt()}%"
        }
        spotPos.addOnChangeListener { _, value, fromUser ->
            if (fromUser) prefs.spotPosition = value.toInt()
        }
        spotHeight.addOnChangeListener { _, value, fromUser ->
            if (fromUser) prefs.spotHeight = value.toInt()
        }

        switchFace.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            if (!checked) {
                prefs.faceGuard = false
            } else if (hasPermission(Manifest.permission.CAMERA)) {
                prefs.faceGuard = true
            } else {
                binding = true
                switchFace.isChecked = false
                binding = false
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        btnOverlay.setOnClickListener { openOverlaySettings() }
        btnNotif.setOnClickListener { askNotifications(force = true) }
        btnBattery.setOnClickListener { openBatterySettings() }
        findViewById<Button>(R.id.btnAutostart).setOnClickListener { openOppoAutoStart() }
    }

    private fun bindUi() {
        binding = true
        val mode = prefs.mode
        modeGroup.check(
            when (mode) {
                Prefs.Mode.DIM -> R.id.modeDim
                Prefs.Mode.SPOTLIGHT -> R.id.modeSpot
                Prefs.Mode.PATTERN -> R.id.modePattern
            }
        )
        modeHint.text = when (mode) {
            Prefs.Mode.DIM -> "Darkens the whole screen. Hold the phone close and straight: you can still read it, people further away can't."
            Prefs.Mode.SPOTLIGHT -> "Only a strip of the screen stays visible. Set where it sits below."
            Prefs.Mode.PATTERN -> "Covers the screen with fine lines that break up text at a distance."
        }
        spotOptions.visibility = if (mode == Prefs.Mode.SPOTLIGHT) View.VISIBLE else View.GONE
        darkness.value = snap(prefs.darkness.toFloat(), 0f, 80f, 5f)
        darknessLabel.text = "Darkness: ${darkness.value.toInt()}%"
        spotPos.value = prefs.spotPosition.toFloat()
        spotHeight.value = prefs.spotHeight.toFloat()
        switchFace.isChecked = prefs.faceGuard && hasPermission(Manifest.permission.CAMERA)
        binding = false
    }

    private fun snap(v: Float, min: Float, max: Float, step: Float) =
        (Math.round(v.coerceIn(min, max) / step) * step)

    // --------------------------------------------------------------- turn on/off

    private fun turnOn() {
        if (!Settings.canDrawOverlays(this)) {
            binding = true
            switchMain.isChecked = false
            binding = false
            Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }
        askNotifications(force = false)
        PrivacyService.start(this)
        // If the service couldn't start, put the switch back to Off
        switchMain.postDelayed({
            if (!PrivacyService.running.value) {
                binding = true
                switchMain.isChecked = false
                binding = false
            }
        }, 1500)
    }

    // ------------------------------------------------------------- setup (Oppo)

    private fun refreshSetup() {
        btnOverlay.text = mark(Settings.canDrawOverlays(this), "1. Allow floating windows")
        btnNotif.text = mark(NotificationManagerCompat.from(this).areNotificationsEnabled(),
            "2. Allow notifications")
        val pm = getSystemService(PowerManager::class.java)
        btnBattery.text = mark(pm.isIgnoringBatteryOptimizations(packageName),
            "3. Allow background running")
        bindUi()
    }

    private fun mark(ok: Boolean, label: String) = if (ok) "✓  $label" else label

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun askNotifications(force: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (force) {
            tryStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun openOverlaySettings() {
        tryStart(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        )
    }

    @SuppressLint("BatteryLife")
    private fun openBatterySettings() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            tryStart(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")),
                appDetails(),
            )
        } else {
            // On ColorOS: App info > Battery usage > "Allow background activity"
            tryStart(appDetails())
            Toast.makeText(this,
                "Open Battery usage and turn on \"Allow background activity\"",
                Toast.LENGTH_LONG).show()
        }
    }

    /** Oppo / OnePlus / Realme hide auto-launch in different places on each ColorOS version. */
    private fun openOppoAutoStart() {
        val candidates = listOf(
            ComponentName("com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.oppoguardelf",
                "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
        ).map { Intent().setComponent(it) }

        val opened = tryStart(*(candidates + appDetails()).toTypedArray())
        if (opened) {
            Toast.makeText(this,
                "Turn on Auto launch / Allow background activity for Privacy Screen",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun appDetails() =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    /** Starts the first intent that works. */
    private fun tryStart(vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                startActivity(i)
                return true
            } catch (_: Exception) {
                // not available on this phone, try the next one
            }
        }
        Toast.makeText(this, "Couldn't open that settings page on this phone", Toast.LENGTH_SHORT).show()
        return false
    }

    companion object {
        const val EXTRA_TURN_ON = "turn_on"
    }
}
