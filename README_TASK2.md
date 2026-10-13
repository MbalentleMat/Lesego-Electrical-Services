# Lesego Electrical Services — XISD6329 Task 2

## What this package is
Enhanced Kotlin Android Studio source based on the supplied LesesoElectricalApp project. Existing navigation, Firebase Auth, Google SSO, profile/settings, Room cache and UI layouts are retained. Service directory, contractors, vacancies, bookings and enquiries now use Firestore directly rather than a placeholder API hostname.

**Important:** This source is not a verified release APK. Firebase credentials and real-device testing are required. No live Firebase account or hosted API was accessed while preparing this package.

## Firebase setup (in order)
1. Go to https://console.firebase.google.com and create a Firebase project.
2. Add an Android app with **exact package ID** `com.lesego.electrical`.
3. Download `google-services.json` and place it in `app/google-services.json` (not committed to GitHub).
4. In Authentication > Sign-in method, enable **Email/Password** and **Google**. Set a support email.
5. For Google SSO, obtain your debug SHA-1 (`./gradlew signingReport`) and register it in Firebase Project settings > Your apps. Register the release SHA-1 too before release. Download a fresh `google-services.json` after updating SHA fingerprints.
6. Create a Cloud Firestore database. For the demonstration choose a suitable region; start in production mode and publish the provided `firestore.rules` under Firestore > Rules. Do not use public test-mode rules.
7. Seed three public demo collections with 10 records each. Install Node.js, run `npm install firebase-admin` in `firebase_seed/`, create a restricted service-account JSON in Firebase project settings (never put it in the repo), set `GOOGLE_APPLICATION_CREDENTIALS` to its path, then run `node seed.js` from `firebase_seed/`.
8. Register a test customer using the app; Firebase Authentication will show the account and Firestore `users` should show a profile document. Book a service; Firestore `bookings` should show the booking. Submit a contact enquiry; Firestore `inquiries` should show the message.
9. Deploy rules through the Firebase Console, or run `firebase deploy --only firestore:rules --project YOUR_PROJECT_ID` after `firebase login` and selecting your project.
10. For GitHub Actions, add repository secret `GOOGLE_SERVICES_JSON` containing the exact JSON contents. Push source files (not ZIP) to GitHub. Actions runs JVM tests, lint and builds a debug APK. Enable signed release workflow only after configuring the keystore secrets and repository variable `ENABLE_SIGNED_RELEASE=true`.

## Run on physical Android device
Install Android Studio and Android SDK 35, open the project folder containing `settings.gradle.kts`, sync Gradle with internet, connect a physical Android device using USB debugging, select device, then Run. Test email registration, sign-in, Google sign-in, browsing, booking, enquiry, profile updates, sign-out and sign-in again. Check the Firebase Console for matching records.

## Demonstration and rubric evidence
Record a screen capture **on a physical Android phone**, with spoken narration. Show: registration and login, Google SSO, service list, new booking and reference, Firestore bookings record, contact form and Firestore inquiries record, profile settings, handling invalid input, Firestore rules, GitHub commit history, Kanban board, GitHub Actions passing build/test run, README, user help, and the matching website (website must be hosted separately). Screenshots and links belong in the Task 2 report with cover and contents page; submit report on ARC and source through GitHub, not as a ZIP.

## Remaining project work and limitations
- The supplied website was not included in the Android ZIP; Task 2 also requires updating the website. Integrate the same Firebase project or backend and document it separately.
- The existing app contains a Retrofit client referencing an undeployed placeholder hostname. The main repository's implemented features now use Firestore, but inspect other uses before deleting Retrofit.
- Staff job assignment, invoice/payment processing and management reports from the original XISD project analysis are **not** implemented by this patch. These require additional role-based screens and trusted server-side processing; do not claim them as complete.
- Booking status is staff-controlled under Firestore rules. Never let customers self-assign or mark jobs complete.
- Demo data contains fictitious contractors and job listings, clearly marked as examples. Do not present them as real employees or vacancies.
- Confirm that Firebase project billing, data protection requirements and email notifications are appropriate before a real launch. Email notifications are not configured.
- The existing project's authentication implementation and dependency versions still require device/build verification.

## DevOps evidence checklist
Track backlog, in-progress, testing and completed tasks on Azure DevOps Kanban; link tasks to GitHub commits; capture evidence of pull requests, unit tests, lint and successful CI builds. Record meeting notes, feedback and iteration changes. Never fabricate screenshots of tests or deployments.
