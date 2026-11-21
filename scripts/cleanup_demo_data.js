/**
 * Safe one-time cleanup script to delete ONLY identifiable demo records
 * created by the initial GLASYER build, without touching real user accounts
 * or real user-uploaded videos/posts.
 *
 * Usage:
 *   npm install firebase-admin
 *   GOOGLE_APPLICATION_CREDENTIALS=./service-account.json \
 *   FIRESTORE_DB_ID="ai-studio-android-glasyer-fd1b09cc-4a08-432f-833f-a18b67da1198" \
 *   node scripts/cleanup_demo_data.js
 */

const admin = require("firebase-admin");
const { getFirestore } = require("firebase-admin/firestore");

const SAMPLE_VIDEO_PREFIX = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/";
const DEMO_POST_TEXT = "आप अगला बड़ा वीडियो किस श्रेणी में देखना पसंद करेंगे? नीचे वोट करें!";
const DEMO_WELCOME_NOTIF_TITLE = "GLASYER में आपका स्वागत है!";
const DEMO_CHAT_MSG = "GLASYER लाइव चैट में स्वागत है! अपना सवाल भेजें 🏏";

function isDemoVideo(data) {
  if (!data) return false;
  if (data.seedItem === true) return true;
  const url = String(data.videoUrl || "");
  const thumb = String(data.thumbnailKey || data.thumbnailUrl || "");
  return url.startsWith(SAMPLE_VIDEO_PREFIX) || thumb.startsWith("preset:");
}

async function deleteCollectionMatches(db, collectionName, predicate) {
  const snap = await db.collection(collectionName).get();
  let deleted = 0;
  for (const doc of snap.docs) {
    if (predicate(doc.data(), doc.id)) {
      // Clean subcollections (comments/postComments) if any
      const subCols = await doc.ref.listCollections();
      for (const sub of subCols) {
        const subSnap = await sub.get();
        for (const subDoc of subSnap.docs) {
          await subDoc.ref.delete();
        }
      }
      await doc.ref.delete();
      deleted++;
    }
  }
  return deleted;
}

async function main() {
  admin.initializeApp();
  const dbId = process.env.FIRESTORE_DB_ID || "ai-studio-android-glasyer-fd1b09cc-4a08-432f-833f-a18b67da1198";
  const db = getFirestore(admin.app(), dbId);

  const deletedVideos = await deleteCollectionMatches(db, "videos", isDemoVideo);
  const deletedPosts = await deleteCollectionMatches(db, "posts", (d) => d.text === DEMO_POST_TEXT);
  const deletedNotifs = await deleteCollectionMatches(db, "notifications", (d) => d.title === DEMO_WELCOME_NOTIF_TITLE);
  const deletedChats = await deleteCollectionMatches(db, "liveChats", (d) => d.message === DEMO_CHAT_MSG);

  console.log(JSON.stringify({
    status: "ok",
    deletedVideos,
    deletedPosts,
    deletedNotifs,
    deletedChats,
  }, null, 2));
}

main().catch((err) => {
  console.error("Cleanup failed:", err);
  process.exit(1);
});
