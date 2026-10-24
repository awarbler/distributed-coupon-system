# Grocery Intelligence Platform — Frontend

The React and TypeScript browser workspace for the
[Grocery Intelligence Platform](../README.md), built with Vite.

## Responsibilities

- Receipt entry, correction, browsing, and financial report views.
- Shopping plans, coupons, promotions, and reward account views.
- Responsive layouts, validation feedback, and collapsible sections.
- Authenticated communication with the Java API; financial calculations remain
  in the Java backend.

## Structure

| Location | Purpose |
| --- | --- |
| `src/features/` | Receipt and shopping workflows |
| `src/pages/` | Workspace pages |
| `src/api/` | API client and formatting helpers |
| `src/components/` | Shared UI components |
| `src/index.css` | Typography, colors, and form-control tokens |
| `src/App.css` | Page and component layouts |
| `tests/` | Playwright browser checks |

## Documentation

- [Project documentation index](../docs/reference/README.md)
- [Environment and operating guide](../docs/operations/LOCAL_API_SETUP.md)
- [Frontend design system](../docs/reference/FRONTEND-DESIGN-SYSTEM.md)
- [Verification and test scope](../docs/reference/VERIFICATION.md)

Frontend validation uses TypeScript, Oxlint, Vitest, and Playwright. Browser tests
use synthetic fixtures or an explicitly configured disposable API database.
