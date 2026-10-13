# Existing Firebase project integration

Firebase project ID: `lesegoelectricalapp`

The supplied Android Firebase registration is for `com.lesego.electrical.debug`. The debug build therefore uses `applicationIdSuffix = ".debug"`, and its configuration is at `app/src/debug/google-services.json`. The main Android namespace and release application ID remain `com.lesego.electrical`.

## Before running
1. In Firebase Console, open the existing project `lesegoelectricalapp`.
2. In Authentication > Sign-in method, enable Email/Password and Google if not already enabled.
3. For Google Sign-In, add the SHA-1 and SHA-256 fingerprints for the keystore used to sign the debug build, and download an updated google-services.json if Firebase changes the OAuth configuration.
4. In Firestore Database, create or check the existing database and **review rules against existing data and roles before deploying**. Do not overwrite existing production data with demo seed records.
5. Open this project in Android Studio, sync Gradle, and run the **debug** build on a real Android device.
6. Verify registration, login, Google Sign-In, booking writes, data retrieval, logout, and invalid-input handling.

## Release build
The release application ID is `com.lesego.electrical`. To configure release Firebase, register that *separate* Android app in the same Firebase project and download its configuration to `app/src/release/google-services.json`. Until then, the release build may fail Firebase configuration processing. Do not distribute a release APK before configuring signing and verifying rules.

## Security
This JSON is an Android client configuration, not a Firebase Admin SDK service-account key. Restrict applicable Google Cloud API keys, review Firestore security rules, and never commit private service-account credentials.

## Verification status
Configuration and package ID alignment were checked statically; this environment has not completed a Gradle build or device/Firebase integration test.
