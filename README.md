# Privacy Screen (for Oppo / ColorOS)

An Android app that makes your screen hard to read for people sitting beside you.
Works on any Oppo phone running Android 8.0 or newer (also OnePlus and Realme, which use the same ColorOS base).

## What it does

| Feature | How it works |
|---|---|
| **Dim** | Darkens the whole screen over every app. Held close and straight you can still read it; from the side or a distance it's much harder. |
| **Spotlight** | Blacks out everything except a strip you choose (position and height are adjustable). |
| **Pattern** | Covers the screen with fine lines that break up text at a distance. |
| **Face guard** (optional) | Front camera + on-device face detection. If a second face appears, the whole screen goes black until they look away. Nothing is recorded or uploaded. |
| **Quick Settings tile** | Turn it ON / OFF from the notification shade without opening the app. |
| **Notification** | Shows while it's on, with a "Turn off" button. |

Honest limit: this is a software filter. A physical privacy screen protector narrows the viewing angle of the glass itself; no app can do that. This app makes shoulder-surfing much harder, and Face guard covers the case where someone leans in.

---

## 1. Build the APK (on a Windows / Mac / Linux computer)

1. Install **Android Studio** (free): https://developer.android.com/studio
2. Unzip this folder, then in Android Studio choose **File > Open** and select the `PrivacyScreen` folder.
3. Wait for "Gradle sync" to finish. The first time it downloads a few hundred MB. If it offers to install a missing SDK or upgrade Gradle/AGP, accept.
4. Menu **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
5. When it finishes, click **locate** in the popup. The file is `app/build/outputs/apk/debug/app-debug.apk`.

**Faster alternative:** connect your Oppo with a USB cable (turn on Developer options and USB debugging first, see below) and press the green ▶ Run button. Android Studio installs it straight onto the phone.

### Turn on USB debugging on Oppo (only needed for the ▶ Run method)
Settings > About device > Version > tap **Build number** 7 times → enter your PIN.
Then Settings > Additional settings > Developer options > turn on **USB debugging**.

## 2. Install the APK on the Oppo phone

1. Copy `app-debug.apk` to the phone (USB, Google Drive, WhatsApp to yourself, etc.).
2. Open it from the **Files** app.
3. ColorOS will ask to allow installing from this source. Allow it, then tap **Install**.
4. If ColorOS shows a security scan warning, choose **Install anyway** (the app is your own build).

## 3. One-time Oppo setup (important)

ColorOS closes background apps aggressively. The app has a "Setup for Oppo" section with buttons for each step:

1. **Allow floating windows** ("Display over other apps"). Required. Without it nothing can be drawn over other apps.
2. **Allow notifications**, so the on/off notification shows.
3. **Allow background running**: tap the button and allow. Also check App info > **Battery usage** > turn on **Allow background activity** (and **Allow foreground activity** if shown).
4. **Auto launch**: tap the button and enable Privacy Screen. If the button opens App info instead, look for "Auto launch" / "Allow auto launch" there, or in Settings > Apps > Auto launch.
5. **Lock it in Recents**: open Recent apps, tap the ⋮ (or long-press the app card) and choose **Lock**.
6. **Add the tile**: swipe down twice from the top, tap the pencil / edit icon, drag **Privacy screen** into the panel.

## 4. Using it

- Turn it on with the big switch or the Quick Settings tile.
- Adjust **Darkness** until it's comfortable for you but hard for others (60-80% works well in daylight; lower your screen brightness too, it helps a lot).
- Darkness is capped at 80% on purpose: Android 12 and newer block taps through anything more opaque, and you need to keep using your phone underneath.
- **Face guard**: switch it on inside the app (it needs camera permission). You'll see Android's green camera-in-use dot while it runs. If the screen ever goes black and stays black, swipe down from the top and tap **Turn off** in the notification.
- If you turn the privacy screen on from the tile while the app is closed, Android 14+ may not allow the camera to start. The app will say "Face guard paused"; just open the app once and it resumes.

## Battery

Dim / Spotlight / Pattern use almost no battery. Face guard keeps the front camera running, so expect noticeably higher battery use while it's on. Use it when you need it (bus, metro, office).

## Project layout

```
app/src/main/java/com/privacyscreen/app/
  MainActivity.kt         settings screen + Oppo setup shortcuts
  PrivacyService.kt       foreground service that keeps the layer on screen
  PrivacyOverlayView.kt   draws Dim / Spotlight / Pattern / blackout
  FaceWatcher.kt          front camera + ML Kit face counting (on-device)
  PrivacyTileService.kt   Quick Settings tile
  Prefs.kt                saved settings
```

## Publishing to Play Store (later)
For your own phone, the debug APK is enough. For the Play Store you'd need: a signed release build (Build > Generate Signed Bundle), a privacy policy page (mention the camera is processed on-device only), and the "special use" foreground service declaration in Play Console.
