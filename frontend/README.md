# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend enabling type-aware lint rules by installing `oxlint-tsgolint` and editing `.oxlintrc.json`:

```json
{
  "$schema": "./node_modules/oxlint/configuration_schema.json",
  "plugins": ["react", "typescript", "oxc"],
  "options": {
    "typeAware": true
  },
  "rules": {
    "react/rules-of-hooks": "error",
    "react/only-export-components": ["warn", { "allowConstantExport": true }]
  }
}
```

See the [Oxlint rules documentation](https://oxc.rs/docs/guide/usage/linter/rules) for the full list of rules and categories.

## Grocery Intelligence Platform

Run `npm ci` once, then `npm run dev`. Open http://127.0.0.1:5173 and enter an API
access token from your separately running Java backend. The default proxy target
is http://127.0.0.1:8080; override with `COUPON_API_TARGET` before starting Vite.
See the root README's **Web and mobile browser interface (Step 6)** section for
supported workflows, security boundaries, PWA deployment, and verification.

Checks: `npm run typecheck`, `npm run lint`, `npm test`, `npm run build`.
Browser checks: `npm run test:e2e` (requires a built app and Playwright Chromium).
Never configure real-API browser tests against a persistent database.
