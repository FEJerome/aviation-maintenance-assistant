# Security Policy

## Supported Versions

Only the latest commit on `main` and the most recent release receive security fixes.

## Reporting a Vulnerability

Please report suspected security vulnerabilities through **GitHub private vulnerability reporting**:
open the repository's **Security** tab → **Advisories** → **Report a vulnerability**.

Please **do not** open public issues for security reports.

When reporting, include:

- the affected commit or release;
- steps to reproduce, with request/response examples where possible;
- the impact you believe the issue has.

We aim to acknowledge reports within 7 days. If the report is accepted, a fix is prepared and
released before any public disclosure, and the reporter is credited (unless they prefer otherwise).

## Scope Notes

- The backend's ingest endpoint (`/api/admin/ingest`) has no built-in authentication by design;
  restrict it to localhost or a trusted network, as documented in the README and DEPLOY guide.
- LLM credentials must only ever be supplied through server-side environment variables — never
  commit `.env` files or keys. If you believe a credential has been committed to this repository,
  report it through the private channel above.
