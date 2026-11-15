# Contributing to Aviation Maintenance Assistant (AMA)

This document defines the contribution workflow, the review process, the
AI-assisted development disclosure policy, and the public-content rules for
this repository. It is the public collaboration contract; details of the
internal document-driven development process (design docs, ADRs, commit
structure) live in [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md).

## Contribution workflow

1. **Open or pick an Issue.** Every pull request references an Issue
   (`Closes #N` or `Refs #N`). Blank Issues are disabled; use the bug report
   or task template and fill in every section.
2. **Branch from `main`.** Use short-lived branches named
   `feature/...`, `fix/...`, `chore/...`, or `docs/...`. Keep each branch
   focused on one reviewable change.
3. **Verify locally before pushing:**
   - Backend: `./mvnw -B test` (JDK 21, Eclipse Temurin recommended)
   - Frontend: `npm ci && npm test && npm run build` (Node 24)
4. **Open a pull request.** The PR template is mandatory: Motivation,
   Changes, Verification evidence, Review outcome. Fill in real commands and
   numeric results.
5. **Wait for CI.** The `backend-tests` and `frontend-tests` checks must be
   green. `npm audit` runs as informational feedback on pull requests and as
   a scheduled weekly job that is allowed to fail; it is not a required
   check.
6. **Address review comments.** The maintainer records the review outcome
   and makes the merge decision.

Branch protection on `main` (required status checks) is enabled and adjusted
by the maintainer only after the checks have passed on a real pull request.

## Testing boundaries

- Automated tests use loopback fake/local services only (fake
  OpenAI-compatible server on `127.0.0.1`, isolated local ChromaDB, backend
  on a temporary port). See `scripts/tests/README.md` for the setup.
- Do not present loopback or fake-server results as real provider, real
  model, public network, or production verification.
- Real cloud/local model smoke checks, Docker end-to-end runs, and real
  browser acceptance are out of CI scope and require a separately approved
  acceptance step.
- Do not add real API keys, tokens, or credentials anywhere in the
  repository, Issues, or pull requests.

## Public-content rules

Issues, pull requests, commit messages, and documentation are public.

- No internal persona names, private project codenames, or internal
  session/planning identifiers. Maintainer authorship stays under the
  repository's configured, real-name git author; this rule does not
  restrict that public authorship.
- No private planning references, internal hostnames, intranet addresses, or
  incident post-mortem details.
- No real credentials. Redact logs before attaching them.
- Do not file public Issues with unpatched vulnerability exploit details.
  Report vulnerabilities through GitHub's private vulnerability reporting
  where available, or contact the maintainer directly; public discussion is
  limited to the fixed release.
- All public documentation and templates are written in English.

## AI-assisted development

This project uses AI-assisted development as part of its workflow.
The approved disclosure statement is:

> Development on this repository is carried out with Claude Code as an
> agentic development harness, using official models including Kimi
> K3.1 Pro, Kimi K3, Kimi K2.8 Preview, and DeepSeek-V4.1-Flash-1017.
> These are development tools, not runtime dependencies of the product.
> The model is chosen per task by the maintainer's workflow. The
> maintainer supervises and approves the design, verification,
> independent review, and merge of every change. Independent AI-assisted
> review is presented as a substantive pull request comment — what was
> checked, findings, and resolution — and is never passed off as
> external human review.

Supplementary notes:

- The tools above are **development tools**, not runtime dependencies;
  the running assistant stack does not depend on any specific model
  provider.
- AI-generated output is always verified with the local test commands
  above before a pull request is opened.
- Independent AI-assisted review does not replace maintainer approval.

## Commit conventions

- Commit messages are in English and follow the structured format described
  in `docs/CONTRIBUTING.md` (type, background, decision, pitfalls,
  verification evidence).
- Do not commit unrelated files; review `git status` before staging.

## What is out of scope for drive-by changes

- Product code, API behavior, and the visual presentation layer: propose
  them via an Issue first.
- Changes to locked technology versions (see `docs/CONFIGURATION.md` and
  ADRs): open an Issue explaining why the pin must move.
