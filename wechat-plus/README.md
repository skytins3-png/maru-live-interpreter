# MARU 위챗 통역+ V0.5

Standalone Android application that combines the convenient conversation-mode visual approach of a bilingual translation app with a user-initiated call-launch shortcut. Does not intercept or reroute call audio.

Features: Chinese/Korean two-way voice input; English intermediate translation; reverse-check after pivot translation; editable translation; text copying; WeChat text share; manual floating translator; conversation history; large conversation UI; voice playback speed; face-to-face orientation; example phrases; optional auto voice output; optional experimental language recognition (dependent on device ASR).

Offline ML Kit text translation after models are downloaded. Speech recognition may use an online service. Do not rely on translation accuracy for critical messages; inspect both versions.

**Android limitation:** Third-party apps generally cannot read protected WeChat call audio, and synthesized sound is not directly injected into the WeChat microphone. Call mic contention may prevent recognizing the user's speech while a call is active.

Build: Gradle 8.13 + JDK 17 + Android SDK platform 35; `gradle :app:assembleDebug`.

The applicationId matches 0.4 for intended update, but APKs produced by different GitHub runner debug keys may have different signatures. If "App not installed" occurs, uninstall old 0.4 before installing 0.5, which will clear old app data.
