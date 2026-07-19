# Lyra ✨ - The Smart, Secure Life Logging App

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=google&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-%23FF9900.svg?style=for-the-badge&logo=amazon-aws&logoColor=white)
![Firebase](https://img.shields.io/badge/firebase-%23039BE5.svg?style=for-the-badge&logo=firebase)
<img width="1178" height="2498" alt="3" src="https://github.com/user-attachments/assets/af883b5a-0dbc-4626-a4ca-32dfee6a5eeb" />
<img width="1178" height="2498" alt="2" src="https://github.com/user-attachments/assets/04e45461-58c4-42c6-b596-b5781f138900" />
<img width="1178" height="2498" alt="1" src="https://github.com/user-attachments/assets/003c33e2-01ad-4726-91a3-cd2b7a2336c3" />

**Lyra** is an AI-powered, highly secure, and intuitive life-logging Android application. Built to be more than just a standard CRUD journal, Lyra understands your emotions, tracks your locations, and securely syncs your memories across the cloud without compromising performance or privacy.

---

## 🌟 Key Features

*   **🧠 AI-Powered Sentiment Analysis:** Uses on-device Machine Learning via **TensorFlow Lite (TFLite)** to automatically analyze the sentiment/mood of your daily journal entries.
*   **☁️ Scalable Cloud Storage:** Seamlessly uploads and streams high-quality images and audio notes using **AWS S3**, ensuring zero performance drops.
*   **🔄 Offline-First Synchronization:** Engineered with **Room Database** for local caching and **Firebase** for real-time cloud backups. Your data is always accessible, even offline.
*   **📍 Smart Location Tagging:** Integrates **Google Location Services (FusedLocationProvider)** and **Geocoder API** to automatically translate raw coordinates into readable addresses for your memories.
*   **🔐 Biometric Security:** Maximum privacy guaranteed. The app is locked behind Android's native Biometric Authentication.
*   **🎨 Glassmorphism UI:** Features a custom, modern Glassmorphism interface with interactive starry background animations, built entirely from scratch using **Jetpack Compose**.
*   **🎤 Voice-to-Text & Audio Notes:** Record audio memories directly or use speech recognition to write your logs effortlessly.

---

## 💻 Tech Stack

### Frontend / UI
*   **Language:** Kotlin
*   **Framework:** Jetpack Compose (Material 3)
*   **Image Loading:** Coil

### Backend / Data Architecture
*   **Local Storage:** Room Database
*   **Cloud Sync:** Firebase Realtime Database / Firestore
*   **Media Storage:** Amazon Web Services (AWS S3)

### AI & Hardware Integration
*   **Machine Learning:** TensorFlow Lite (TFLite)
*   **Location Services:** Google Fused Location API & Geocoder
*   **Security:** AndroidX Biometric API

---


