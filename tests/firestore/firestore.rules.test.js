import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, beforeEach, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from "firebase/firestore";

const projectId = "demo-dost";
const rules = await readFile(new URL("../../firestore.rules", import.meta.url), "utf8");
let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId,
    firestore: { rules },
  });
});

after(async () => {
  await testEnv?.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

function profile({
  isPublic = true,
  ageBand = "18–24",
  ageCohort = "adult",
  displayName = "Test person",
  contactPolicy = "everyone",
} = {}) {
  return {
    displayName,
    ageBand,
    ageCohort,
    gender: "Prefer not to say",
    country: "Bangladesh",
    bio: "",
    interests: "",
    contactPolicy,
    isPublic,
    updatedAt: serverTimestamp(),
  };
}

function snapshot({
  ageBand = "18–24",
  ageCohort = "adult",
  displayName = "Test person",
  contactPolicy = "everyone",
} = {}) {
  return {
    displayName,
    ageBand,
    ageCohort,
    gender: "Prefer not to say",
    country: "Bangladesh",
    bio: "",
    interests: "",
    contactPolicy,
  };
}

function user(uid, claims = {}) {
  return testEnv.authenticatedContext(uid, {
    dostAgeEligible: true,
    dostAgeEligibleUntil: Math.floor(Date.now() / 1000) + 3600,
    ...claims,
  }).firestore();
}

function callOffer({
  callerUid = "alice",
  calleeUid = "bob",
  mediaType = "audio",
  ringExpiresAt = new Date(Date.now() + 60_000),
  expiresAt = new Date(Date.now() + 3_000_000),
} = {}) {
  return {
    callerUid,
    calleeUid,
    mediaType,
    status: "ringing",
    offer: { type: "offer", sdp: "v=0\r\naudio-only-offer" },
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ringExpiresAt,
    expiresAt,
  };
}

async function seedProfile(uid, fields = {}) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), `publicProfiles/${uid}`), {
      displayName: "Test person",
      ageBand: "18–24",
      ageCohort: "adult",
      gender: "Prefer not to say",
      country: "Bangladesh",
      bio: "",
      interests: "",
      contactPolicy: "everyone",
      isPublic: true,
      ...fields,
    });
  });
}

async function seedChat({ members = ["alice", "bob"], ageCohort = "adult", profiles } = {}) {
  const memberProfiles = profiles ?? {
    alice: snapshot({ displayName: "Alice" }),
    bob: snapshot({ displayName: "Bob" }),
  };
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "peopleChats/alice-bob"), {
      members,
      ageCohort,
      memberProfiles,
      createdBy: "alice",
      createdAt: new Date(),
      lastMessage: "",
      lastMessageSenderUid: "",
      lastMessageAt: new Date(),
      lastMessageId: "",
      unreadCounts: { alice: 0, bob: 0 },
    });
  });
}

async function sendChatMessage(uid, messageId, text, unreadCounts) {
  const firestore = user(uid);
  const batch = writeBatch(firestore);
  batch.update(doc(firestore, "peopleChats/alice-bob"), {
    lastMessage: text,
    lastMessageSenderUid: uid,
    lastMessageAt: serverTimestamp(),
    lastMessageId: messageId,
    unreadCounts,
  });
  batch.set(doc(firestore, `peopleChats/alice-bob/messages/${messageId}`), {
    senderUid: uid,
    text,
    createdAt: serverTimestamp(),
  });
  return batch.commit();
}

test("a user can create a private profile but cannot read another private profile", async () => {
  await assertSucceeds(
    setDoc(doc(user("alice"), "publicProfiles/alice"), profile({ isPublic: false })),
  );
  await assertFails(getDoc(doc(user("bob"), "publicProfiles/alice")));
});

test("People data requires an unexpired server age-eligibility claim", async () => {
  await seedProfile("alice");
  await seedChat();

  const unverified = testEnv.authenticatedContext("alice").firestore();
  const expired = user("alice", { dostAgeEligibleUntil: 1 });

  await assertSucceeds(getDoc(doc(user("alice"), "publicProfiles/alice")));
  await assertFails(getDoc(doc(unverified, "publicProfiles/alice")));
  await assertFails(getDoc(doc(expired, "publicProfiles/alice")));
  await assertFails(getDoc(doc(unverified, "peopleChats/alice-bob")));
  await assertFails(
    setDoc(doc(unverified, "peopleChats/alice-bob/messages/unverified"), {
      senderUid: "alice",
      text: "This must not be stored.",
      createdAt: serverTimestamp(),
    }),
  );
  await assertFails(
    setDoc(doc(unverified, "publicProfiles/another-user"), profile()),
  );
});

test("profile creation rejects under-18 age bands and cohorts", async () => {
  await assertFails(
    setDoc(
      doc(user("teen"), "publicProfiles/teen"),
      profile({ ageBand: "13–15", ageCohort: "adult" }),
    ),
  );
  await assertFails(
    setDoc(
      doc(user("teen-2"), "publicProfiles/teen-2"),
      profile({ ageBand: "16–17", ageCohort: "under18" }),
    ),
  );
});

test("public discovery only returns adult profiles and denies legacy minor profiles", async () => {
  await seedProfile("adult-viewer");
  await seedProfile("adult-peer");
  await seedProfile("legacy-teen", {
    ageBand: "16–17",
    ageCohort: "under18",
  });

  const profiles = await assertSucceeds(
    getDocs(query(
      collection(user("adult-viewer"), "publicProfiles"),
      where("isPublic", "==", true),
      where("ageCohort", "==", "adult"),
    )),
  );

  assert.deepEqual(
    profiles.docs.map((entry) => entry.id).sort(),
    ["adult-peer", "adult-viewer"],
  );
  await assertFails(getDoc(doc(user("adult-viewer"), "publicProfiles/legacy-teen")));
  await assertFails(getDoc(doc(user("legacy-teen"), "publicProfiles/legacy-teen")));
});

test("a user cannot change their age band or cohort after profile creation", async () => {
  await seedProfile("adult", { ageBand: "18–24", ageCohort: "adult" });

  await assertFails(
    setDoc(
      doc(user("adult"), "publicProfiles/adult"),
      profile({ ageBand: "25–34", ageCohort: "adult" }),
    ),
  );
});

test("only chat participants can read a conversation", async () => {
  await seedChat();

  await assertSucceeds(getDoc(doc(user("alice"), "peopleChats/alice-bob")));
  await assertSucceeds(getDoc(doc(user("bob"), "peopleChats/alice-bob")));
  await assertFails(getDoc(doc(user("mallory"), "peopleChats/alice-bob")));
});

test("audio and video calls are limited to adult chat participants and valid state changes", async () => {
  await seedChat();
  const alice = user("alice");
  const bob = user("bob");
  const callRef = doc(alice, "peopleChats/alice-bob/calls/call-1");

  await assertSucceeds(setDoc(callRef, callOffer()));
  await assertSucceeds(setDoc(
    doc(alice, "peopleChats/alice-bob/calls/video-call"),
    callOffer({ mediaType: "video" }),
  ));
  await assertFails(setDoc(
    doc(alice, "peopleChats/alice-bob/calls/unsupported-call"),
    callOffer({ mediaType: "screen" }),
  ));
  const legacyAudioCall = callOffer();
  delete legacyAudioCall.mediaType;
  await assertSucceeds(setDoc(
    doc(alice, "peopleChats/alice-bob/calls/legacy-audio-call"),
    legacyAudioCall,
  ));
  await assertFails(updateDoc(doc(alice, "peopleChats/alice-bob/calls/video-call"), {
    mediaType: "audio",
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(getDoc(doc(bob, "peopleChats/alice-bob/calls/call-1")));
  await assertFails(getDoc(doc(user("mallory"), "peopleChats/alice-bob/calls/call-1")));
  await assertFails(
    setDoc(doc(user("mallory"), "peopleChats/alice-bob/calls/forged"), callOffer({
      callerUid: "mallory",
      calleeUid: "bob",
    })),
  );
  await assertFails(
    setDoc(doc(alice, "peopleChats/alice-bob/calls/expired"), callOffer({
      ringExpiresAt: new Date(Date.now() - 1_000),
    })),
  );

  await assertFails(updateDoc(callRef, {
    status: "accepted",
    answer: { type: "answer", sdp: "v=0\r\naudio-answer" },
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(updateDoc(doc(bob, "peopleChats/alice-bob/calls/call-1"), {
    status: "accepted",
    answer: { type: "answer", sdp: "v=0\r\naudio-answer" },
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(updateDoc(callRef, {
    status: "ended",
    updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(doc(bob, "peopleChats/alice-bob/calls/call-1"), {
    status: "ringing",
    updatedAt: serverTimestamp(),
  }));
});

test("call ICE candidates are participant-scoped and immutable", async () => {
  await seedChat();
  const alice = user("alice");
  const bob = user("bob");
  const expiresAt = new Date(Date.now() + 3_000_000);
  const callRef = doc(alice, "peopleChats/alice-bob/calls/call-2");
  await assertSucceeds(setDoc(callRef, callOffer({ expiresAt })));
  const callerCandidate = doc(alice, "peopleChats/alice-bob/calls/call-2/callerCandidates/candidate-1");
  const candidate = {
    sdp: "candidate:1 1 UDP 1 192.0.2.1 5000 typ host",
    sdpMid: "0",
    sdpMLineIndex: 0,
    createdAt: serverTimestamp(),
    expiresAt,
  };
  await assertSucceeds(setDoc(callerCandidate, candidate));
  await assertFails(setDoc(
    doc(bob, "peopleChats/alice-bob/calls/call-2/callerCandidates/forged"),
    candidate,
  ));
  await assertSucceeds(setDoc(
    doc(bob, "peopleChats/alice-bob/calls/call-2/calleeCandidates/candidate-2"),
    candidate,
  ));
  await assertFails(updateDoc(callerCandidate, { sdp: "changed" }));
});

test("blocking a chat participant prevents call signaling", async () => {
  await seedChat();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "peopleBlocks/bob/blockedUsers/alice"), {
      createdAt: new Date(),
    });
  });
  await assertFails(setDoc(
    doc(user("alice"), "peopleChats/alice-bob/calls/blocked-call"),
    callOffer(),
  ));
});

test("call push tokens are private and writable only by their owner", async () => {
  const tokenRef = doc(user("alice"), "peopleCallTokens/alice/devices/device-1");
  await assertSucceeds(setDoc(tokenRef, {
    token: "fcm-registration-token".padEnd(64, "x"),
    updatedAt: serverTimestamp(),
  }));
  await assertFails(getDoc(doc(user("bob"), "peopleCallTokens/alice/devices/device-1")));
  await assertFails(setDoc(doc(user("bob"), "peopleCallTokens/alice/devices/device-2"), {
    token: "fcm-registration-token".padEnd(64, "y"),
    updatedAt: serverTimestamp(),
  }));
});

test("creating a chat requires two adult public profiles and exact snapshots", async () => {
  await seedProfile("alice", { displayName: "Alice" });
  await seedProfile("bob", { displayName: "Bob" });
  const chat = {
    members: ["alice", "bob"],
    ageCohort: "adult",
    memberProfiles: {
      alice: snapshot({ displayName: "Alice" }),
      bob: snapshot({ displayName: "Bob" }),
    },
    createdBy: "alice",
    createdAt: serverTimestamp(),
    lastMessage: "",
    lastMessageSenderUid: "",
    lastMessageAt: serverTimestamp(),
    lastMessageId: "",
    unreadCounts: { alice: 0, bob: 0 },
  };

  await assertSucceeds(setDoc(doc(user("alice"), "peopleChats/new-chat"), chat));
  await assertFails(
    setDoc(doc(user("alice"), "peopleChats/forged-chat"), {
      ...chat,
      memberProfiles: {
        ...chat.memberProfiles,
        bob: snapshot({ displayName: "Impersonated Bob" }),
      },
    }),
  );

  await seedProfile("minor", { ageBand: "16–17", ageCohort: "under18" });
  await assertFails(
    setDoc(doc(user("alice"), "peopleChats/minor-chat"), {
      ...chat,
      members: ["alice", "minor"],
      memberProfiles: {
        alice: snapshot({ displayName: "Alice" }),
        minor: snapshot({ ageBand: "16–17", ageCohort: "under18", displayName: "Minor" }),
      },
    }),
  );

  await assertSucceeds(
    setDoc(doc(user("alice"), "peopleBlocks/alice/blockedUsers/bob"), {
      createdAt: serverTimestamp(),
    }),
  );
  await assertFails(setDoc(doc(user("alice"), "peopleChats/blocked-chat"), chat));
});

test("request-only profiles require an accepted contact request before chat creation", async () => {
  await seedProfile("alice", { displayName: "Alice", contactPolicy: "requests" });
  await seedProfile("bob", { displayName: "Bob", contactPolicy: "requests" });
  const chat = {
    members: ["alice", "bob"],
    ageCohort: "adult",
    memberProfiles: {
      alice: snapshot({ displayName: "Alice", contactPolicy: "requests" }),
      bob: snapshot({ displayName: "Bob", contactPolicy: "requests" }),
    },
    createdBy: "alice",
    createdAt: serverTimestamp(),
    lastMessage: "",
    lastMessageSenderUid: "",
    lastMessageAt: serverTimestamp(),
    lastMessageId: "",
    unreadCounts: { alice: 0, bob: 0 },
  };
  await assertFails(setDoc(doc(user("alice"), "peopleChats/alice-bob"), chat));

  const request = doc(user("alice"), "peopleContactRequests/bob/incoming/alice");
  await assertSucceeds(setDoc(request, {
    senderUid: "alice",
    recipientUid: "bob",
    conversationId: "alice-bob",
    status: "pending",
    senderProfile: snapshot({ displayName: "Alice", contactPolicy: "requests" }),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));
  const incoming = await assertSucceeds(getDocs(query(
    collection(user("bob"), "peopleContactRequests/bob/incoming"),
    where("recipientUid", "==", "bob"),
    where("status", "==", "pending"),
  )));
  assert.equal(incoming.size, 1);
  await assertSucceeds(getDoc(doc(user("bob"), "peopleContactRequests/bob/incoming/alice")));
  await assertSucceeds(getDoc(doc(user("alice"), "peopleContactRequests/bob/incoming/alice")));
  await assertFails(getDoc(doc(user("mallory"), "peopleContactRequests/bob/incoming/alice")));

  const bobFirestore = user("bob");
  const accept = writeBatch(bobFirestore);
  accept.update(doc(bobFirestore, "peopleContactRequests/bob/incoming/alice"), {
    status: "accepted",
    respondedBy: "bob",
    updatedAt: serverTimestamp(),
  });
  accept.set(doc(bobFirestore, "peopleContactApprovals/alice-bob"), {
    conversationId: "alice-bob",
    recipientUid: "bob",
    senderUid: "alice",
    members: ["alice", "bob"],
    createdBy: "bob",
    createdAt: serverTimestamp(),
  });
  accept.set(doc(bobFirestore, "peopleChats/alice-bob"), { ...chat, createdBy: "bob" });
  await assertSucceeds(accept.commit());
  await assertFails(setDoc(doc(user("bob"), "peopleChats/duplicate-chat"), {
    ...chat,
    createdBy: "bob",
  }));
});

test("a recipient can report a pending contact request but an outsider cannot", async () => {
  await seedProfile("alice", { displayName: "Alice" });
  await seedProfile("bob", { displayName: "Bob", contactPolicy: "requests" });
  await assertSucceeds(setDoc(doc(user("alice"), "peopleContactRequests/bob/incoming/alice"), {
    senderUid: "alice",
    recipientUid: "bob",
    conversationId: "alice-bob",
    status: "pending",
    senderProfile: snapshot({ displayName: "Alice" }),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));
  const report = {
    reporterUid: "bob",
    targetUid: "alice",
    contactRequestId: "alice",
    reason: "Inappropriate contact request",
    createdAt: serverTimestamp(),
  };
  await assertSucceeds(setDoc(doc(user("bob"), "peopleReports/request-report"), report));
  await assertFails(setDoc(doc(user("mallory"), "peopleReports/outsider-report"), {
    ...report,
    reporterUid: "mallory",
  }));
});

test("a recipient can ignore or block a request and handled requests cannot be reopened", async () => {
  await seedProfile("alice", { displayName: "Alice" });
  await seedProfile("bob", { displayName: "Bob", contactPolicy: "requests" });
  await assertSucceeds(setDoc(doc(user("alice"), "peopleContactRequests/bob/incoming/alice"), {
    senderUid: "alice",
    recipientUid: "bob",
    conversationId: "alice-bob",
    status: "pending",
    senderProfile: snapshot({ displayName: "Alice" }),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));
  const bobFirestore = user("bob");
  await assertSucceeds(updateDoc(doc(bobFirestore, "peopleContactRequests/bob/incoming/alice"), {
    status: "ignored",
    respondedBy: "bob",
    updatedAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(user("alice"), "peopleContactRequests/bob/incoming/alice"), {
    senderUid: "alice",
    recipientUid: "bob",
    conversationId: "alice-bob",
    status: "pending",
    senderProfile: snapshot({ displayName: "Alice" }),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));

  await seedProfile("charlie", { displayName: "Charlie" });
  await assertSucceeds(setDoc(doc(user("charlie"), "peopleContactRequests/bob/incoming/charlie"), {
    senderUid: "charlie",
    recipientUid: "bob",
    conversationId: "bob-charlie",
    status: "pending",
    senderProfile: snapshot({ displayName: "Charlie" }),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));
  const block = writeBatch(bobFirestore);
  block.set(doc(bobFirestore, "peopleBlocks/bob/blockedUsers/charlie"), {
    createdAt: serverTimestamp(),
  });
  block.update(doc(bobFirestore, "peopleContactRequests/bob/incoming/charlie"), {
    status: "ignored",
    respondedBy: "bob",
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(block.commit());
  await assertFails(setDoc(doc(user("charlie"), "peopleChats/bob-charlie"), {
    members: ["bob", "charlie"],
    ageCohort: "adult",
    memberProfiles: {
      bob: snapshot({ displayName: "Bob", contactPolicy: "requests" }),
      charlie: snapshot({ displayName: "Charlie" }),
    },
    createdBy: "charlie",
    createdAt: serverTimestamp(),
    lastMessage: "",
    lastMessageSenderUid: "",
    lastMessageAt: serverTimestamp(),
    lastMessageId: "",
    unreadCounts: { bob: 0, charlie: 0 },
  }));
});

test("profiles that reject new chats cannot be contacted directly or by request", async () => {
  await seedProfile("alice", { displayName: "Alice" });
  await seedProfile("bob", { displayName: "Bob", contactPolicy: "nobody" });
  const chat = {
    members: ["alice", "bob"],
    ageCohort: "adult",
    memberProfiles: {
      alice: snapshot({ displayName: "Alice" }),
      bob: snapshot({ displayName: "Bob", contactPolicy: "nobody" }),
    },
    createdBy: "alice",
    createdAt: serverTimestamp(),
    lastMessage: "",
    lastMessageSenderUid: "",
    lastMessageAt: serverTimestamp(),
    lastMessageId: "",
    unreadCounts: { alice: 0, bob: 0 },
  };
  await assertFails(setDoc(doc(user("alice"), "peopleChats/alice-bob"), chat));
  await assertFails(setDoc(doc(user("alice"), "peopleContactRequests/bob/incoming/alice"), {
    senderUid: "alice",
    recipientUid: "bob",
    conversationId: "alice-bob",
    status: "pending",
    senderProfile: snapshot({ displayName: "Alice" }),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));
});

test("legacy under-18 chats and messages are inaccessible", async () => {
  await seedChat({ ageCohort: "under18" });

  await assertFails(getDoc(doc(user("alice"), "peopleChats/alice-bob")));
  await assertFails(getDocs(collection(user("alice"), "peopleChats/alice-bob/messages")));
});

test("only a participant can send a correctly attributed bounded message", async () => {
  await seedChat();
  await assertSucceeds(sendChatMessage("alice", "message-1", "Hello", { alice: 0, bob: 1 }));
  await assertFails(sendChatMessage("mallory", "message-2", "Hello", { alice: 0, bob: 2 }));
  await assertFails(
    sendChatMessage("alice", "message-3", "x".repeat(2001), { alice: 0, bob: 2 }),
  );
});

test("unread counts can only be cleared by their owner and are incremented with a message", async () => {
  await seedChat();
  await assertFails(
    updateDoc(doc(user("alice"), "peopleChats/alice-bob"), {
      unreadCounts: { alice: 0, bob: 1 },
    }),
  );
  await assertSucceeds(sendChatMessage("alice", "message-1", "Hello", { alice: 0, bob: 1 }));

  await assertFails(
    updateDoc(doc(user("alice"), "peopleChats/alice-bob"), {
      unreadCounts: { alice: 0, bob: 0 },
    }),
  );
  await assertSucceeds(
    updateDoc(doc(user("bob"), "peopleChats/alice-bob"), {
      unreadCounts: { alice: 0, bob: 0 },
    }),
  );
});

test("blocking either participant prevents both from sending new messages", async () => {
  await seedChat();
  await assertSucceeds(
    setDoc(doc(user("alice"), "peopleBlocks/alice/blockedUsers/bob"), {
      createdAt: serverTimestamp(),
    }),
  );

  await assertFails(sendChatMessage("alice", "blocked-1", "Hello", { alice: 0, bob: 1 }));
  await assertFails(sendChatMessage("bob", "blocked-2", "Hello", { alice: 1, bob: 0 }));
});

test("reports can only be submitted by a participant about the other chat participant", async () => {
  await seedChat();
  const report = {
    reporterUid: "alice",
    targetUid: "bob",
    conversationId: "alice-bob",
    reason: "Inappropriate messages",
    createdAt: serverTimestamp(),
  };

  await assertSucceeds(setDoc(doc(user("alice"), "peopleReports/report-1"), report));
  await assertFails(
    setDoc(doc(user("mallory"), "peopleReports/report-2"), {
      ...report,
      reporterUid: "mallory",
    }),
  );
  await assertFails(getDoc(doc(user("alice"), "peopleReports/report-1")));
  await assertFails(getDocs(collection(user("bob"), "peopleReports")));
});
