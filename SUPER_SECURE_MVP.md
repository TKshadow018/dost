# Shield (formerly Super Secure)

**Status: implementation draft; paid access and customer data collection are not approved.** Shield is build-flagged off by default. Do not enable it for customers until the launch gates below have been reviewed and accepted by the product owner, privacy counsel, and Firebase operator. This document records implementation assumptions for issues #67 and #73; it is not legal advice or a claim of independent security certification.

## MVP assumptions

| Topic | MVP default |
| --- | --- |
| Activation and billing | `DOST_SUPER_SECURE_ENABLED` and the server-side `plan_shield_enabled` switch are false by default. Firestore also denies Shield access unless an administrator enables `secureOrgConfig/superSecure`. This legacy config path controls Shield; `secureOrgs/{orgId}` remains the data namespace. Clients cannot write the config or organization `plan` field. |
| Age eligibility | Owners who create or pay for an organisation require the server-verified adult eligibility claim. Invited members aged 13+ require a trusted `dostAgeCohort` claim (`teen` or `adult`); membership does not require the adult purchasing claim. |
| Backend and data boundary | Use the existing Firebase project and Firestore region under the separate `secureOrgs/{orgId}` namespace. Shield is separate from People and AI collections, not a separate Firebase project or customer-controlled deployment. |
| Membership | The owner and managers admit people by Firebase UID. Organization IDs are shared out of band. There is no public directory, email lookup, member invite, or invite-code flow. Members cannot add or remove people. |
| Roles and size | One owner is created with each organisation. Only the owner can appoint or remove managers; an owner cannot be removed or demoted. The owner-paid Team plan allows 10 active seats; Business allows 25, counting owners and managers. Firestore rules enforce the mirrored plan limit atomically. |
| Conversations | One text-only group chat and text-only 1:1 chats between active members. Messages are limited to 4,000 characters; the live message view is limited to the latest 100. No media, calls, reactions, or end-to-end encryption. |
| AI and People | AI, catalog, AI archives, and People discovery/chat are unavailable while the Shield screen is active. Their existing features resume after leaving it. Shield data is never sent to those services. |
| Retention and deletion | After the subscription expires, sending and other writes stop after a 14-day grace period. Owners and members can still read data; no data is automatically deleted. Member removal revokes future access but does not erase historical messages. |
| Export and hosting move | Owners can export the member list and group/direct text messages as JSON from the app. Moving data to a customer-owned server remains a manual service arranged with Dost; no self-service or automatic migration exists. |
| Privacy operations | No use for model training, product improvement, experiments, testing, analytics, or advertising. Standard Crashlytics stays enabled, but the feature must not attach message, member, or organization details as custom keys or logs. |

## Data flow and access boundary

The Android client writes organisation metadata, member records, membership audit events, group/direct message text, sender UID, and server timestamps into Firestore. Rules require a valid owner Shield claim to create an organisation, a trusted age cohort for access, active membership, an atomic audit record for membership changes, and a plan seat limit for admission. `premium_verify.php` writes the owner's `dostShieldUntil` and `dostShieldSeats` claims; the trusted `shield_plan_sync.php` endpoint mirrors those into `secureOrgs/{orgId}.plan`. Client writes to `plan` are denied. The legacy `secureOrgConfig/superSecure` path is the Shield remote kill switch; signed-in clients can read it only while enabled. The build flag is only a UI control and is not an authorization boundary.

The existing Firebase project and region process this data. Firebase administrators and its infrastructure provider can technically access stored data. Shield is closed and admin-controlled, isolated from AI and analytics, and encrypted in transit and at rest by Google Cloud. It is not end-to-end encrypted. Android backup/archive code does not read Shield's Firestore namespace. The app does not show Firestore cache snapshots while offline.

Removed membership is retained as a status tombstone. Current Firestore rules deny that account further organisation reads and writes. Existing messages remain available to authorized members until organisation deletion. Provider-managed backup copies may persist according to the Firebase provider's backup schedule after active records are deleted.

## Launch gates

Before enabling customer access:

- Product owner explicitly approves the feature flag, hosting region, availability/retention commitments, deletion process, and any pricing.
- Privacy counsel approves the policy, customer terms, and DPA; the owner publishes the required disclosures and completes the Play Console Data safety form.
- Firebase operator deploys and verifies the reviewed Firestore rules in the intended project, confirms region and administrative access, configures production access controls, and verifies deletion and provider-backup behavior.
- Keep `secureOrgConfig/superSecure` absent or `enabled: false`, `plan_shield_enabled` false, and `DOST_SUPER_SECURE_ENABLED` false until every launch gate passes. Only the Firebase operator may change the remote document.
- Run Firestore emulator rules tests, PHP entitlement tests, Kotlin unit tests, and a two-account online smoke test covering owner eligibility, teen members, exports, read-only grace expiry, and Team/Business seat caps.
- Confirm AI/archive/People requests and optional analytics cannot receive organization content or identifiers, including during background work and crash handling.
- Confirm export includes all organisation messages and the member list, and establish the separate manual process for customer-server moves. Do not promise deletion timing until provider-backup retention is operationally verified.

## Draft documents

- [Play Data safety notes](docs/super-secure/play-data-safety-draft.md) — a product-specific input sheet, not a completed Play Console submission.
- [Customer terms](docs/super-secure/customer-terms-draft.md) — draft terms for legal review.
- [Data Processing Addendum](docs/super-secure/dpa-template-draft.md) — unexecuted template with operational items that must be completed before signing.
