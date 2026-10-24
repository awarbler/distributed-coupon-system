# Documentation revision 1.16 — README and guide ownership

The root README is the GitHub project overview, not the user manual. The frontend README describes the browser application. Setup and operating procedures are in the existing operations guides; workflow details and verification history are in the existing reference guides. Revision 1.16 adds no application features.

# Revision 1.15 — single-document catalog

Updated October 24, 2026. The current specifications are Draft 1.16. Earlier revision sections below are historical. This revision consolidates documents, not application behavior.

| Purpose | One canonical location | Version control |
|---|---|---|
| Requirements and historical requirements inventory | Distributed_Coupon_System_SRS_Draft_1_0.docx | Internal Draft 1.15; revision-history table |
| Design and historical combined/design plans | Distributed_Coupon_System_SDD_Draft_1_0.docx | Internal Draft 1.15; revision-history table |
| API contract and prior API guides | API-REFERENCE.md | Revision 1.15 consolidation section |
| Deployment/backup | ../operations/DEPLOYMENT-BACKUP.md | Revision 1.15 consolidation section |
| Historical code inventory | ../architecture/baseline/grocery-code-inventory/FILE_INDEX.md and ALL_DECLARATIONS.tsv | Revision 1.15; original snapshot dates retained in as_of |
| Detailed visual specifications | FRONTEND-DESIGN-SYSTEM.md | Numbered design-system revisions; separate purpose from SDD |
| Consolidation audit and verification | VERIFICATION.md | Revision 1.15 audit entry |

Other data, calculation, schema, function/dependency and workflow guides remain separate because they document different subjects. Generated library notices, migration evidence and receipt attachments are not merged or deleted. Repeated receipt.properties files belong to different receipts.

Future changes update the canonical file in place, add a dated numbered revision entry, and update this index; do not create copies named “2”, “copy”, or new version-suffixed documents. Historical material is explicitly marked and does not override the current implementation contract.

---

# Canonical system documentation — Draft 1.15

Updated October 24, 2026. There is one authoritative SRS and one authoritative SDD:

- [SRS — current Draft 1.16](Distributed_Coupon_System_SRS_Draft_1_0.docx)
- [SDD — current Draft 1.16](Distributed_Coupon_System_SDD_Draft_1_0.docx)

The filenames remain stable; the cover, core metadata and revision-history table carry the version. Future updates must increment the internal version and append a dated change-history row in these same files. Do not create numbered filename copies. Revision 1.14 consolidates previously appended changes; earlier entries below are history, not the current version.

The requirements inventory is now incorporated into the SRS historical-source appendix. Both earlier design/combined plans are incorporated into the SDD historical-source appendix. The separate source Word copies were removed after content-preservation checks. There are exactly two application Word documents: the SRS and SDD.

The canonical specifications, linked reference guides and operating guides are eligible for Git. Private financial research, receipt data, migration snapshots and generated documentation remain ignored. Nothing was committed or pushed by this audit.

The 1.14 implementation audit maps implemented receipt entry/editing, coupon selection, CVS/ECB, member rewards, expiration dates, account/reporting and responsive UI workflows to their existing code. It does not mark outstanding spreadsheet corrections or future simulator scope complete. See local VERIFICATION.md for exact checks and limits.

---

# Application reference — start here

## Revision history

| Version | Date | Changes | Verification |
|---|---|---|---|
| 1.16 | 2026-10-24 | Project-overview READMEs; manual content relocated to existing guides | Markdown structure and relative links checked |
| 1.15 | 2026-10-24 | Consolidated duplicate guides/inventories and historical Word sources | Content preservation, document structure and canonical-file checks |
| 1.14 | 2026-10-24 | Consolidated implementation audit and canonical SRS/SDD version control | Automated tests and document checks |
| 1.0 | 2026-10-21 | Original reference snapshot | Static inspection |
| 1.1 | 2026-10-22 | Planning reward progress, unknown-value semantics and current UPC policy | Source updated; final checks not run |
| 1.2 | 2026-10-22 | Receipt evidence/reconciliation, distinct purchase/scan and partial benefit values | See VERIFICATION.md |

## Revision 1.2 — receipt evidence and reconciliation

Receipt revision 1.2 preserves original evidence and legacy calculations while adding explicit printed-total basis, payment completeness and classified reconciliation. Purchase versus receipt-scan categories remain distinct. Existing documents/files are updated in place; no schema migration or new application files. See VERIFICATION.md for execution evidence and unverified deployment limits.


## Revision 1.1 — current behavior and status

Revision 1.1 updates existing documents in place. Potential reward progress and zero/unknown/not-applicable distinctions are implemented in the working tree but not verified by a final test/build run. Advertised-deal UPC matching is opt-in in current source. OD-07/ADR-P05 are partially resolved for planning policy only; unrelated financial policies remain open. Existing file names are retained; internal revision tables identify updates.



Original revision 1.0 inspection: October 21, 2026 (America/Chicago). Base Git commit: `fa3b8debc070cb5de990cbe9eb420fe1e56b7c06`. This reference describes the working tree, including pre-existing uncommitted checkout changes; it does not prove what is deployed online. No application tests were run for this documentation-only task.

This documentation covers the application's own Java/React code, SQL migrations, tests, configuration, examples and existing reference artifacts. Installed Java/Python/PostgreSQL/Docker internals, downloaded libraries, generated build output and live account data are outside scope. Application-owned scripts and configuration remain in scope.

## Where to look

| Question | Document |
|---|---|
| How do the applications connect? | [System overview](SYSTEM-OVERVIEW.md) |
| What does each file do? | [File catalog](FILE-CATALOG.md), [spreadsheet-ready CSV](file-catalog.csv) |
| What do the important fields mean? | [Data dictionary](DATA-DICTIONARY.md), [model declarations](MODEL-DECLARATIONS.md) |
| How do promotions qualify? | [Promotion rules](PROMOTIONS.md) |
| Why do checkout totals differ? | [Calculation guide](CALCULATIONS.md) |
| Does online match the CLI? | [Workflow comparison and gaps](WORKFLOW-COMPARISON.md) |
| Which functions and dependencies exist? | [Function index](FUNCTION-INDEX.md), [import index](DEPENDENCY-INDEX.md) |
| What can the API do? | [API reference](API-REFERENCE.md) |
| What is stored in PostgreSQL? | [Schema source reference](DATABASE-SCHEMA.md) |
| How is behavior verified? | [Verification and maintenance](VERIFICATION.md), [test index](TEST-INDEX.md) |

## Reading the evidence

**Reviewed** means a rule was checked in the current source. **Indexed** means a declaration/import/comment was extracted for navigation. **Proposed** means desired behavior, not implemented behavior. **Unverified** means this task did not establish parity or deployment behavior. Indexing all files is not equivalent to manually auditing every business rule.

This reference records a working-tree snapshot. Existing uncommitted application changes were present before documentation began. No source application changes, migrations, builds, services or database writes were performed to create these documents. Consult source before changing behavior; do not treat a documentation example as retailer policy.

Open this file in the IDE Markdown preview and follow links. Open the CSV in a spreadsheet to filter by area or filename. All reference files are ordinary text stored with the project and can be versioned alongside code.


October 23 update: receipt lifecycle endpoints/read models are documented in
[API-REFERENCE.md](API-REFERENCE.md), corrected product-price semantics in
[CALCULATIONS.md](CALCULATIONS.md), field meanings in [DATA-DICTIONARY.md](DATA-DICTIONARY.md),
and workflow dependencies in [DEPENDENCY-INDEX.md](DEPENDENCY-INDEX.md).
[DATABASE-SCHEMA.md](DATABASE-SCHEMA.md) records that these changes need no new
migration. Detailed user steps are in [Receipt totals](../operations/RECEIPT_TOTALS.md).
Historical generated inventories remain dated snapshots rather than claims of a
fresh complete code inventory. Verification is recorded in VERIFICATION.md.

Current visual specifications: [Frontend Design System & Component Style Guide](FRONTEND-DESIGN-SYSTEM.md).
The Word SRS/SDD are updated to Draft 1.4 for readable, consistently sized form
controls and full-text descriptions; original filenames are retained.

## Revision 1.6 — receipt/account changes

See [Receipt & Reward Workflows](RECEIPT-REWARDS-WORKFLOWS.md) for implemented behavior, API contract, changed application files and verification limits. The actual SRS and SDD Word documents are updated to Draft 1.6. [Frontend Design System](FRONTEND-DESIGN-SYSTEM.md) remains the detailed font/color/control specification; SRS states requirements and SDD describes component design.


## Revision 1.7 — receipt RR correction and matching

SRS/SDD Draft 1.7 adds receipt RR entry, eligible-source matching, explicit misclassification/duplicate correction, payment role resolution and receipt-section placement/collapse. See RECEIPT-REWARDS-WORKFLOWS.md and VERIFICATION.md.


## Revision 1.8 — RR without an earning receipt

SRS/SDD Draft 1.8 records unknown-source RR entry without an earning receipt/date and atomic matching to the existing recorded use. See RECEIPT-REWARDS-WORKFLOWS.md.


## Revision 1.9 — Rewards points-account display

SRS/SDD Draft 1.9 documents Rewards-only points presentation for MyPoints, Fetch, P&G Brand, Swagbucks and Merryfield. Design System v1.5 records the UI policy.


## Revision 1.10 — independent Rewards inventory

SRS/SDD Draft 1.10 adds Rewards-page standalone Register Reward inventory and later matching, including nullable history and explicit availability confirmation. Design System v1.6 and RECEIPT-REWARDS-WORKFLOWS.md describe the UI and changed files.


## Revision 1.11 — visible recorded receipt calculations

SRS/SDD Draft 1.11 and Design System v1.7 document always-visible recorded receipt arithmetic with provisional verification. See RECEIPT-REWARDS-WORKFLOWS.md and VERIFICATION.md.


## Revision 1.12 — simplified receipt totals display

SRS/SDD Draft 1.12 records the requested receipt Totals display simplification; Design System v1.8 supersedes the revision 1.11 calculation-table visibility requirement. Stored values/calculations remain intact.


## Revision 1.13 — reuse existing files and align CLI rebate display

SRS/SDD Draft 1.13 records the CLI/spreadsheet display correction and consolidation into existing files. Design System v1.9 names current token/component owners. Owner development constraint: edit and reuse existing files. Discuss any proposed new application, test or documentation file with the owner before creating it. This revision consolidates the earlier added helpers/components/tests into pre-existing files and does not add source or documentation files.
