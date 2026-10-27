HbkBeats Music - Android music + video player (Kotlin, Jetpack Compose, libVLC)

BUILD THE APK
1. Install Android Studio (free) and open this folder (HbkBeatsMusic).
2. Let Gradle sync finish (needs internet the first time). Accept any prompt to update Gradle/plugins.
3. Debug APK: Build > Build Bundle(s) / APK(s) > Build APK(s).
   File appears in app/build/outputs/apk/debug/app-debug.apk
4. Copy to your phone and install (allow "install unknown apps").
5. Release APK: Build > Generate Signed Bundle / APK > APK, create a keystore, choose release.

PRIVATE SPACE
- Tap the 3-dot menu on any song/video: "Make private" (file) or "Make folder ... private".
- Private items vanish from the Music and Videos tabs and appear only in the Private tab.
- First time in the Private tab you create a 4-8 digit PIN.
- The space locks when you leave the app; private playback stops. Screenshots are blocked in it.
- NOTE: items are hidden inside this app only. Other apps (Gallery, file manager) can still see the files.
- If you forget the PIN, clear the app's data (this also clears the hidden list).

BUILD ON GITHUB (no Android Studio needed)
1. Create a new GitHub repository and upload everything inside this folder to its root
   (settings.gradle.kts must be at the top level, and the .github/workflows/build.yml file must be included).
2. Open the repo's Actions tab. The "Build APK" workflow runs on every push (or press Run workflow).
3. When it finishes (about 5-10 minutes), open the run and download "HbkBeatsMusic-debug-apk" under Artifacts.
4. Unzip it, copy app-debug.apk to your phone and install.
