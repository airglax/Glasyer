# GLASYER — Real User Video Platform (Android + Kotlin + Jetpack Compose + Firebase)

**GLASYER** is a Hindi (Devanagari, **Hind** font) mobile-first YouTube-style user video platform built natively with **Kotlin, Jetpack Compose, Material 3, Media3 ExoPlayer, and Firebase (Authentication, Cloud Firestore, Firebase Cloud Storage)**.

> **No Mock Data**: All demo videos, bundled demo thumbnails, fake channels, fake stats, and automatic seeding have been removed. Every video, short, post, comment, like, subscription, and view count is backed by real Firebase Authentication, Cloud Storage, and Cloud Firestore operations.

---

## Implemented Features
- **Design & Branding**: Dual-color `GLASYER` logo, Hind Devanagari font (`res/font/hind.ttf`), deep teal-black dark theme (`#09141A` / `#112028` / `#5CC8FF`), matching light theme (`#F2F8FB` / `#0A8FD8`), coral live badge (`#EF4E5A`), and system/dark/light theme switching.
- **Auth & Real User Profiles**: Google Sign-In via Jetpack Credential Manager + Firebase Auth, first-time channel setup (handle, display name, Photo Picker avatar uploaded to Firebase Storage, bio), and real uploader channel resolution (`users/{ownerUid}`).
- **Real Video Upload (`+`)**: Zero-permission Android Photo Picker -> MIME & 100 MB size validation -> Firebase Cloud Storage upload with live byte progress (`UploadProgressState`) -> auto-captured or custom thumbnail uploaded to Storage -> Firestore metadata published only after Storage succeeds. Videos <=60s with vertical orientation automatically become Shorts.
- **Real Home Feed & Pagination**: Queries `videos` where `visibility == "public"` with Firestore cursor pagination (`startAfter` + `limit`), real-time listener for newly published public videos, category chips (`सब`, `गेमिंग`, `म्यूज़िक`, `लाइव`, `खाना`, `क्रिकेट`, `सीखें`, `यात्रा`), search, and proper Hindi empty state (`"अभी कोई वीडियो उपलब्ध नहीं है।"` + `"पहला वीडियो अपलोड करें"`).
- **Real Video Player & Qualified View Tracking**: Media3 ExoPlayer with play/pause, seek slider, speed (`0.5x`–`2x`), fullscreen, mini-player, transaction-safe likes/dislikes (no duplicate votes), transaction-safe subscriptions (updates `subscriberCount`), notification bell modes, Watch Later & custom playlists, threaded comments & transaction-safe comment likes, and qualified view tracking (increments once per session after actual `STATE_READY` playback, never on recomposition).
- **Shorts & Community**: Vertical `VerticalPager` Shorts with loop, mute, `1x/2x` speed, Clear-screen mode, and Community Posts (up to 10 Storage-hosted images + interactive Polls).

---

## Firebase Console & Deployment Setup

1. **Firestore Rules & Indexes**:
   - `firestore.rules` and `firestore.indexes.json` are included in the root directory.
   - Deploy rules and indexes:
     ```bash
     firebase deploy --only firestore:rules,firestore:indexes
     ```

2. **Firebase Cloud Storage (Required for Real Video & Thumbnail Uploads)**:
   - Open **Firebase Console → Storage** for project `gen-lang-client-0454137527` and click **Get Started** to initialize the default bucket (`gen-lang-client-0454137527.firebasestorage.app`).
   - Deploy `storage.rules` so authenticated users can only upload to their own `/videos/{uid}/...`, `/users/{uid}/...`, and `/posts/{uid}/...` paths with strict MIME and size enforcement:
     ```bash
     firebase deploy --only storage
     ```

3. **Safe Legacy Demo Cleanup Script**:
   - The Android client automatically cleans up any legacy demo records owned by the signed-in user on startup without touching real uploads.
   - To remove legacy demo documents across all users from an admin workstation, run:
     ```bash
     GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccount.json node scripts/cleanup_demo_data.js
     ```

4. **Optional Firebase Extensions**:
   - **Phone OTP Sign-In**: Enable *Phone* under *Firebase Console → Authentication → Sign-in method* and add your app's SHA-1/SHA-256 fingerprints.
   - **Push Notifications (FCM)**: Enable *Cloud Messaging* and register a `FirebaseMessagingService`.
   - **Live Streaming Ingest**: Connect a dedicated WebRTC/RTMP ingest pipeline to populate live HLS/DASH URLs for live broadcasts.
