# Shield Data Processing Addendum — draft template

**Unexecuted template. Do not sign or represent as a DPA until completed and approved by counsel and the service operator.** This draft assumes the customer is controller and Dost is processor for organization data; confirm the parties' actual legal roles and governing law.

## 1. Parties and service

This Addendum is between **[Dost legal entity and address]** ("Processor") and **[Customer legal entity and address]** ("Controller"), effective **[date]**. It applies only to Shield organisation data processed to provide the closed-group messaging service under **[main agreement]**.

## 2. Processing details

- **Subject matter and purpose:** hosting organization membership, role/audit records, and text-only group and direct messages.
- **Duration:** for the service term and until verified deletion instructions are completed, subject to legally required retention and provider-managed backup expiry.
- **Data subjects:** customer employees, family members, or other people the customer admits to its organization.
- **Personal data:** organization name/ID; Firebase UIDs; display names; role and membership status; audit actor/target/action/timestamp; message text, sender UID, and timestamp.
- **Sensitive data:** Controller must not submit special-category or highly sensitive personal data unless separately agreed in writing after a privacy/security review.
- **Documented instructions:** provide the service, enforce organization membership, maintain the audit records, deliver messages, and perform verified export/deletion instructions only as separately agreed. Processor must not use organization data for model training, product improvement, testing, experiments, analytics, advertising, or unrelated purposes.

## 3. Processor obligations

Processor will:

1. process personal data only on documented Controller instructions, unless applicable law requires otherwise;
2. ensure authorized personnel are subject to confidentiality obligations;
3. apply appropriate technical and organizational safeguards, including Firebase access controls and encryption in transit and at rest, and document the controls actually deployed;
4. notify Controller without undue delay after confirming a personal-data breach affecting this service, with a target of no later than 72 hours after confirmation, and provide reasonably available incident details;
5. assist Controller with data-subject requests and compliance duties to the extent supported by the service and applicable law;
6. restrict access to current organization members under Firestore rules, with role-limited management and audit access; and
7. provide information reasonably needed to demonstrate compliance, subject to security and confidentiality restrictions.

Shield is closed, admin-controlled, isolated from AI and analytics, and encrypted in transit and at rest by Google Cloud. It is not end-to-end encrypted. Google Cloud infrastructure and privileged project administrators may technically access stored data. Processor will not claim otherwise.

## 4. Subprocessors and transfers

The known hosting subprocessor is **Google Firebase/Cloud Firestore** under the configured Google Cloud project and region: **[confirm project and region]**. Before execution, Processor must list all applicable subprocessors, processing locations, and transfer safeguards; notify Controller of material changes; and provide a reasonable objection process. No AI/model provider receives Shield data.

## 5. Retention, return, and deletion

Active organization records are retained until a verified owner request. A member removal blocks future server-authorized access but does not delete historical messages. The current MVP has no self-service org deletion or automated export/migration.

Proposed service target, subject to operational verification before signature: Processor acknowledges a verified owner deletion instruction within **[5] business days** and deletes active organization records within **[30] calendar days**, except where law requires retention. Provider-managed backups expire under the provider's configured schedule, which Processor must document here before signing: **[backup-retention period and deletion behavior]**. A request to export or migrate data is a separate written scope, schedule, and secure-transfer agreement; it is not guaranteed by this template.

At the end of processing, Processor will delete or return personal data at Controller's choice, subject to the verified process and backup schedule above, and confirm completion in writing.

## 6. Controller obligations and annexes

Controller will provide required notices, establish a lawful basis, obtain necessary permissions, limit the data supplied, and manage its members. Security measures, incident contacts, data-subject request contacts, applicable transfer clauses, audit procedures, governing law, and signature blocks must be completed in **Annex 1** before execution.

**Annex 1 — required before signature**

- Customer and Processor legal names/addresses and contacts
- Applicable privacy law and international-transfer mechanism
- Firebase project, processing region, and complete subprocessors
- Verified security controls, access-review cadence, and incident contact
- Operational deletion SLA and provider backup-retention period
- Audit and regulator-cooperation procedure
- Customer-specific instructions and signature/date blocks
