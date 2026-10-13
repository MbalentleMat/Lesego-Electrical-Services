# ⚡ Lesego Electrical Services — Android App

> **XISD6329/w — Work Integrated Learning 3B | Task 2**  
> Diploma in IT Software Development | The Independent Institute of Education (IIE)

[![Android CI](https://github.com/your-username/LesesoElectricalApp/actions/workflows/android.yml/badge.svg)](https://github.com/your-username/LesesoElectricalApp/actions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-green.svg)](https://developer.android.com)
[![Firebase](https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-orange.svg)](https://firebase.google.com)

---

## 📱 App Overview

A fully functional Android companion app for **Lesego Electrical Services** — a professional electrical services company based in Gauteng, South Africa. The app mirrors the company's website (built in XISD5319) and extends it with mobile-first features including push notifications, GPS-aware contractor search, and offline booking history.

**Tagline:** *Powering your world with excellence*

---

## 🎬 Demo Video

> 📹 [Watch the full app demo on YouTube](https://youtu.be/YOUR_LINK_HERE)  
> (Voice-over walkthrough of all features, running on a physical Android device)

---

## ✨ Features

| Feature | Status | Description |
|---|---|---|
| 🔐 Email/Password Login | ✅ | Firebase Auth with bcrypt hashing |
| 🔐 Google SSO | ✅ | Credential Manager API (modern SSO) |
| 🏠 Home Screen | ✅ | Hero banner, service cards, emergency CTA |
| ⚡ Services Catalogue | ✅ | Filterable by category, offline-cached |
| 📅 3-Step Booking Wizard | ✅ | Date/time picker, confirmation summary |
| 👷 Contractor Directory | ✅ | Search + filter by area & specialty |
| 📊 My Bookings | ✅ | Booking history from Room + API |
| 🧑‍💼 Profile & Settings | ✅ | Edit profile, notification toggles, sign out |
| 💼 Careers / Jobs | ✅ | Expandable job listings |
| 🆘 Support & Help | ✅ | Searchable FAQs, accordion categories |
| 📞 Contact Us | ✅ | Tap-to-call, tap-to-email, message form |
| 🔔 Push Notifications | ✅ | Firebase Cloud Messaging |
| 📶 Offline Support | ✅ | Room DB caches services & bookings |
| 🧪 Unit Tests | ✅ | 15+ tests covering ViewModels & Utils |
| 🤖 GitHub Actions CI | ✅ | Auto build, test, lint on every push |
| 📦 Signed Release APK | ✅ | Ready for Google Play Store |

---

## 🏗️ Architecture

```
app/
├── auth/                   # Login + Register Activities
├── data/
│   ├── api/                # Retrofit + OkHttp (REST API)
│   ├── db/                 # Room Database + DAOs
│   ├── models/             # Kotlin data classes
│   └── repository/         # Single source of truth
├── di/                     # Hilt dependency injection
├── ui/                     # Fragments (one per screen)
├── utils/                  # ValidationUtils, PasswordUtils
└── viewmodel/              # MainViewModel (shared)
```

**Pattern:** MVVM + Repository + Clean Architecture  
**DI:** Hilt  
**Database:** Room (offline) + Firestore (cloud)  
**Networking:** Retrofit 2 + OkHttp  
**Auth:** Firebase Auth (email + Google SSO)  
**Images:** Glide  
**Navigation:** Jetpack Navigation Component + Safe Args  

---

## 🎨 Design System

The app uses the same brand colours as the website:

| Token | Hex | Use |
|---|---|---|
| `primary` | `#7B2CBF` | Buttons, icons, active states |
| `primary_dark` | `#4B0082` | Toolbar, header, nav bar |
| `accent_purple` | `#BB86FC` | Headings, links, highlights |
| `background_dark` | `#0B0B0B` | Screen backgrounds |
| `surface_dark` | `#161616` | Cards, input fields |
| `accent_orange` | `#FF9900` | Stats, warnings, CTAs |
| `text_light` | `#F5F5F5` | Body text |

---

## 🔐 Authentication & Security

- **Email/Password:** Firebase Auth (bcrypt server-side, never stored in plaintext)
- **Google SSO:** Credential Manager API — modern replacement for deprecated GoogleSignIn SDK
- **Password Reset:** Firebase email reset flow
- **JWT Tokens:** Firebase ID token auto-attached to every API request via `AuthInterceptor`
- **Input Validation:** `ValidationUtils` — email regex, password strength, phone format

---

## 🗄️ Database Schema

### Room (Local — SQLite)

**`services`** table:
| Column | Type | Description |
|---|---|---|
| id | TEXT PK | Unique service ID |
| title | TEXT | Service name |
| description | TEXT | Full description |
| category | TEXT | residential / commercial / solar / industrial / maintenance |
| priceFrom | REAL | Starting price in ZAR |
| isAvailable | INTEGER | 1 = available |

**`bookings`** table:
| Column | Type | Description |
|---|---|---|
| id | TEXT PK | Booking UUID |
| userId | TEXT | Firebase UID |
| serviceId | TEXT | FK to services |
| customerName | TEXT | Customer full name |
| customerEmail | TEXT | Customer email |
| customerPhone | TEXT | Contact number |
| serviceAddress | TEXT | Job location |
| preferredDate | TEXT | ISO date string |
| preferredTime | TEXT | HH:mm |
| status | TEXT | pending / confirmed / completed / cancelled |
| reference | TEXT | Human-readable booking ref (e.g. LES-001) |
| createdAt | INTEGER | Unix timestamp |

### Firestore (Cloud)

**`users/{uid}`** document:
```json
{
  "uid": "firebase-uid",
  "name": "Enhle Dlamini",
  "email": "enhle@example.com",
  "phone": "082 123 4567",
  "role": "customer",
  "createdAt": "Timestamp"
}
```

---

## 🌐 REST API

Base URL: `https://api.lesesoelectric.co.za/v1/`

All endpoints accept `Authorization: Bearer <firebase-id-token>`

| Method | Endpoint | Description |
|---|---|---|
| GET | `/services` | List all services (optional `?category=`) |
| GET | `/services/{id}` | Single service detail |
| POST | `/bookings` | Create a booking |
| GET | `/bookings/my` | User's booking history |
| GET | `/contractors` | List contractors (optional filters) |
| GET | `/contractors/{id}` | Contractor profile |
| GET | `/jobs` | Open job listings |
| POST | `/contact` | Send a contact message |

---

## 🧪 Unit Tests

Run with:
```bash
./gradlew test
```

**Test classes:**

| File | Tests |
|---|---|
| `ValidationUtilsTest` | Email, password strength, phone, text validation |
| `PasswordUtilsTest` | SHA-256 hashing, strength scoring, labels |
| `MainViewModelTest` | Services, contractors, bookings, jobs, contact |

---

## 🤖 GitHub Actions (DevOps Pipeline)

Every push to `main` or `develop` triggers:

1. **Unit Tests** — JVM tests via `./gradlew test`
2. **Lint** — Android lint checks
3. **Debug APK Build** — assembled and uploaded as artifact
4. **Release APK Build** — signed with keystore (main branch only)

Secrets required in GitHub repository settings:
- `GOOGLE_SERVICES_JSON` — Firebase config file (base64 or JSON)
- `KEYSTORE_BASE64` — Signing keystore encoded as base64
- `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — Signing credentials

---

## 🚀 Release Notes — v1.0.0

**Initial Release**

### New Features
- ✅ Full email/password authentication with Firebase
- ✅ Google Single Sign-On (SSO) via Credential Manager
- ✅ 3-step guided booking wizard with date/time picker
- ✅ Contractor directory with area and specialty filters
- ✅ Offline service catalogue (Room DB caching)
- ✅ Booking history synced from API + cached locally
- ✅ Searchable FAQ support centre
- ✅ Contact form with tap-to-call / tap-to-email shortcuts
- ✅ Job listings with expandable detail cards
- ✅ Profile screen with notification preference toggles
- ✅ Emergency call-out banner on home screen

### Innovative Features Added Since Prototype
- 🔐 **Credential Manager SSO** — replaced deprecated Google Sign-In with the modern Credential Manager API, supporting passkeys and federated identity
- 📶 **Offline-first architecture** — services and booking history available without internet via Room database
- 🔒 **AuthInterceptor** — automatic Firebase JWT token injection on all API calls, token refreshed transparently
- ♻️ **Reactive data with Flow** — Room emits live data via Kotlin Flow; UI updates automatically without polling
- 🧪 **15+ unit tests** — covers validation, password utilities, and all ViewModel data operations
- 🤖 **Full GitHub Actions CI/CD pipeline** — automated build, test, lint, and signed APK on every push

---

## 📋 Google Play Store Preparation

- ✅ `minSdk 26` (Android 8.0 — covers 95%+ of active devices)
- ✅ ProGuard / R8 minification enabled for release
- ✅ Signed APK produced by CI pipeline
- ✅ `network_security_config.xml` — no cleartext traffic
- ✅ App icon provided in all mipmap densities
- ✅ Content rating: Everyone

> Screenshots and Play Store listing assets are in `/play-store-assets/`

---

## 🛠️ Setup Instructions

### Prerequisites
- Android Studio Ladybug (2024.2.x) or later
- JDK 17
- Android SDK 35

### Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/LesesoElectricalApp.git
   cd LesesoElectricalApp
   ```

2. **Add `google-services.json`**  
   Download from your Firebase project console and place in `app/google-services.json`

3. **Build and run**
   ```bash
   ./gradlew assembleDebug
   # or open in Android Studio and press Run ▶
   ```

4. **Run tests**
   ```bash
   ./gradlew test
   ```

---

## 🤝 Team Members

| Name | Student Number | Role |
|---|---|---|
| Enhle | ST10XXXXXX | Lead Developer, Auth, Booking |
| [Team Member 2] | ST10XXXXXX | API & Repository Layer |
| [Team Member 3] | ST10XXXXXX | UI/UX, Contractor Module |

---

## 🛡️ AI Tool Usage

AI tools (Claude) were used during this project for:
- Generating boilerplate Kotlin class structures and XML layouts
- Suggesting Hilt dependency injection module patterns
- Debugging Retrofit interceptor configuration
- Generating unit test structure and mock setups

All generated code was reviewed, understood, and adapted by the team. No AI-generated code was submitted without comprehension.

---

## 📄 Licence

© 2026 Lesego Electrical Services. All rights reserved.  
Academic project for IIE XISD6329/w — not for commercial use.
