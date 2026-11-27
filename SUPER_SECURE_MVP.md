# Super Secure MVP

**Status: implementation draft; customer data collection is not approved.** The feature is build-flagged off by default. Do not enable it for customers until the launch gates below have been reviewed and accepted by the product owner, privacy counsel, and Firebase operator. This document records implementation assumptions for issue #67; it is not legal advice or a claim of independent security certification.

## MVP assumptions

| Topic | MVP default |
| --- | --- |
| Activation and billing | `DOST_SUPER_SECURE_ENABLED` is false unless explicitly enabled at build time. Firestore also denies all Super Secure access unless a Firebase administrator creates/enables `secureOrgConfig/superSecure`; clients cannot write this control document. There is no Super Secure billing or connection to consumer Premium SKUs. |
| Adult eligibility | Reuse the app's current server-verified adult eligibility gate. |
| Backend and data boundary | Use the existing Firebase project and Firestore region, under the separate `secureOrgs/{orgId}` namespace. This is separate from People and AI collections, not a separate Firebase project or customer-controlled deployment. |
| Membership | The owner and managers admit people by Firebase UID. Organization IDs are shared out of band. There is no public directory, email lookup, member invite, or invite-code flow. Members cannot add or remove people. |
| Roles and size | One owner is created with each organization. Only the owner can appoint or remove managers; an owner cannot be removed or demoted. There can be at most 25 active members, counting the owner and managers. The limit is checked by the client and enforced atomically in Firestore rules. |
| Conversations | One text-only group chat and text-only 1:1 chats between active members. Messages are limited to 4,000 characters; the live message view is limited to the latest 100. No media, calls, reactions, or end-to-end encryption. |
| AI and People | AI, catalog, AI archives, and People discovery/chat are unavailable while the Super Secure screen is active. Their existing features resume after leaving it. Super Secure data is never sent to those services. |
| Retention and deletion | Organization records remain until the owner requests deletion through the published Contact Us email and verifies ownership. Member removal revokes future server-authorized access but does not erase historical messages. No self-service deletion exists. |
| Export and hosting move | No automated JSON/media export or customer-managed hosting in the MVP. Owners can contact Dost to request a future export or migration; timing and feasibility must be agreed before relying on it. Phase 2 requires a separately reviewed transfer and deletion process. |
| Privacy operations | No use for model training, product improvement, experiments, testing, analytics, or advertising. Standard Crashlytics stays enabled, but the feature must not attach message, member, or organization details as custom keys or logs. |

## Data flow and access boundary

The Android client writes organization metadata, member records, membership audit events, group/direct message text, sender UID, and server timestamps into Firestore. Rules require an active member for organization/message access, limit direct chats to their two current members, and require manager/owner role plus an atomic audit record for membership changes. Owner, manager, member, and organization-size checks are enforced server-side. The admin-only `secureOrgConfig/superSecure` document is an independent server-side launch gate; it is absent by default and has no client access rule. The build flag is only a UI/activation control and is not an authorization boundary.

The existing Firebase project and region process this data. Firebase administrators and its infrastructure provider can technically access stored data. Firebase provides encryption in transit and at rest; this MVP does not provide end-to-end encryption. Android's app-private SQLite database domain is excluded from backup/device transfer. The app does not show Firestore cache snapshots while offline.

Removed membership is retained as a status tombstone. Current Firestore rules deny that account further organization reads and writes. Existing messages remain available to current authorized members until organization deletion. Provider-managed backup copies may persist according to the Firebase provider's backup schedule after active records are deleted.

## Launch gates

Before enabling customer access:

- Product owner explicitly approves the feature flag, hosting region, availability/retention commitments, deletion process, and any pricing.
- Privacy counsel approves the policy, customer terms, and DPA; the owner publishes the required disclosures and completes the Play Console Data safety form.
- Firebase operator deploys and verifies the reviewed Firestore rules in the intended project, confirms region and administrative access, configures production access controls, and verifies deletion and provider-backup behavior.
- Keep `secureOrgConfig/superSecure` absent or `enabled: false` until every other launch gate passes. Only the Firebase operator may enable or disable this document; verify client reads and writes are denied while absent or disabled.
- Run Firestore emulator rules tests, Kotlin unit tests, and a two-account online smoke test covering creation, manager/member access, direct/group messages, removal, and the 25-member boundary.
- Confirm AI/archive/People requests and optional analytics cannot receive organization content or identifiers, including during background work and crash handling.
- Establish a support procedure to verify the owner, export or delete active org records as committed, track the request, and state provider-backup retention accurately. Do not promise export or deletion timing until this procedure is operational.

## Draft documents

- [Play Data safety notes](docs/super-secure/play-data-safety-draft.md) — a product-specific input sheet, not a completed Play Console submission.
- [Customer terms](docs/super-secure/customer-terms-draft.md) — draft terms for legal review.
- [Data Processing Addendum](docs/super-secure/dpa-template-draft.md) — unexecuted template with operational items that must be completed before signing.
