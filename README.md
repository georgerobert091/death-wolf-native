# Death Wolf Native — starter alpha

This is the first native Android foundation for Death Wolf AI. It preserves the supplied wolf logo and the black/purple visual direction.

## Included in this first stage
- Native Kotlin + Jetpack Compose screen.
- Tap microphone, say **Wolf**, then give a supported command.
- Android speech recognition and text-to-speech.
- App launching for WhatsApp, YouTube, Chrome, and Free Fire when installed.
- Stop Listening control.

## Not included yet
- Gemini/online AI connection.
- Default Android digital-assistant (`VoiceInteractionService`) integration.
- Reliable always-on wake-word listening in the background or with screen off.

Those are later stages. This alpha intentionally requires the user to open Death Wolf and tap the microphone first. Android controls background microphone access, and this starter does not claim to listen while closed.

## Build
Open this folder in Android Studio or a compatible Android IDE, sync Gradle, then build the debug APK. The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile SDK 35, and minimum SDK 26.
