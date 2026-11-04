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

See the [project overview](../README.md). Detailed setup, design-system, and
verification guides remain local while under review and are not published yet.

Frontend validation uses TypeScript, Oxlint, Vitest, and Playwright. Browser tests
use synthetic fixtures or an explicitly configured disposable API database.


Receipt Import (1.61): Add Receipt includes an owner-only PDF/image import into its existing editable draft. It retains original evidence and review flags, offers source comparison and duplicate links, and uses the existing preview/save flow. OCR stays on the local Java server; no frontend OCR key or external upload service is used.

The deal reader now uses a local Ollama vision/text model (`qwen3-vl:8b`) at `127.0.0.1:11434`. Run `./start-local-reader.sh` from the repository root if it is stopped. `./start-local-api.sh` also starts it when the downloaded runtime is present. Model files are in the ignored `.local-reader/` directory. No API key or paid service is used. Extracted products, coupons, and rewards go directly into editable plan fields; unknown details and arithmetic differences appear as review notes.
