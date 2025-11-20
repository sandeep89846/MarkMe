# Mark Me: Offline-First Attendance System

**Mark Me** is a secure, geolocation-based attendance tracking system designed for environments with unstable internet connectivity. It uses a "Store-and-Forward" architecture to ensure attendance is never lost, even if the device is offline.

## 🚀 Key Features

*   **Offline-First Architecture:** Attendance is signed and stored locally when offline, then automatically synced via background workers when the internet returns.
*   **Secure Identity:** Uses **Biometric Authentication** (Fingerprint/Face) to access the Android Keystore for cryptographic signing (ECDSA).
*   **Geofencing:** Validates student location against classroom coordinates using the Haversine formula.
*   **Anti-Spoofing:** Prevents proxy attendance via Device ID binding and rotating QR code nonces.

## 🛠 Tech Stack

### Android App (Client)
*   **Language:** Kotlin
*   **UI:** Jetpack Compose (Material 3)
*   **Local DB:** SQLDelight
*   **Background Sync:** WorkManager
*   **Networking:** Retrofit + Moshi

### Server (Backend)
*   **Runtime:** Node.js
*   **Framework:** Fastify
*   **Database:** SQLite (via Prisma ORM)
*   **Language:** TypeScript

---

## 📦 Getting Started

### Prerequisites
1.  **Node.js** (v18 or higher) installed.
2.  **Android Studio** (Koala or newer recommended).
3.  A physical Android device (recommended) or Emulator with Google Play Services.

---

### 1. Server Setup

1.  Navigate to the server directory:
    ```bash
    cd server
    ```
2.  Install dependencies:
    ```bash
    npm install
    ```
3.  Create a `.env` file in the `server` folder and add the following secrets:
    ```env
    DATABASE_URL="file:./prisma/dev.db"
    JWT_SECRET="your_super_secret_random_string"
    TEACHER_SECRET="teacher_password_to_access_portal"
    ADMIN_EMAIL="your_google_email_for_testing@gmail.com"
    ```
4.  Initialize the database and seed dummy data (students/subjects):
    ```bash
    npx prisma migrate dev --name init
    npm run prisma:seed
    ```
5.  Start the server:
    ```bash
    npm run dev
    ```
    *The server will run on `http://127.0.0.1:4000`*

---

### 2. Android App Setup

1.  Open **Android Studio**.
2.  Select **Open** and choose the `android-app` folder.
3.  **Configure API URL:**
    *   Open `app/src/main/java/com/markme/app/network/RetrofitClient.kt`.
    *   Change `DEV_BASE_URL` to your computer's IP address.
    *   *Note: If using an Emulator, use `http://10.0.2.2:4000/`. If using a physical phone, use your PC's local IP (e.g., `http://192.168.1.5:4000/`).*
4.  **Sync Gradle** and run the app on your device.

---

## 📱 How It Works

1.  **Teacher Portal:**
    *   Open `http://localhost:4000/?secret=YOUR_TEACHER_SECRET` in a browser.
    *   Select a subject to generate a rotating QR Code.

2.  **Student Registration:**
    *   Open the App.
    *   Sign in with Google (First time links the device to the student account).
    *   Enroll Biometrics.

3.  **Marking Attendance:**
    *   Click "Mark Attendance".
    *   **Step 1 (Location):** App checks if you are physically near the class coordinates (defined in `seed.ts`).
    *   **Step 2 (Scan):** Scan the Teacher's QR code.
    *   **Step 3 (Sign):** Authenticate with Fingerprint to cryptographically sign the data.

4.  **Sync:**
    *   If online: Data sends immediately.
    *   If offline: Data is queued. It will auto-sync when internet is restored.

---

## 🛡️ Security Details

This project implements a **Defense-in-Depth** strategy:
1.  **Non-Repudiation:** Requests are signed using a private key stored in the hardware TEE (Trusted Execution Environment), accessible only via biometrics.
2.  **Replay Protection:** QR codes contain a `nonce` valid for only 30 seconds.
3.  **Device Binding:** A student account is strictly bound to one physical device ID.

---

## 📄 License
Open Source - For Educational Purposes.
