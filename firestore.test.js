const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastDate = (offsetMs = 5000) => new Date(Date.now() - offsetMs);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("videos").where("visibility", "==", "public").get());
  await assertFails(unauth.collection("users").where("publicChannel", "==", true).get());
  await assertFails(unauth.collection("posts").where("visibility", "==", "public").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).set({ userId: ALICE_UID }));
});

test("Users channel profile create, update, and privilege escalation prevention", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(10000);

  // Cannot self-assign verified = true or fake subscriberCount
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_vlogs",
      displayName: "Alice Vlogs",
      photoUrl: "",
      bannerUrl: "",
      bio: "नमस्ते",
      subscriberCount: 500,
      verified: true,
      publicChannel: true,
      onboardingDone: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Valid profile create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_vlogs",
      displayName: "Alice Vlogs",
      photoUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/avatar.jpg",
      bannerUrl: "",
      bio: "नमस्ते दोस्तों",
      subscriberCount: 0,
      verified: false,
      publicChannel: true,
      onboardingDone: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Filtered public list succeeds, unfiltered list fails
  await assertSucceeds(bobDb.collection("users").where("publicChannel", "==", true).get());
  await assertFails(bobDb.collection("users").get());

  // Bob cannot edit Alice's bio, but can update subscriberCount
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ bio: "Hacked", updatedAt: pastDate(1000) })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).update({ subscriberCount: 1, updatedAt: pastDate(1000) })
  );

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({ ghostField: "x", updatedAt: pastDate(1000) })
  );
});

test("Private user settings and blocked users are strictly owner-only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("settings").set({
      userId: ALICE_UID,
      themeMode: "system",
      language: "hi",
      autoplay: true,
      playbackQuality: "auto",
      restrictedMode: false,
      dataSaver: false,
      historyPaused: false,
      updatedAt: pastDate(1000),
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").doc("settings").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("blocked").doc(BOB_UID).set({
      blockedUserId: BOB_UID,
      blockedName: "Bob",
      createdAt: pastDate(1000),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("blocked").get());
});

test("Real uploaded videos visibility, zero-initial-counts, cursor pagination, and comments", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(10000);

  // Reject non-HTTPS videoUrl or spoofed non-zero initial views
  await assertFails(
    aliceDb.collection("videos").doc("vid_fake").set({
      videoId: "vid_fake",
      ownerUid: ALICE_UID,
      channelName: "Alice Tech",
      channelAvatar: "",
      title: "Fake Views",
      description: "",
      tags: [],
      category: "सीखें",
      videoType: "video",
      visibility: "public",
      videoUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/vid.mp4?alt=media",
      thumbnailUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/thumb.jpg?alt=media",
      durationLabel: "02:00",
      duration: 120,
      viewsCount: 9999,
      likesCount: 0,
      dislikesCount: 0,
      commentsCount: 0,
      viewerCount: 0,
      commentsEnabled: true,
      ageRestricted: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Public video create
  await assertSucceeds(
    aliceDb.collection("videos").doc("8299999990000_abc1").set({
      videoId: "8299999990000_abc1",
      ownerUid: ALICE_UID,
      channelName: "Alice Tech",
      channelAvatar: "",
      title: "हिन्दी टेक रिव्यू",
      description: "शानदार वीडियो #tech",
      tags: ["tech", "hindi"],
      category: "सीखें",
      videoType: "video",
      visibility: "public",
      videoUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/vid1.mp4?alt=media",
      thumbnailUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/thumb1.jpg?alt=media",
      durationLabel: "02:00",
      duration: 120,
      viewsCount: 0,
      likesCount: 0,
      dislikesCount: 0,
      commentsCount: 0,
      viewerCount: 0,
      commentsEnabled: true,
      ageRestricted: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Private video create
  await assertSucceeds(
    aliceDb.collection("videos").doc("8299999991000_priv").set({
      videoId: "8299999991000_priv",
      ownerUid: ALICE_UID,
      channelName: "Alice Tech",
      channelAvatar: "",
      title: "प्राइवेट ड्राफ्ट",
      description: "",
      tags: [],
      category: "सब",
      videoType: "short",
      visibility: "private",
      videoUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/draft.mp4?alt=media",
      thumbnailUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/draft.jpg?alt=media",
      durationLabel: "00:30",
      duration: 30,
      viewsCount: 0,
      likesCount: 0,
      dislikesCount: 0,
      commentsCount: 0,
      viewerCount: 0,
      commentsEnabled: true,
      ageRestricted: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Public paginated query succeeds; unfiltered fails; private doc read by Bob fails
  const page1 = await assertSucceeds(
    bobDb.collection("videos").where("visibility", "==", "public").limit(10).get()
  );
  await assertFails(bobDb.collection("videos").get());
  await assertFails(bobDb.collection("videos").doc("8299999991000_priv").get());
  await assertSucceeds(aliceDb.collection("videos").where("ownerUid", "==", ALICE_UID).get());
  if (page1.docs.length > 0) {
    await assertSucceeds(
      bobDb.collection("videos").where("visibility", "==", "public").startAfter(page1.docs[page1.docs.length - 1]).limit(10).get()
    );
  }

  // Bob cannot edit Alice's title, but can increment viewsCount
  await assertFails(
    bobDb.collection("videos").doc("8299999990000_abc1").update({ title: "Hijack", updatedAt: pastDate(500) })
  );
  await assertSucceeds(
    bobDb.collection("videos").doc("8299999990000_abc1").update({ viewsCount: 1, updatedAt: pastDate(500) })
  );

  // Comments on video
  await assertSucceeds(
    bobDb.collection("videos").doc("8299999990000_abc1").collection("comments").doc("c_1").set({
      videoId: "8299999990000_abc1",
      videoOwnerId: ALICE_UID,
      videoVisibility: "public",
      authorId: BOB_UID,
      authorName: "Bob",
      authorAvatar: "",
      text: "बहुत बढ़िया वीडियो!",
      parentId: "",
      likesCount: 0,
      pinned: false,
      creatorHearted: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Video owner Alice can pin and heart Bob's comment
  await assertSucceeds(
    aliceDb.collection("videos").doc("8299999990000_abc1").collection("comments").doc("c_1").update({
      pinned: true,
      creatorHearted: true,
      updatedAt: pastDate(500),
    })
  );

  await assertSucceeds(
    bobDb.collection("videos").doc("8299999990000_abc1").collection("comments").where("videoVisibility", "==", "public").get()
  );
});

test("Subscriptions, Likes, Playlists, History, Notifications, Reports", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("subscriptions").doc(`${ALICE_UID}_${BOB_UID}`).set({
      subscriberId: ALICE_UID,
      channelId: BOB_UID,
      channelName: "Bob Channel",
      channelAvatar: "",
      notifyMode: "all",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("subscriptions").where("subscriberId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("subscriptions").where("subscriberId", "==", ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("likes").doc(`${ALICE_UID}_vid_1`).set({
      userId: ALICE_UID,
      targetId: "vid_1",
      targetType: "video",
      reaction: "like",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("likes").doc(`${ALICE_UID}_vid_1`).update({
      reaction: "dislike",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(bobDb.collection("likes").doc(`${ALICE_UID}_vid_1`).get());

  await assertSucceeds(
    aliceDb.collection("playlists").doc("pl_1").set({
      ownerId: ALICE_UID,
      title: "पसंदीदा",
      description: "",
      visibility: "private",
      videoIds: ["vid_1"],
      systemType: "custom",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(bobDb.collection("playlists").doc("pl_1").get());
  await assertSucceeds(aliceDb.collection("playlists").where("ownerId", "==", ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("history").doc(`${ALICE_UID}_vid_1`).set({
      userId: ALICE_UID,
      videoId: "vid_1",
      videoTitle: "हिन्दी टेक",
      channelName: "Alice",
      thumbnailUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/thumb1.jpg",
      durationLabel: "05:00",
      watchedAt: ts,
    })
  );
  await assertFails(bobDb.collection("history").where("userId", "==", ALICE_UID).get());

  await assertSucceeds(
    bobDb.collection("notifications").doc("n_1").set({
      recipientId: ALICE_UID,
      actorId: BOB_UID,
      actorName: "Bob",
      type: "reply",
      title: "नया जवाब",
      body: "Bob ने आपकी टिप्पणी पर जवाब दिया",
      targetId: "vid_1",
      read: false,
      createdAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("notifications").where("recipientId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("notifications").where("recipientId", "==", ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("reports").doc("r_1").set({
      reporterId: ALICE_UID,
      targetId: "vid_1",
      targetType: "video",
      reason: "स्पैम",
      status: "pending",
      createdAt: ts,
    })
  );
  await assertFails(bobDb.collection("reports").doc("r_1").get());
});
