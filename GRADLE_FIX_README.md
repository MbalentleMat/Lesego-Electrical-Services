# Gradle compatibility fix

- Changed wrapper from Gradle 9.6.0 to Gradle 8.7, compatible with AGP 8.5.2.
- Removed experimental or obsolete Gradle/Android properties that could prevent configuration.
- Retained existing Firebase debug configuration and app code.

## Open in Android Studio
1. Extract this archive, open the `LesegoElectricalApp` directory (not the ZIP).
2. Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK: choose JDK 17.
3. Turn off Gradle Offline Mode, then File > Sync Project with Gradle Files.
4. Run `./gradlew --stop`, `./gradlew clean testDebugUnitTest assembleDebug` (Windows: `gradlew.bat`).
5. If dependencies were cached against Gradle 9, use File > Invalidate Caches / Restart and re-sync.

Note: Firebase JSON is configured for the `.debug` variant. Release builds require a separate Firebase Android registration/configuration. Build not verified in this environment (no Android SDK/Gradle dependencies).
