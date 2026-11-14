# Frontend dependency security review — 2026-11-12

## Release posture

**Release-blocking review required.** `npm ci` installed the locked dependencies successfully, but `npm audit` reported one critical and seven high package findings. No `audit fix`, dependency upgrade, or lockfile rewrite was performed. Frontend tests and production build passed before this review; those results do not clear the vulnerabilities.

Evidence commands:

```bash
npm audit --json
npm ls dompurify vite vue @vue/server-renderer nanoid postcss source-map-js undici --all
```

## Critical / high findings

| Package and current version | Dependency path / scope | Impact for this application | Recommended disposition (not applied) |
|---|---|---|---|
| **dompurify 3.4.8 — critical** | Direct production dependency, `node_modules/dompurify`; used to sanitize rendered Markdown. Advisories affect versions through 3.4.16 and include XSS/hook/configuration bypasses. | Directly relevant to untrusted model/source rendering and the application's XSS boundary. | Highest priority. Upgrade deliberately to a release outside all reported ranges (audit indicates 3.4.17+), inspect configuration/hooks, then rerun renderer XSS tests and visual checks before release. |
| **vue 3.5.35 / @vue/server-renderer 3.5.35 — high** | Direct `vue`; transitive `@vue/server-renderer`; also referenced by `@vitejs/plugin-vue`. Advisory GHSA-g2v6-rqmx-r4w6 affects `<3.5.42`. | The application is client-rendered rather than SSR, reducing direct SSR exposure, but the vulnerable package is present in the production dependency tree. | Upgrade Vue and its renderer together to a compatible release outside the advisory range, then run component/build/visual regression. |
| **vite 5.4.21 direct; vite 8.0.16 under vitest — high** | Direct build/dev dependency and `vitest -> vite`; related `postcss` tree. Advisories cover `<=6.4.3` and `8.0.0–8.1.5`, including Windows/path and dev-middleware issues. | Primarily development/build/test exposure; the static production bundle does not run the Vite middleware. Windows development paths are relevant. | Audit proposes vite 8.3.8, a major upgrade. Do not apply blindly: review Vite/plugin-vue/Vitest compatibility and Node requirements, upgrade as a separate change, then rerun tests/build. |
| **postcss 8.5.15 — high** | `vite -> postcss`; also `@vue/compiler-sfc`. Advisories affect `<=8.5.22` and involve source-map path handling/disclosure. | Build-time source-map exposure rather than browser runtime code, but untrusted build inputs could be affected. | Upgrade through the compatible Vite/Vue dependency plan to 8.5.23+ and verify source maps/build output. |
| **nanoid 3.3.12 — high** | `vite -> postcss -> nanoid`. Advisories affect versions before 3.3.16/3.3.18 and describe non-secure/custom generator loops with invalid sizes. | Build tooling only; no evidence that application runtime calls the vulnerable generators. | Update the dependency tree to nanoid 3.3.18+ and rerun build/tests; avoid claiming production exploitability without a call path. |
| **source-map-js 1.2.1 — high** | `vite -> postcss`; `jsdom -> css-tree`; Vue compiler packages. GHSA-68fv-2mgg-jv7q affects 1.0.0–1.2.1. | Build/test source-map parsing can cause event-loop denial of service with crafted section offsets. | Update to 1.2.2+ through the coordinated dependency plan and rerun compiler/test suites. |
| **undici 7.27.2 — high** | `jsdom -> undici`, test-only dependency. Multiple advisories affect versions through 7.30.0, including proxy/TLS, cache/cookie, response parsing, and denial-of-service issues. | Not shipped in the static browser bundle, but test network/proxy behavior and CI isolation can be affected. | Update jsdom/undici to a compatible tree with undici 7.30.1+; review tests that use external URLs and keep CI network access restricted. |

## Moderate findings and follow-up

The audit also reported three moderate findings. They remain in the raw `npm audit --json` evidence and should be reviewed alongside the coordinated upgrade; this report does not treat them as cleared.

## Required independent verification

After a separately approved dependency update, record the new lockfile diff and rerun `npm audit`, `npm test`, and `npm run build`. Review the production renderer separately from dev/test-only findings. No vulnerability is marked resolved by this document.
