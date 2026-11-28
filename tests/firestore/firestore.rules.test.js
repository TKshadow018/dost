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
  collectionGroup,
  doc,
  getDoc,
  getDocs,
  orderBy,
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
let secureAuditSequence = 0;

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
    dostAgeCohort: "adult",
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

function secureUser(uid, claims = {}) {
  return user(uid, {
    dostShieldUntil: Math.floor(Date.now() / 1000) + 3600,
    dostShieldSeats: 25,
    ...claims,
  });
}

function secureMember(uid, actorUid, role, organizationName = "Private group", eventId = `evt-${uid}`) {
  return {
    uid,
    displayName: uid,
    role,
    status: "active",
    actorUid,
    auditEventId: eventId,
    organizationName,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

async function setSecureFeatureEnabled(enabled) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "secureOrgConfig/superSecure"), {
      enabled,
    });
  });
}

async function setCipherFeatureEnabled(enabled) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "cipherConfig/cipher"), { enabled });
  });
}

async function seedCircleChat(chatId = "circle-chat") {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), `peopleChats/${chatId}`), {
      members: ["alice", "bob"],
    });
  });
}

function cipherChat({
  createdBy = "alice",
  extra = {},
} = {}) {
  return {
    members: ["alice", "bob"],
    createdBy,
    sourceCircleChatId: "circle-chat",
    salt: "AAAAAAAAAAAAAAAAAAAAAA==",
    keyCheck: "A".repeat(64),
    v: 1,
    createdAt: serverTimestamp(),
    ...extra,
  };
}

async function seedCipherChat() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "cipherChats/circle-chat"), {
      ...cipherChat(),
      createdAt: new Date(),
    });
  });
}

async function createSecureOrg(
  orgId,
  ownerUid,
  enableFeature = true,
  ownerClaims = {},
  seatLimit = 25,
  includeShieldEntitlement = true,
) {
  if (enableFeature) await setSecureFeatureEnabled(true);
  const firestore = includeShieldEntitlement
    ? secureUser(ownerUid, ownerClaims)
    : user(ownerUid, ownerClaims);
  const batch = writeBatch(firestore);
  batch.set(doc(firestore, `secureOrgs/${orgId}`), {
    name: "Private group",
    ownerUid,
    memberCount: 1,
    memberCountAuditEventId: "org-created",
    createdAt: serverTimestamp(),
  });
  batch.set(
    doc(firestore, `secureOrgs/${orgId}/members/${ownerUid}`),
    secureMember(ownerUid, ownerUid, "owner", "Private group", "org-created"),
  );
  batch.set(doc(firestore, `secureOrgs/${orgId}/groupChats/main`), {
    createdBy: ownerUid,
    createdAt: serverTimestamp(),
  });
  batch.set(doc(firestore, `secureOrgs/${orgId}/auditLog/org-created`), {
    actorUid: ownerUid,
    targetUid: ownerUid,
    action: "org_created",
    createdAt: serverTimestamp(),
  });
  await batch.commit();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), `secureOrgs/${orgId}`), {
      plan: {
        seatLimit,
        activeUntil: new Date(Date.now() + 3_600_000),
      },
    });
  });
}

function circleGroup({
  members = ["alice"],
  admins = ["alice"],
  name = "Friends",
  creatorUid = "alice",
} = {}) {
  return {
    members,
    admins,
    createdBy: creatorUid,
    name,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    lastMessage: "",
    lastMessageSenderUid: "",
    lastMessageAt: serverTimestamp(),
    lastMessageId: "",
  };
}

async function createCircleGroup(groupId = "friends") {
  await assertSucceeds(
    setDoc(doc(user("alice"), `circleGroups/${groupId}`), circleGroup()),
  );
}

test("Shield org creation requires its server-controlled flag and owner entitlement", async () => {
  await setSecureFeatureEnabled(true);
  await assertSucceeds(getDoc(doc(secureUser("alice"), "secureOrgConfig/superSecure")));
  await assertFails(setDoc(doc(secureUser("alice"), "secureOrgConfig/superSecure"), {
    enabled: false,
  }));
  await assertFails(createSecureOrg("secure-no-shield-claim", "alice", false, {}, 25, false));
  await assertFails(createSecureOrg("secure-ineligible-owner", "alice", false, {
    dostAgeEligible: false,
  }));
  await assertFails(createSecureOrg("secure-expired-owner", "alice", false, {
    dostAgeEligibleUntil: Math.floor(Date.now() / 1000) - 1,
  }));
  await assertFails(createSecureOrg("secure-expired-shield-owner", "alice", false, {
    dostShieldUntil: Math.floor(Date.now() / 1000) - 1,
  }));
  await assertFails(createSecureOrg("secure-unpaid", "alice", false, {
    dostShieldUntil: 1,
    dostShieldSeats: 0,
  }));
  await assertSucceeds(createSecureOrg("secure-paid", "alice", false));
});

test("Shield remains unavailable until its server-controlled flag is enabled", async () => {
  await assertFails(createSecureOrg("secure-disabled", "alice", false));
  await assertFails(setDoc(doc(secureUser("alice"), "secureOrgConfig/superSecure"), {
    enabled: true,
  }));

  await setSecureFeatureEnabled(true);
  await assertSucceeds(createSecureOrg("secure-enabled", "alice", false));
  await setSecureFeatureEnabled(false);

  await assertFails(getDoc(doc(secureUser("alice"), "secureOrgs/secure-enabled")));
  await assertFails(getDoc(doc(secureUser("alice"), "secureOrgs/secure-enabled/members/alice")));
  await assertFails(setDoc(doc(secureUser("alice"), "secureOrgs/secure-enabled/groupChats/main/messages/disabled"), {
    senderUid: "alice",
    text: "The service is disabled.",
    createdAt: serverTimestamp(),
  }));
});

async function addSecureOrgMember(
  actorUid,
  orgId,
  targetUid,
  role = "member",
  actorClaims = {},
  targetAgeCohort = "adult",
) {
  const firestore = secureUser(actorUid, actorClaims);
  const inviteRef = doc(firestore, `secureOrgs/${orgId}/invites/${targetUid}`);
  await assertSucceeds(setDoc(inviteRef, {
    inviteeUid: targetUid,
    displayName: targetUid,
    invitedBy: actorUid,
    status: "pending",
    createdAt: serverTimestamp(),
  }));
  const invitee = user(targetUid, { dostAgeCohort: targetAgeCohort });
  await assertSucceeds(updateDoc(doc(invitee, `secureOrgs/${orgId}/invites/${targetUid}`), {
    status: "accepted",
    acceptedAgeCohort: targetAgeCohort,
    acceptedAt: serverTimestamp(),
  }));
  const eventId = `evt-${targetUid}-${++secureAuditSequence}`;
  const organization = await getDoc(doc(firestore, `secureOrgs/${orgId}`));
  const memberCount = organization.get("memberCount");
  const memberRef = doc(firestore, `secureOrgs/${orgId}/members/${targetUid}`);
  const existingMember = await getDoc(memberRef);
  const batch = writeBatch(firestore);
  batch.update(doc(firestore, `secureOrgs/${orgId}`), {
    memberCount: memberCount + 1,
    memberCountAuditEventId: eventId,
  });
  const fields = secureMember(targetUid, actorUid, role, "Private group", eventId);
  if (existingMember.exists()) {
    delete fields.createdAt;
    batch.update(memberRef, fields);
  } else {
    batch.set(memberRef, fields);
  }
  batch.set(doc(firestore, `secureOrgs/${orgId}/auditLog/${eventId}`), {
    actorUid,
    targetUid,
    action: role === "manager" ? "manager_added" : "member_added",
    createdAt: serverTimestamp(),
  });
  return batch.commit();
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

test("Shield organization data is private to current members", async () => {
  await createSecureOrg("secure-a", "alice");
  await addSecureOrgMember("alice", "secure-a", "bob");
  await createSecureOrg("secure-b", "charlie");

  await assertSucceeds(getDoc(doc(secureUser("alice"), "secureOrgs/secure-a")));
  await assertSucceeds(getDoc(doc(secureUser("bob"), "secureOrgs/secure-a")));
  await assertSucceeds(getDoc(doc(secureUser("bob"), "secureOrgs/secure-a/members/alice")));
  await assertFails(getDoc(doc(secureUser("mallory"), "secureOrgs/secure-a")));
  await assertFails(getDoc(doc(secureUser("alice"), "secureOrgs/secure-b")));
  await assertFails(getDocs(collection(secureUser("alice"), "secureOrgs")));
  await assertFails(getDoc(doc(testEnv.unauthenticatedContext().firestore(), "secureOrgs/secure-a")));
  await assertSucceeds(setDoc(doc(secureUser("alice"), "secureOrgs/secure-a/groupChats/main/messages/group-message"), {
    senderUid: "alice",
    text: "Organization-only group text.",
    createdAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(secureUser("mallory"), "secureOrgs/secure-a/groupChats/main/messages/outsider"), {
    senderUid: "mallory",
    text: "Not a member.",
    createdAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(secureUser("alice"), "secureOrgs/secure-b/groupChats/main/messages/cross-org"), {
    senderUid: "alice",
    text: "Not in this organization.",
    createdAt: serverTimestamp(),
  }));

  await assertSucceeds(setDoc(doc(secureUser("bob"), "secureOrgs/secure-a/directChats/alice-bob"), {
    members: ["alice", "bob"],
    createdBy: "bob",
    createdAt: serverTimestamp(),
  }));
  await assertSucceeds(setDoc(doc(secureUser("alice"), "secureOrgs/secure-a/directChats/alice-bob/messages/direct-message"), {
    senderUid: "alice",
    text: "Private to this pair.",
    createdAt: serverTimestamp(),
  }));
  await assertFails(getDoc(doc(secureUser("mallory"), "secureOrgs/secure-a/directChats/alice-bob")));
  await addSecureOrgMember("alice", "secure-a", "carol");
  await addSecureOrgMember("alice", "secure-a", "manager", "manager");
  await assertSucceeds(setDoc(doc(secureUser("bob"), "secureOrgs/secure-a/directChats/bob-carol"), {
    members: ["bob", "carol"],
    createdBy: "bob",
    createdAt: serverTimestamp(),
  }));
  await assertSucceeds(setDoc(doc(secureUser("carol"), "secureOrgs/secure-a/directChats/bob-carol/messages/owner-export"), {
    senderUid: "carol",
    text: "Owner export access.",
    createdAt: serverTimestamp(),
  }));
  await assertSucceeds(getDoc(doc(secureUser("alice"), "secureOrgs/secure-a/directChats/bob-carol")));
  await assertSucceeds(getDoc(doc(secureUser("alice"), "secureOrgs/secure-a/directChats/bob-carol/messages/owner-export")));
  await assertSucceeds(getDocs(collection(secureUser("alice"), "secureOrgs/secure-a/directChats")));
  await assertSucceeds(getDocs(collection(
    secureUser("alice"),
    "secureOrgs/secure-a/directChats/bob-carol/messages",
  )));
  await assertFails(getDoc(doc(secureUser("manager"), "secureOrgs/secure-a/directChats/bob-carol")));
  await assertFails(getDoc(doc(secureUser("manager"), "secureOrgs/secure-a/directChats/bob-carol/messages/owner-export")));
  await assertFails(setDoc(doc(secureUser("alice"), "secureOrgs/secure-a/directChats/alice-bob/messages/too-long"), {
    senderUid: "alice",
    text: "x".repeat(4_001),
    createdAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(secureUser("alice"), "secureOrgs/secure-a/directChats/alice-mallory"), {
    members: ["alice", "mallory"],
    createdBy: "alice",
    createdAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(secureUser("alice"), "secureOrgs/secure-b/directChats/alice-charlie"), {
    members: ["alice", "charlie"],
    createdBy: "alice",
    createdAt: serverTimestamp(),
  }));
});

test("only managers administer members, changes are audited, and removal revokes chat access", async () => {
  await createSecureOrg("secure-a", "alice");
  await addSecureOrgMember("alice", "secure-a", "manager", "manager");
  await assertSucceeds(addSecureOrgMember("manager", "secure-a", "bob", "member", {
    dostShieldUntil: 1,
    dostShieldSeats: 0,
  }));
  assert.equal((await getDoc(doc(secureUser("alice"), "secureOrgs/secure-a"))).get("memberCount"), 3);

  await assertFails(addSecureOrgMember("manager", "secure-a", "new-manager", "manager"));
  await assertSucceeds(addSecureOrgMember("alice", "secure-a", "carol"));
  const alice = secureUser("alice");
  const carolMember = doc(alice, "secureOrgs/secure-a/members/carol");
  const staleCarolAuditId = (await getDoc(carolMember)).get("auditEventId");
  await assertFails(
    updateDoc(doc(secureUser("bob"), "secureOrgs/secure-a/members/bob"), {
      role: "manager",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(
    setDoc(doc(secureUser("bob"), "secureOrgs/secure-a/invites/another-user"), {
      uid: "another-user",
    }),
  );
  await assertFails(
    setDoc(doc(secureUser("bob"), "secureOrgs/secure-a/auditLog/fake"), {
      actorUid: "bob",
      targetUid: "another-user",
      action: "member_added",
      createdAt: serverTimestamp(),
    }),
  );

  const firestore = secureUser("manager");
  const batch = writeBatch(firestore);
  batch.update(doc(firestore, "secureOrgs/secure-a/members/bob"), {
    role: "member",
    status: "removed",
    actorUid: "manager",
    auditEventId: "evt-remove-bob",
    updatedAt: serverTimestamp(),
  });
  batch.set(doc(firestore, "secureOrgs/secure-a/auditLog/evt-remove-bob"), {
    actorUid: "manager",
    targetUid: "bob",
    action: "member_removed",
    createdAt: serverTimestamp(),
  });
  const organization = await getDoc(doc(firestore, "secureOrgs/secure-a"));
  batch.update(doc(firestore, "secureOrgs/secure-a"), {
    memberCount: organization.get("memberCount") - 1,
    memberCountAuditEventId: "evt-remove-bob",
  });
  await assertSucceeds(batch.commit());

  assert.equal((await getDoc(doc(secureUser("alice"), "secureOrgs/secure-a"))).get("memberCount"), 3);
  await assertSucceeds(getDoc(doc(secureUser("manager"), "secureOrgs/secure-a/auditLog/evt-remove-bob")));
  await assertFails(getDoc(doc(secureUser("bob"), "secureOrgs/secure-a/auditLog/evt-remove-bob")));
  await assertFails(getDoc(doc(secureUser("carol"), "secureOrgs/secure-a/members/bob")));
  const promotion = writeBatch(alice);
  promotion.update(carolMember, {
    role: "manager",
    actorUid: "alice",
    auditEventId: "evt-promote-carol",
    updatedAt: serverTimestamp(),
  });
  promotion.set(doc(alice, "secureOrgs/secure-a/auditLog/evt-promote-carol"), {
    actorUid: "alice",
    targetUid: "carol",
    action: "manager_promoted",
    createdAt: serverTimestamp(),
  });
  await assertSucceeds(promotion.commit());
  await assertFails(
    updateDoc(doc(secureUser("manager"), "secureOrgs/secure-a/members/carol"), {
      role: "manager",
      status: "removed",
      actorUid: "manager",
      auditEventId: staleCarolAuditId,
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(getDocs(collection(secureUser("bob"), "secureOrgs/secure-a/members")));
  const activeMembers = await assertSucceeds(getDocs(query(
    collection(secureUser("carol"), "secureOrgs/secure-a/members"),
    where("status", "==", "active"),
  )));
  assert.deepEqual(activeMembers.docs.map((entry) => entry.id).sort(), ["alice", "carol", "manager"]);
  await assertFails(getDocs(collection(secureUser("bob"), "secureOrgs/secure-a/groupChats/main/messages")));
  await assertFails(setDoc(doc(secureUser("bob"), "secureOrgs/secure-a/groupChats/main/messages/after-removal"), {
    senderUid: "bob",
    text: "Removed members cannot send.",
    createdAt: serverTimestamp(),
  }));
  await assertFails(
    updateDoc(doc(secureUser("alice"), "secureOrgs/secure-a/members/alice"), {
      role: "member",
      status: "removed",
      actorUid: "alice",
      auditEventId: "evt-remove-owner",
      updatedAt: serverTimestamp(),
    }),
  );

  await assertSucceeds(addSecureOrgMember("manager", "secure-a", "bob"));
  assert.equal((await getDoc(doc(secureUser("alice"), "secureOrgs/secure-a"))).get("memberCount"), 4);
});

test("the Firestore boundary enforces the 25-active-member limit", async () => {
  await createSecureOrg("secure-full", "alice", true, {}, 25);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "secureOrgs/secure-full"), {
      memberCount: 25,
    });
  });

  await assertFails(addSecureOrgMember("alice", "secure-full", "bob"));
});

test("Shield Team applies its synchronized ten-seat limit", async () => {
  await createSecureOrg("secure-team-cap", "alice", true, {}, 10);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "secureOrgs/secure-team-cap"), {
      memberCount: 10,
    });
  });
  await assertFails(addSecureOrgMember("alice", "secure-team-cap", "member-11"));
});

test("Shield Business blocks the 26th active member", async () => {
  await createSecureOrg("secure-business-cap", "alice", true, {}, 25);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "secureOrgs/secure-business-cap"), {
      memberCount: 25,
    });
  });
  await assertFails(addSecureOrgMember("alice", "secure-business-cap", "member-26"));
});

test("a downgraded Shield org can remove members until it reaches its new seat cap", async () => {
  await createSecureOrg("secure-downgraded", "alice", true, {}, 25);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "secureOrgs/secure-downgraded"), {
      memberCount: 12,
    });
    await setDoc(doc(context.firestore(), "secureOrgs/secure-downgraded/members/member-12"), secureMember(
      "member-12",
      "alice",
      "member",
    ));
  });
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "secureOrgs/secure-downgraded"), {
      plan: { seatLimit: 10, activeUntil: new Date(Date.now() + 3_600_000) },
    });
  });

  await assertFails(addSecureOrgMember("alice", "secure-downgraded", "member-new"));
  await assertFails(setDoc(doc(
    secureUser("alice"),
    "secureOrgs/secure-downgraded/groupChats/main/messages/over-seat-limit",
  ), {
    senderUid: "alice",
    text: "Sending is blocked until the team is within its downgraded seat cap.",
    createdAt: serverTimestamp(),
  }));
  const firestore = secureUser("alice");
  const batch = writeBatch(firestore);
  batch.update(doc(firestore, "secureOrgs/secure-downgraded/members/member-12"), {
    status: "removed",
    actorUid: "alice",
    auditEventId: "evt-downgrade-removal",
    updatedAt: serverTimestamp(),
  });
  batch.set(doc(firestore, "secureOrgs/secure-downgraded/auditLog/evt-downgrade-removal"), {
    actorUid: "alice",
    targetUid: "member-12",
    action: "member_removed",
    createdAt: serverTimestamp(),
  });
  batch.update(doc(firestore, "secureOrgs/secure-downgraded"), {
    memberCount: 11,
    memberCountAuditEventId: "evt-downgrade-removal",
  });
  await assertSucceeds(batch.commit());
  assert.equal((await getDoc(doc(firestore, "secureOrgs/secure-downgraded"))).get("memberCount"), 11);
  await assertFails(setDoc(doc(
    firestore,
    "secureOrgs/secure-downgraded/groupChats/main/messages/still-over-seat-limit",
  ), {
    senderUid: "alice",
    text: "Members must be removed until the org is within the seat cap.",
    createdAt: serverTimestamp(),
  }));
});

test("Shield permits 13+ members without granting them an owner plan", async () => {
  await createSecureOrg("secure-teen", "alice");
  await assertFails(setDoc(doc(
    secureUser("alice"),
    "secureOrgs/secure-teen/members/teen",
  ), secureMember("teen", "alice", "member")));
  await assertSucceeds(addSecureOrgMember("alice", "secure-teen", "teen", "member", {}, "teen"));
  const teen = secureUser("teen", {
    dostAgeEligible: false,
    dostAgeEligibleUntil: 1,
    dostAgeCohort: "teen",
  });
  await assertSucceeds(getDoc(doc(teen, "secureOrgs/secure-teen")));
  await assertSucceeds(setDoc(doc(
    teen,
    "secureOrgs/secure-teen/groupChats/main/messages/teen-message",
  ), {
    senderUid: "teen",
    text: "Invited teen members can participate.",
    createdAt: serverTimestamp(),
  }));
});

test("Shield group and direct blocks immediately restrict reads and sends", async () => {
  await createSecureOrg("secure-blocks", "alice");
  await addSecureOrgMember("alice", "secure-blocks", "bob");
  await assertSucceeds(setDoc(doc(
    secureUser("alice"),
    "secureOrgs/secure-blocks/groupChats/main/blockedUsers/bob",
  ), {
    blockedUid: "bob",
    blockedByUid: "alice",
    createdAt: serverTimestamp(),
  }));
  await assertFails(getDoc(doc(
    secureUser("bob"),
    "secureOrgs/secure-blocks/groupChats/main",
  )));
  await assertFails(setDoc(doc(
    secureUser("bob"),
    "secureOrgs/secure-blocks/groupChats/main/messages/blocked",
  ), {
    senderUid: "bob",
    text: "This must be blocked.",
    createdAt: serverTimestamp(),
  }));

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "secureOrgs/secure-blocks/directChats/alice-bob"), {
      members: ["alice", "bob"],
      createdBy: "alice",
      createdAt: new Date(),
    });
  });
  await assertSucceeds(setDoc(doc(user("alice"), "peopleBlocks/alice/blockedUsers/bob"), {
    createdAt: serverTimestamp(),
  }));
  await assertFails(getDoc(doc(
    secureUser("bob"),
    "secureOrgs/secure-blocks/directChats/alice-bob",
  )));
  await assertFails(getDoc(doc(
    secureUser("alice"),
    "secureOrgs/secure-blocks/directChats/alice-bob",
  )));
  await assertFails(setDoc(doc(
    secureUser("bob"),
    "secureOrgs/secure-blocks/directChats/alice-bob/messages/blocked",
  ), {
    senderUid: "bob",
    text: "This must be blocked.",
    createdAt: serverTimestamp(),
  }));
  await assertFails(getDocs(collection(
    secureUser("alice"),
    "secureOrgs/secure-blocks/directChats/alice-bob/messages",
  )));
});

test("Shield messaging is allowed during the 14-day grace period and read-only afterward", async () => {
  await createSecureOrg("secure-grace", "alice");
  await addSecureOrgMember("alice", "secure-grace", "bob");
  await assertSucceeds(setDoc(doc(
    secureUser("alice"),
    "secureOrgs/secure-grace/directChats/during-grace",
  ), {
    members: ["alice", "bob"],
    createdBy: "alice",
    createdAt: serverTimestamp(),
  }));
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "secureOrgs/secure-grace"), {
      plan: {
        seatLimit: 10,
        activeUntil: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000),
      },
    });
  });
  await assertSucceeds(setDoc(doc(
    secureUser("alice"),
    "secureOrgs/secure-grace/groupChats/main/messages/during-grace",
  ), {
    senderUid: "alice",
    text: "The org remains writable during grace.",
    createdAt: serverTimestamp(),
  }));
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "secureOrgs/secure-grace"), {
      plan: {
        seatLimit: 10,
        activeUntil: new Date(Date.now() - 15 * 24 * 60 * 60 * 1000),
      },
    });
  });
  await assertSucceeds(getDoc(doc(secureUser("alice"), "secureOrgs/secure-grace")));
  await assertFails(setDoc(doc(
    secureUser("alice"),
    "secureOrgs/secure-grace/directChats/after-grace",
  ), {
    members: ["alice", "bob"],
    createdBy: "alice",
    createdAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(
    secureUser("alice"),
    "secureOrgs/secure-grace/groupChats/main/messages/after-grace",
  ), {
    senderUid: "alice",
    text: "The org is read-only after grace.",
    createdAt: serverTimestamp(),
  }));
});

test("a user can create a private profile but cannot read another private profile", async () => {
  await assertSucceeds(
    setDoc(doc(user("alice"), "publicProfiles/alice"), profile({ isPublic: false })),
  );
  await assertFails(getDoc(doc(user("bob"), "publicProfiles/alice")));
});

test("teen Circle privacy defaults keep handles, presence, and photos private", async () => {
  const teen = user("teen", { dostAgeCohort: "teen" });
  await assertSucceeds(setDoc(doc(teen, "circleProfiles/teen"), {
    uid: "teen",
    ageCohort: "teen",
    handleLookupEnabled: false,
    lastSeenVisibility: "nobody",
    onlineVisibility: "nobody",
    photoVisibility: "contacts",
    updatedAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(teen, "circleProfiles/teen"), {
    uid: "teen",
    ageCohort: "teen",
    handleLookupEnabled: true,
    lastSeenVisibility: "contacts",
    onlineVisibility: "contacts",
    photoVisibility: "contacts",
    updatedAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(teen, "circleHandles/teen-handle"), {
    ownerUid: "teen",
    createdAt: serverTimestamp(),
  }));

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const firestore = context.firestore();
    await setDoc(doc(firestore, "circleHandles/teen-handle"), { ownerUid: "teen" });
    await setDoc(doc(firestore, "circlePresence/teen"), {
      lastSeenAt: serverTimestamp(),
      online: true,
      updatedAt: serverTimestamp(),
    });
    await setDoc(doc(firestore, "circleProfilePhotos/teen"), {
      photoUrl: "https://example.invalid/photo",
      updatedAt: serverTimestamp(),
    });
    await setDoc(doc(firestore, "circleContacts/teen/contacts/alice"), { status: "accepted" });
    await setDoc(doc(firestore, "circleContacts/alice/contacts/teen"), { status: "accepted" });
  });
  await assertSucceeds(getDoc(doc(teen, "circleProfiles/teen")));
  await assertSucceeds(getDoc(doc(teen, "circlePresence/teen")));
  await assertSucceeds(getDoc(doc(teen, "circleProfilePhotos/teen")));
  await assertSucceeds(getDoc(doc(user("alice"), "circleProfilePhotos/teen")));
  await assertFails(getDoc(doc(user("alice"), "circleProfiles/teen")));
  await assertFails(getDoc(doc(user("alice"), "circleHandles/teen-handle")));
  await assertFails(getDoc(doc(user("alice"), "circlePresence/teen")));
  await assertFails(getDoc(doc(user("mallory"), "circleProfilePhotos/teen")));

  await assertSucceeds(setDoc(doc(teen, "circleProfiles/teen"), {
    uid: "teen",
    ageCohort: "teen",
    handleLookupEnabled: true,
    lastSeenVisibility: "nobody",
    onlineVisibility: "nobody",
    photoVisibility: "contacts",
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(getDoc(doc(user("alice"), "circleHandles/teen-handle")));
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
  await assertFails(
    setDoc(doc(expired, "publicProfiles/expired-user"), profile()),
  );
});

test("teen and cohort-less claims cannot access People discovery or chats", async () => {
  await seedProfile("adult");
  await seedChat({ members: ["teen", "adult"] });
  const teen = user("teen", { dostAgeCohort: "teen" });
  const missingCohort = user("adult", { dostAgeCohort: null });

  await assertFails(getDoc(doc(teen, "publicProfiles/adult")));
  await assertFails(getDoc(doc(teen, "peopleChats/alice-bob")));
  await assertFails(getDoc(doc(missingCohort, "publicProfiles/adult")));
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

test("message push tokens are private and writable only by their owner", async () => {
  const tokenRef = doc(user("alice"), "peopleMessageTokens/alice/devices/device-1");
  await assertSucceeds(setDoc(tokenRef, {
    token: "fcm-registration-token".padEnd(64, "z"),
    updatedAt: serverTimestamp(),
  }));
  await assertFails(getDoc(doc(user("bob"), "peopleMessageTokens/alice/devices/device-1")));
  await assertFails(setDoc(doc(user("bob"), "peopleMessageTokens/alice/devices/device-2"), {
    token: "fcm-registration-token".padEnd(64, "w"),
    updatedAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(user("alice"), "peopleMessageTokens/alice/devices/device-3"), {
    token: "fcm-registration-token".padEnd(64, "v"),
    updatedAt: serverTimestamp(),
    text: "must not be stored in a push token",
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
  const sent = await assertSucceeds(getDocs(query(
    collectionGroup(user("alice"), "incoming"),
    where("senderUid", "==", "alice"),
    where("status", "in", ["pending", "accepted", "ignored"]),
    orderBy("updatedAt", "desc"),
  )));
  assert.equal(sent.size, 1);
  await assertFails(getDocs(query(
    collectionGroup(user("mallory"), "incoming"),
    where("senderUid", "==", "alice"),
    where("status", "in", ["pending", "accepted", "ignored"]),
    orderBy("updatedAt", "desc"),
  )));
  await assertFails(setDoc(doc(user("bob"), "peopleContactRequests/alice/incoming/bob"), {
    senderUid: "bob",
    recipientUid: "alice",
    conversationId: "alice-bob",
    status: "pending",
    senderProfile: snapshot({ displayName: "Bob", contactPolicy: "requests" }),
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

test("match-first profiles require an accepted expression of interest before chat creation", async () => {
  await seedProfile("alice", { displayName: "Alice" });
  await seedProfile("bob", { displayName: "Bob", contactPolicy: "matches" });
  const chat = {
    members: ["alice", "bob"],
    ageCohort: "adult",
    memberProfiles: {
      alice: snapshot({ displayName: "Alice" }),
      bob: snapshot({ displayName: "Bob", contactPolicy: "matches" }),
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

  const approval = {
    conversationId: "alice-bob",
    recipientUid: "bob",
    senderUid: "alice",
    members: ["alice", "bob"],
    createdBy: "bob",
    createdAt: serverTimestamp(),
  };
  await assertFails(setDoc(doc(user("bob"), "peopleContactApprovals/alice-bob"), approval));

  const request = doc(user("alice"), "peopleContactRequests/bob/incoming/alice");
  await assertSucceeds(setDoc(request, {
    senderUid: "alice",
    recipientUid: "bob",
    conversationId: "alice-bob",
    status: "pending",
    senderProfile: snapshot({ displayName: "Alice" }),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));
  const bobFirestore = user("bob");
  const accept = writeBatch(bobFirestore);
  accept.update(doc(bobFirestore, "peopleContactRequests/bob/incoming/alice"), {
    status: "accepted",
    respondedBy: "bob",
    updatedAt: serverTimestamp(),
  });
  accept.set(doc(bobFirestore, "peopleContactApprovals/alice-bob"), approval);
  accept.set(doc(bobFirestore, "peopleChats/alice-bob"), { ...chat, createdBy: "bob" });
  await assertSucceeds(accept.commit());
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
    priorityReview: true,
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

test("Circle group creation is private and group membership is capped", async () => {
  await assertSucceeds(
    setDoc(doc(user("alice"), "circleGroups/friends"), circleGroup()),
  );
  const teen = user("teen", { dostAgeCohort: "teen" });
  await assertSucceeds(setDoc(doc(teen, "circleGroups/teen-friends"), circleGroup({
    members: ["teen"],
    admins: ["teen"],
    creatorUid: "teen",
  })));
  await assertSucceeds(setDoc(doc(teen, "peopleBlocks/teen/blockedUsers/alice"), {
    createdAt: serverTimestamp(),
  }));
  await assertFails(
    getDoc(doc(user("mallory"), "circleGroups/friends")),
  );
  await assertFails(
    setDoc(doc(user("bob"), "circleGroups/invalid"), circleGroup({
      members: ["alice", "bob"],
      admins: ["alice"],
    })),
  );
});

test("blocking a Circle group member immediately denies group access and messages", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "circleGroups/friends"), circleGroup({
      members: ["alice", "bob"],
    }));
  });
  await assertSucceeds(setDoc(doc(user("alice"), "circleGroups/friends/blockedUsers/bob"), {
    blockedUid: "bob",
    blockedByUid: "alice",
    createdAt: serverTimestamp(),
  }));
  await assertFails(getDoc(doc(user("bob"), "circleGroups/friends")));
  await assertFails(getDocs(collection(user("bob"), "circleGroups/friends/messages")));
  await assertFails(setDoc(doc(user("bob"), "circleGroups/friends/blockedUsers/alice"), {
    blockedUid: "alice",
    blockedByUid: "bob",
    createdAt: serverTimestamp(),
  }));
});

test("Circle group invites require admin authority and invitee acceptance", async () => {
  await createCircleGroup();
  const alice = user("alice");
  const teen = user("teen", { dostAgeCohort: "teen" });
  const invite = doc(alice, "circleGroups/friends/invites/teen");
  await assertFails(
    setDoc(doc(user("mallory"), "circleGroups/friends/invites/charlie"), {
      groupId: "friends",
      inviteeUid: "charlie",
      invitedBy: "mallory",
      status: "pending",
      createdAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(setDoc(invite, {
    groupId: "friends",
    groupName: "Friends",
    inviteeUid: "teen",
    invitedBy: "alice",
    status: "pending",
    createdAt: serverTimestamp(),
  }));
  await assertFails(
    updateDoc(doc(user("alice"), "circleGroups/friends"), {
      members: ["alice", "teen"],
      updatedAt: serverTimestamp(),
    }),
  );
  const batch = writeBatch(teen);
  batch.update(doc(teen, "circleGroups/friends/invites/teen"), {
    status: "accepted",
    acceptedAt: serverTimestamp(),
  });
  batch.update(doc(teen, "circleGroups/friends"), {
    members: ["alice", "teen"],
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(batch.commit());
  await assertSucceeds(getDoc(doc(teen, "circleGroups/friends")));
  await assertFails(getDoc(doc(user("mallory"), "circleGroups/friends")));
  const leave = writeBatch(teen);
  leave.update(doc(teen, "circleGroups/friends"), {
    members: ["alice"],
    admins: ["alice"],
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(leave.commit());
  await assertFails(
    updateDoc(doc(teen, "circleGroups/friends"), {
      members: ["alice", "teen"],
      updatedAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(
    setDoc(doc(user("alice"), "circleGroups/friends/invites/teen"), {
      groupId: "friends",
      groupName: "Friends",
      inviteeUid: "teen",
      invitedBy: "alice",
      status: "pending",
      createdAt: serverTimestamp(),
    }),
  );
  const rejoin = writeBatch(teen);
  rejoin.update(doc(teen, "circleGroups/friends/invites/teen"), {
    status: "accepted",
    acceptedAt: serverTimestamp(),
  });
  rejoin.update(doc(teen, "circleGroups/friends"), {
    members: ["alice", "teen"],
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(rejoin.commit());
});

test("Circle group invites reject blocked people and cannot exceed 32 members", async () => {
  await createCircleGroup();
  await assertSucceeds(
    setDoc(doc(user("alice"), "peopleBlocks/alice/blockedUsers/bob"), {
      createdAt: serverTimestamp(),
    }),
  );
  await assertFails(
    setDoc(doc(user("alice"), "circleGroups/friends/invites/bob"), {
      groupId: "friends",
      inviteeUid: "bob",
      invitedBy: "alice",
      status: "pending",
      createdAt: serverTimestamp(),
    }),
  );
  const members = ["alice", ...Array.from({ length: 31 }, (_, index) => `member-${index}`)];
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "circleGroups/at-capacity"), {
      ...circleGroup({ members }),
    });
  });
  await assertSucceeds(
    setDoc(doc(user("alice"), "circleGroups/at-capacity/invites/extra"), {
      groupId: "at-capacity",
      groupName: "Friends",
      inviteeUid: "extra",
      invitedBy: "alice",
      status: "pending",
      createdAt: serverTimestamp(),
    }),
  );
  const extra = user("extra");
  const batch = writeBatch(extra);
  batch.update(doc(extra, "circleGroups/at-capacity/invites/extra"), {
    status: "accepted",
    acceptedAt: serverTimestamp(),
  });
  batch.update(doc(extra, "circleGroups/at-capacity"), {
    members: [...members, "extra"],
    updatedAt: serverTimestamp(),
  });
  await assertFails(batch.commit());
});

test("Circle group admins can rename, members can leave, and outsiders cannot send", async () => {
  await createCircleGroup();
  const alice = user("alice");
  const rename = writeBatch(alice);
  rename.update(doc(alice, "circleGroups/friends"), {
    name: "Weekend group",
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(rename.commit());
  await assertFails(
    updateDoc(doc(user("bob"), "circleGroups/friends"), {
      name: "Changed by outsider",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(
    setDoc(doc(user("mallory"), "circleGroups/friends/messages/forged"), {
      senderUid: "mallory",
      text: "Not a member",
      createdAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(
    setDoc(doc(alice, "circleGroups/friends/invites/bob"), {
      groupId: "friends",
      groupName: "Weekend group",
      inviteeUid: "bob",
      invitedBy: "alice",
      status: "pending",
      createdAt: serverTimestamp(),
    }),
  );
  const bob = user("bob");
  const join = writeBatch(bob);
  join.update(doc(bob, "circleGroups/friends/invites/bob"), {
    status: "accepted",
    acceptedAt: serverTimestamp(),
  });
  join.update(doc(bob, "circleGroups/friends"), {
    members: ["alice", "bob"],
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(join.commit());
  await assertFails(
    updateDoc(doc(alice, "circleGroups/friends"), {
      members: ["bob"],
      admins: [],
      updatedAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(
    updateDoc(doc(alice, "circleGroups/friends"), {
      admins: ["alice", "bob"],
      updatedAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(
    updateDoc(doc(bob, "circleGroups/friends"), {
      name: "Renamed by admin",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(
    updateDoc(doc(alice, "circleGroups/friends"), {
      members: ["alice"],
      updatedAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(
    updateDoc(doc(alice, "circleGroups/friends"), {
      members: ["bob"],
      admins: ["bob"],
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(getDoc(doc(alice, "circleGroups/friends")));
  await assertSucceeds(getDoc(doc(bob, "circleGroups/friends")));
});

test("Circle group messages require membership and an atomic group summary update", async () => {
  await createCircleGroup();
  await assertSucceeds(
    setDoc(doc(user("alice"), "circleGroups/friends/invites/bob"), {
      groupId: "friends",
      groupName: "Friends",
      inviteeUid: "bob",
      invitedBy: "alice",
      status: "pending",
      createdAt: serverTimestamp(),
    }),
  );
  const bob = user("bob");
  const join = writeBatch(bob);
  join.update(doc(bob, "circleGroups/friends/invites/bob"), {
    status: "accepted",
    acceptedAt: serverTimestamp(),
  });
  join.update(doc(bob, "circleGroups/friends"), {
    members: ["alice", "bob"],
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(join.commit());

  const alice = user("alice");
  const send = writeBatch(alice);
  send.update(doc(alice, "circleGroups/friends"), {
    lastMessage: "Hello group",
    lastMessageSenderUid: "alice",
    lastMessageAt: serverTimestamp(),
    lastMessageId: "group-message-1",
  });
  send.set(doc(alice, "circleGroups/friends/messages/group-message-1"), {
    senderUid: "alice",
    text: "Hello group",
    createdAt: serverTimestamp(),
  });
  await assertSucceeds(send.commit());
  await assertFails(
    getDoc(doc(user("mallory"), "circleGroups/friends/messages/group-message-1")),
  );
  await assertFails(
    setDoc(doc(user("bob"), "circleGroups/friends/messages/unpaired"), {
      senderUid: "bob",
      text: "Unpaired update",
      createdAt: serverTimestamp(),
    }),
  );
});

test("Circle group invitees can list pending invites with a collection-group query", async () => {
  await createCircleGroup();
  await assertSucceeds(
    setDoc(doc(user("alice"), "circleGroups/friends/invites/teen"), {
      groupId: "friends",
      groupName: "Friends",
      inviteeUid: "teen",
      invitedBy: "alice",
      status: "pending",
      createdAt: serverTimestamp(),
    }),
  );
  const teen = user("teen", { dostAgeCohort: "teen" });
  const pending = await assertSucceeds(getDocs(query(
    collectionGroup(teen, "invites"),
    where("inviteeUid", "==", "teen"),
    where("status", "==", "pending"),
  )));
  assert.equal(pending.size, 1);
  assert.equal(pending.docs[0].get("groupId"), "friends");
  await assertSucceeds(getDoc(doc(teen, "circleGroups/friends/invites/teen")));
  await assertFails(getDocs(query(
    collectionGroup(user("mallory"), "invites"),
    where("inviteeUid", "==", "teen"),
    where("status", "==", "pending"),
  )));
  await assertFails(getDocs(query(
    collectionGroup(user("alice"), "invites"),
    where("inviteeUid", "==", "teen"),
    where("status", "==", "pending"),
  )));
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

  await assertFails(getDoc(doc(user("alice"), "peopleChats/alice-bob")));
  await assertFails(getDoc(doc(user("bob"), "peopleChats/alice-bob")));
  await assertFails(getDocs(collection(user("alice"), "peopleChats/alice-bob/messages")));
  await assertFails(getDocs(collection(user("bob"), "peopleChats/alice-bob/messages")));
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
    priorityReview: true,
    createdAt: serverTimestamp(),
  };

  await assertSucceeds(setDoc(doc(user("alice"), "peopleReports/report-1"), report));
  await assertFails(setDoc(doc(user("alice"), "peopleReports/unprioritized-report"), {
    ...report,
    priorityReview: false,
  }));
  await assertFails(
    setDoc(doc(user("mallory"), "peopleReports/report-2"), {
      ...report,
      reporterUid: "mallory",
    }),
  );
  await assertFails(getDoc(doc(user("alice"), "peopleReports/report-1")));
  await assertFails(getDocs(collection(user("bob"), "peopleReports")));
});

test("public profile reports require public adult profiles and a single profile context", async () => {
  await seedProfile("alice");
  await seedProfile("bob");
  await seedProfile("hidden", { isPublic: false });
  await seedProfile("minor", { ageBand: "13–17", ageCohort: "minor" });
  await seedProfile("private-reporter", { isPublic: false });

  const report = {
    reporterUid: "alice",
    targetUid: "bob",
    profileUid: "bob",
    reason: "Profile impersonation",
    priorityReview: true,
    createdAt: serverTimestamp(),
  };
  await assertSucceeds(
    setDoc(doc(user("alice"), "peopleReports/profile-report"), report),
  );
  await assertFails(
    setDoc(doc(user("alice"), "peopleReports/hidden-profile-report"), {
      ...report,
      targetUid: "hidden",
      profileUid: "hidden",
    }),
  );
  await assertFails(
    setDoc(doc(user("alice"), "peopleReports/minor-profile-report"), {
      ...report,
      targetUid: "minor",
      profileUid: "minor",
    }),
  );
  await assertFails(
    setDoc(doc(user("private-reporter"), "peopleReports/private-reporter-report"), {
      ...report,
      reporterUid: "private-reporter",
    }),
  );
  await assertFails(
    setDoc(doc(user("alice"), "peopleReports/mixed-context-report"), {
      ...report,
      conversationId: "alice-bob",
    }),
  );
});

test("teen reports for Explore AI, Circle, Shield, and Cipher are priority-labelled", async () => {
  const teen = user("teen", { dostAgeCohort: "teen" });
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "circleGroups/teen-group"), circleGroup({
      members: ["teen", "bob"],
      admins: ["teen"],
      creatorUid: "teen",
    }));
  });
  await assertSucceeds(setDoc(doc(teen, "safetyReports/ai-report"), {
    reporterUid: "teen",
    surface: "explore_ai",
    targetId: "friend-1",
    reason: "Unsafe AI reply",
    priorityReview: true,
    createdAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(teen, "safetyReports/unprioritized-ai-report"), {
    reporterUid: "teen",
    surface: "explore_ai",
    targetId: "friend-1",
    reason: "Unsafe AI reply",
    priorityReview: false,
    createdAt: serverTimestamp(),
  }));
  await assertSucceeds(setDoc(doc(teen, "safetyReports/circle-report"), {
    reporterUid: "teen",
    surface: "circle_group",
    groupId: "teen-group",
    targetId: "bob",
    reason: "Harassing messages",
    priorityReview: true,
    createdAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(user("mallory"), "safetyReports/outsider-report"), {
    reporterUid: "mallory",
    surface: "circle_group",
    groupId: "teen-group",
    targetId: "bob",
    reason: "Harassing messages",
    priorityReview: true,
    createdAt: serverTimestamp(),
  }));

  await setSecureFeatureEnabled(true);
  await createSecureOrg("secure-reports", "alice");
  await addSecureOrgMember("alice", "secure-reports", "teen", "member", {}, "teen");
  await assertSucceeds(setDoc(doc(teen, "safetyReports/shield-report"), {
    reporterUid: "teen",
    surface: "shield",
    organizationId: "secure-reports",
    targetId: "alice",
    reason: "Inappropriate group messages",
    priorityReview: true,
    createdAt: serverTimestamp(),
  }));
  await assertSucceeds(setDoc(doc(user("alice"), "safetyReports/about-teen-report"), {
    reporterUid: "alice",
    surface: "shield",
    organizationId: "secure-reports",
    targetId: "teen",
    reason: "Safety concern",
    priorityReview: true,
    createdAt: serverTimestamp(),
  }));

  await setCipherFeatureEnabled(true);
  await seedCircleChat();
  await seedCipherChat();
  const cipherTeen = user("alice", { dostAgeCohort: "teen" });
  await assertSucceeds(setDoc(doc(cipherTeen, "safetyReports/cipher-report"), {
    reporterUid: "alice",
    surface: "cipher",
    conversationId: "circle-chat",
    targetId: "bob",
    reason: "Threatening Cipher messages",
    priorityReview: true,
    createdAt: serverTimestamp(),
  }));
});

test("Cipher chat creation requires its server claim and default-off feature flag", async () => {
  await seedCircleChat();
  const chat = cipherChat();
  const teenEntitled = user("alice", {
    dostAgeCohort: "teen",
    dostAgeEligible: false,
    dostAgeEligibleUntil: 0,
    dostCipherUntil: Math.floor(Date.now() / 1000) + 3600,
  });

  await assertFails(setDoc(doc(user("alice"), "cipherChats/circle-chat"), chat));
  await assertFails(setDoc(doc(teenEntitled, "cipherChats/circle-chat"), chat));
  await setCipherFeatureEnabled(true);
  await assertFails(setDoc(doc(user("alice"), "cipherChats/circle-chat"), chat));
  const expiredCipherClaim = user("alice", {
    dostCipherUntil: Math.floor(Date.now() / 1000) - 3600,
  });
  await assertFails(setDoc(doc(expiredCipherClaim, "cipherChats/circle-chat"), chat));
  await assertSucceeds(setDoc(doc(teenEntitled, "cipherChats/circle-chat"), chat));
});

test("Cipher chats are restricted to source Circle members and ciphertext-only messages", async () => {
  await setCipherFeatureEnabled(true);
  await seedCircleChat();
  await seedCipherChat();

  const ciphertext = {
    ct: "A".repeat(24),
    nonce: "A".repeat(16),
    v: 1,
    senderUid: "bob",
    createdAt: serverTimestamp(),
  };
  await assertSucceeds(
    getDoc(doc(user("alice", {
      dostAgeCohort: "teen",
      dostAgeEligible: false,
      dostAgeEligibleUntil: 0,
    }), "cipherChats/circle-chat/messages/message-1")),
  );
  await assertFails(getDoc(doc(user("mallory"), "cipherChats/circle-chat")));
  await assertSucceeds(
    setDoc(doc(user("bob"), "cipherChats/circle-chat/messages/message-1"), ciphertext),
  );
  await assertFails(
    setDoc(doc(user("mallory"), "cipherChats/circle-chat/messages/message-2"), {
      ...ciphertext,
      senderUid: "mallory",
    }),
  );
  await assertFails(
    setDoc(doc(user("bob"), "cipherChats/circle-chat/messages/plaintext"), {
      ...ciphertext,
      text: "must not be stored",
    }),
  );
  await assertFails(
    setDoc(doc(user("bob"), "cipherChats/circle-chat/messages/oversized"), {
      ...ciphertext,
      ct: "A".repeat(2689),
    }),
  );
});

test("blocking a Cipher participant denies chat access but still permits a safety report", async () => {
  await setCipherFeatureEnabled(true);
  await seedCircleChat();
  await seedCipherChat();
  await assertSucceeds(setDoc(doc(user("alice"), "peopleBlocks/alice/blockedUsers/bob"), {
    createdAt: serverTimestamp(),
  }));
  await assertFails(getDoc(doc(user("alice"), "cipherChats/circle-chat")));
  await assertSucceeds(setDoc(doc(user("alice"), "safetyReports/cipher-after-block"), {
    reporterUid: "alice",
    surface: "cipher",
    conversationId: "circle-chat",
    targetId: "bob",
    reason: "I need to report this person.",
    priorityReview: true,
    createdAt: serverTimestamp(),
  }));
});
