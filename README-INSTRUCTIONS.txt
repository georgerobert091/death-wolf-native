DEATH WOLF AI — PURPLE MICROPHONE DOCK UPDATE
SOURCE PATCH ZIP — NOT AN APK

IMPORTANT
This is a source-code patch for your existing Android Studio project. It is not a ready-to-install APK.
Make a backup/copy of your whole project before replacing anything.

WHAT THIS UPDATE DOES
- Keeps the floating Wolf logo button.
- Tapping the Wolf icon opens a compact dock at the bottom of the screen.
- The dock has a purple microphone button.
- Tapping the purple microphone starts one speech-recognition session.
- No wake word is used.
- The existing MainActivity.kt, dashboard, logo, app ID and Gradle files are not replaced.

FILES IN THIS ZIP
1. Replace:
   app/src/main/java/com/deathwolf/assistant/WolfOverlayService.kt
2. Add:
   app/src/main/java/com/deathwolf/assistant/WolfVoiceService.kt
3. Replace:
   app/src/main/java/com/deathwolf/assistant/WolfListeningService.kt
4. Replace:
   app/src/main/AndroidManifest.xml

DO NOT REPLACE
- MainActivity.kt
- app/build.gradle.kts
- project-level Gradle files
- res/drawable-nodpi/wolf_logo.png
- your existing themes, launcher icons or dashboard files

HOW TO APPLY
1. Back up your complete Death Wolf AI project first.
2. Extract this ZIP.
3. In your project, open app/src/main/java/com/deathwolf/assistant/.
4. Replace WolfOverlayService.kt and WolfListeningService.kt with the files from this ZIP.
5. Put the new WolfVoiceService.kt in that same package folder.
6. Replace app/src/main/AndroidManifest.xml with the manifest from this ZIP.
7. Open/sync the project in Android Studio and build the app.
8. Install the newly built APK.
9. Open Death Wolf AI and allow microphone permission.
10. Enable the assistant/floating icon and allow “Display over other apps” if Android asks.
11. Tap the floating Wolf icon. The bottom dock should appear.
12. Tap the purple microphone and speak a simple test command, such as “list apps” or “open WhatsApp”.

TROUBLESHOOTING
- If speech recognition says it is unavailable, check that your phone has a working speech-recognition provider and internet connection if the provider requires one.
- If it says microphone permission is missing, open Android Settings > Apps > Death Wolf AI > Permissions > Microphone and allow it.
- If the floating icon or dock does not appear, enable Death Wolf AI's “Display over other apps” permission.
- If Gradle reports an error, do not delete project files or change random dependencies. Copy the complete error text and share it so the exact issue can be fixed.

LIMITATIONS / HONEST NOTES
- This patch uses Android's installed SpeechRecognizer service. Recognition quality and language support depend on the phone's speech provider, microphone, network and device settings.
- App-launch commands and “list apps” are handled locally. General AI conversation is not connected to an online AI backend by this patch.
- Android may restrict an app launched from a floating overlay depending on Android version and device manufacturer.
- The source has not been compiled on your specific Android Studio/phone environment. Build and test it before replacing your installed app.
