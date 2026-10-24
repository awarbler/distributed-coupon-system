# Grocery Intelligence Platform

A Java and React application for tracking grocery purchases, coupons, rebates,
and reward accounts. The repository is named `distributed-coupon-system`.

The platform keeps actual cash/card spending separate from rewards used, benefits
earned, and calculated savings, with receipt history available for review.

## Project status

Active development. The application provides a browser workspace, a Java HTTP API
backed by PostgreSQL, and an existing local receipt CLI. The API serves a single
owner with separate read/write access. Distributed-systems simulation remains a
planned area of the project.

## Capabilities

- Create, review, edit, delete, and restore receipts with revision history.
- Track product prices, sale promotions, manufacturer coupons, and store coupons.
- Record and match Register Rewards, and track CVS ExtraBucks earned and used.
- Track expected and received rebates, points, and account balances.
- Review receipt totals and detailed reports by day, date range, and month.
- Plan shopping trips and manage clipped coupons and advertised promotions.

## Technology

| Area | Stack |
| --- | --- |
| Backend | Java 21, Maven, JDK HTTP server |
| Database | PostgreSQL |
| Frontend | React, TypeScript, Vite |
| Verification | JUnit, Vitest, Playwright |

## Repository structure

| Location | Purpose |
| --- | --- |
| `src/main/java/coupons/` | Domain logic, CLI, HTTP API, and repositories |
| `src/main/resources/db/migration/` | PostgreSQL migrations |
| `src/test/` | Java tests |
| `frontend/` | Browser application and frontend tests |
| `docs/` | Specifications, reference guides, and operating documentation |

## Documentation

- [Documentation index](docs/reference/README.md)
- [Software Requirements Specification](docs/reference/Distributed_Coupon_System_SRS_Draft_1_0.docx)
- [Software Design Description](docs/reference/Distributed_Coupon_System_SDD_Draft_1_0.docx)
- [Frontend overview](frontend/README.md)
- [Local environment and operating guide](docs/operations/LOCAL_API_SETUP.md)
- [PostgreSQL and migration runbook](docs/operations/postgresql-local-runbook.md)
- [Receipt and reward workflows](docs/reference/RECEIPT-REWARDS-WORKFLOWS.md)
- [API reference](docs/reference/API-REFERENCE.md)
- [Verification record](docs/reference/VERIFICATION.md)

Setup commands and step-by-step operating instructions belong in the linked
guides. Specifications use stable filenames with numbered revisions inside each
document.

## Maintenance and support

Maintained by [awarbler](https://github.com/awarbler).
Use [GitHub Issues](https://github.com/awarbler/distributed-coupon-system/issues)
for reproducible bugs and proposed changes. Include the affected workflow and
expected behavior, with private receipt details and credentials removed.

Changes should include appropriate tests and updates to the relevant canonical
documentation. The current specification revision is listed in the documentation
index.

## License

A license file has not been added to this repository.
