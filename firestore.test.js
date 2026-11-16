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

function buildValidUserDoc(userId, now) {
  return {
    userId: userId,
    displayName: "Alice Starlight",
    email: "alice@example.com",
    onboardingCompleted: true,
    coreAspiration: "Lead with calm clarity, creative courage, and grounded presence.",
    lifePillars: ["Mindful Leadership", "Creative Mastery", "Deep Relationships"],
    growthGoals: "Practice daily reflection and intentional communication.",
    emotionalGrounding: "Calm under pressure, curious rather than reactive.",
    reflectionStyle: "Warm & Socratic",
    quizCompleted: true,
    companionName: "Nova",
    archetypeTitle: "The Serene Architect",
    backstorySummary: "A pixel spirit forged from your highest aspirations in the Midnight Studio.",
    greetingStyle: "Hey there! Ready to check in on what matters most today?",
    signatureCatchphrase: "Small intentional pixels build the whole masterpiece.",
    warmth: 85,
    directness: 75,
    optimism: 80,
    playfulness: 65,
    analyticalDepth: 78,
    traitKeywords: ["Empathetic", "Grounded", "Strategic"],
    skinToneId: "warm_ivory",
    hairStyleId: "starlight_bob",
    hairColorId: "midnight_indigo",
    eyeStyleId: "sparkle_round",
    eyeColorId: "amber_gold",
    expressionDefault: "warm_smile",
    backdropThemeId: "midnight_studio",
    outfitId: "cozy_hoodie",
    primaryColorHex: "#FFB347",
    accentColorHex: "#FF7E67",
    accessoryId: "pixel_glasses",
    auraEffectId: "starlight_fireflies",
    voicePitchMilli: 1000,
    voiceRateMilli: 1000,
    handsFreeAutoListen: true,
    preferredAudioRoute: "SPEAKER",
    autoExtractMemories: true,
    saveConversationHistory: true,
    contextWindowMemoryLimit: 15,
    createdAt: now,
    updatedAt: now,
  };
}

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

test("Unauthenticated user: cannot read or write user companion profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(buildValidUserDoc(ALICE_UID, new Date()))
  );
});

test("Authenticated owner: can create and update valid user companion profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(buildValidUserDoc(ALICE_UID, now))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      companionName: "Astra",
      warmth: 92,
      updatedAt: new Date(),
    })
  );
});

test("Shadow update & identity spoofing: rejects ghost fields and cross-user writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(buildValidUserDoc(ALICE_UID, now))
  );

  // Ghost field injection must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isVerifiedAdmin: true,
      updatedAt: new Date(),
    })
  );

  // Bob cannot read or update Alice's profile
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      companionName: "Hacked",
      updatedAt: new Date(),
    })
  );
});

test("Conversations & Messages: owner can create/list, cross-user and message updates are blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  const convRef = aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1");
  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      title: "Evening Voice Reflection",
      mode: "VOICE",
      lastMessagePreview: "Let's focus on calm breathwork.",
      messageCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );

  const msgRef = convRef.collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      sender: "USER",
      content: "I want to stay grounded during tomorrow's presentation.",
      emotionTag: "determined",
      createdAt: now,
    })
  );

  // Messages are append-only (updates forbidden)
  await assertFails(
    msgRef.update({
      content: "Edited message",
    })
  );

  // Bob cannot read Alice's messages
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").collection("messages").get()
  );
});

test("Long-term memories: owner can create, list, update, and delete; cross-user blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  const memRef = aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1");
  await assertSucceeds(
    memRef.set({
      userId: ALICE_UID,
      category: "GOAL",
      summary: "Practice 10 minutes of deep creative work every morning before checking messages.",
      importance: 5,
      sourceConversationId: "conv_1",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );

  await assertSucceeds(
    memRef.update({
      importance: 4,
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(memRef.delete());
});
