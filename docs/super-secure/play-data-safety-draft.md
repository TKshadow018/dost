# Super Secure Play Data safety notes

**Draft input only — not a Play Console submission.** Reconcile these notes with the complete app's current SDK behavior and the actual production Firebase configuration before enabling Super Secure or submitting any Data safety declaration. Do not select "no data collected" or "no data shared" on the basis of this feature's isolation.

## Product-specific data inventory

| Data | Purpose | Collection/transfer | Access and handling |
| --- | --- | --- | --- |
| Organization name and organization ID | Create and open a closed workspace | Sent to Firebase Firestore when an owner creates an org or a member opens one | Available to current members; Firebase administrators/provider can technically access it |
| Firebase account UID, display name, role, membership status | Membership and access control | Sent to Firebase Firestore for each admitted member | Owner/managers administer membership; the member can see the member list |
| Membership actor/target UIDs, action, and timestamp | Auditing role and membership changes | Sent to Firebase Firestore when a manager changes membership | Current managers can read the audit log |
| Text message, sender UID, and timestamp | Group and 1:1 messaging | Sent to Firebase Firestore when a member sends a message | Group messages are available to current org members; direct messages to the two current participants |

Super Secure does not collect message media, contacts, location, email addresses for member lookup, or invite codes. It does not send its content to AI/model services, AI archives, People collections, analytics, ads, or Crashlytics custom keys/logs. Normal app Crashlytics reports remain enabled; review the full SDK data collection declaration independently.

## Draft declaration inputs

- **Data categories to review:** personal information (display name and account identifier), messages/other user-generated content, and other identifiers (organization ID and Firebase UID). Confirm the exact categories and qualifiers against the current Play Console taxonomy.
- **Purpose:** app functionality, specifically account/membership administration and organization messaging.
- **Required or optional:** optional product surface; a user must be admitted to an organization to use it. Data sent for a message or membership operation is necessary for that operation.
- **Shared with service providers:** data is transmitted to the configured Firebase/Firestore provider for hosting and delivery. Do not characterize this as no sharing without checking the Data safety definitions and Firebase configuration.
- **Encryption in transit:** Firebase traffic uses TLS. Firebase documents encryption at rest. The feature is not end-to-end encrypted.
- **Deletion:** owners may email the published Contact Us address to request org deletion. Removal of one member revokes future server-authorized access but does not delete historical messages. Do not declare a specific deletion SLA until the operational process and provider-backup retention have been verified.
- **Retention:** active org data remains until a verified owner deletion request; provider-managed backups may persist under provider schedules. Verify the configured Firebase backup/retention behavior before submission.

## Before submission

1. Re-run the whole-app SDK/Data safety review, including Firebase Auth, Firestore, Crashlytics, People, analytics, billing, and any release-specific SDKs.
2. Verify production Firestore region, access configuration, backup/deletion behavior, and whether any app/API logs receive these fields.
3. Confirm the published privacy policy, user-facing terms, DPA, and in-app deletion support path match the actual operations.
4. Submit no Play Console change without the product owner's explicit approval.
