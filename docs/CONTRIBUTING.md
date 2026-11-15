# Contributing Guidelines (CONTRIBUTING.md)

> This document defines the collaboration workflow and quality standards for this project.
>
> **Scope**:
> - Current: PR-based contributions with CI checks (backend tests, frontend tests/build, npm audit) are enforced on `main`.
> - Historical note: the project started as a single-maintainer effort; the early trunk-based workflow is kept below for reference only and no longer describes current practice.

---

## Table of Contents

- [Core Philosophy: Document-Driven Development (DDD)](#core-philosophy-document-driven-development-ddd)
- [Development Workflow](#development-workflow)
- [Document Templates](#document-templates)
  - [Feature Design Document Template](#feature-design-document-template)
  - [ADR Template](#adr-template)
- [Commit Conventions](#commit-conventions)
- [Code Review Checklist](#code-review-checklist)
- [Branch Model](#branch-model)

---

## Core Philosophy: Document-Driven Development (DDD)

**Document-Driven Development**: write the design document first, then the code, and keep the code and the documents in sync.

Why not "code as documentation"?

- Code answers "how"; documents answer "why" and "what".
- Collaborators do not have time to read the source code; documents are the most efficient communication medium.
- Writing the document *is* the design process — it surfaces flaws in the plan before they are baked into code.

---

## Development Workflow

```
+--------------+     +--------------+     +--------------+     +--------------+
| 1. Need/Issue| --> | 2. Write     | --> | 3. Implement | --> | 4. Update    |
|   (Issue/    |     |   design doc |     |   the code   |     |   documents  |
|   brainstorm)|     |   (ADR/PRD)  |     |  (Feature/   |     |  (API/diagram)|
+--------------+     +--------------+     |    Dev)      |     +--------------+
                                          +--------------+           |
                                                                       v
                                                            +--------------+
                                                            | 5. Commit    |
                                                            |  (structured)|
                                                            +--------------+
                                                                       |
                                                                       v
                                                            +--------------+
                                                            | 6. PR + CI   |
                                                            |  (review +   |
                                                            |   checks)    |
                                                            +--------------+
```

### Requirements per stage

| Stage | Input | Output | Acceptance criteria |
|-------|-------|--------|---------------------|
| **1. Need** | User feedback, technical exploration | One-sentence need statement | Value can be explained to a non-technical person |
| **2. Design** | Need statement | `docs/design/xxx.md` or `docs/ADR/ADR-00x-xxx.md` | Includes background, option comparison, interface changes, test plan |
| **3. Code** | Design document | Runnable code + unit tests | Interfaces/architecture from the design document are reflected in code |
| **4. Update** | Code changes | Updated `docs/API.md`, `docs/ARCHITECTURE.md` | API docs include curl examples for new endpoints; the architecture diagram reflects new modules |
| **5. Commit** | Code + documents | Git commit | Follows the structured commit convention (below) |
| **6. PR + CI** | Commit(s) | Merged pull request | Code review checklist passed; all CI checks green |

---

## Document Templates

### Feature Design Document Template

Create the file at: `docs/design/FEATURE-xxx-feature-name.md`

```markdown
# Feature Design: [Feature Name]

## Background

[What problem does this solve? What is the current pain point?]

## Goals

[What are the success criteria? How do we verify the feature is correct?]

## Option Comparison

| Option | Pros | Cons | Conclusion |
|--------|------|------|------------|
| A | ... | ... | ❌ Rejected, because ... |
| B | ... | ... | ✅ Adopted |

## Detailed Design

### Interface Changes

[New/modified REST endpoints, DTO fields]

### Architecture Adjustments

[New/modified modules, classes, data flows]

### Data Model

[New database tables, cache keys, file formats]

## Test Plan

[Unit tests, integration tests, acceptance scenarios]

## Risks and Rollback

[What is the worst case? How do we roll back?]

## Related Documents

- [ADR-xxx](../ADR/ADR-xxx-xxx.md)
```

### ADR Template

Create the file at: `docs/ADR/ADR-00x-title.md`

```markdown
# ADR-00x: [Title]

- **Status**: Proposed / Accepted / Deprecated / Superseded
- **Date**: YYYY-MM-DD
- **Decision makers**:

## Background

[What problem needed solving? What was the context and the constraints at the time?]

## Options Considered

### Option A: [Name]

**Strengths**: ...
**Weaknesses**: ...

### Option B: [Name]

**Strengths**: ...
**Weaknesses**: ...

## Decision

[What was chosen? One-sentence conclusion.]

## Consequences

### Positive effects
...

### Negative effects / trade-offs
...

### Related decisions
- [ADR-xxx](ADR-xxx-xxx.md)

## References
...
```

---

## Commit Conventions

### Structured Commits

Every commit is not just a code snapshot but a **traceable decision record**. Release retrospectives and technical write-ups can quote commit content directly.

**Iron rule: a bare one-liner like `feat: xxx` is not enough — the decision process must be included.**

#### Required structure

```
<type>: <one-line title>

- Background: what problem or need triggered this?
- Dilemma: what options were researched? Pros and cons of each?
- Decision: why was this option chosen? What was given up?
- Pitfalls and fixes: what went unexpectedly during execution? How was it resolved?
- Verification: how was the change confirmed correct? (command + result)
```

#### Types

| Type | Purpose | Example |
|------|---------|---------|
| `feat` | New feature | `feat: add voice input and LLM semantic normalization` |
| `fix` | Bug fix | `fix: re-validate aircraft model when it changes within a session` |
| `docs` | Documentation | `docs: restructure README with architecture diagram and demo` |
| `chore` | Dependencies/config/tooling | `chore: introduce LangChain4j 1.14.1 + DeepSeek` |
| `refactor` | Refactoring | `refactor: extract aircraft-model validation from Service into a Validator` |
| `test` | Tests | `test: add unit tests for model validation` |

#### Good vs. bad examples

**❌ Bad** (no decision information):
```
chore: add LangChain4j dependency
```

**✅ Good** (full decision process):
```
chore: adopt LangChain4j 1.14.1 + DeepSeek, drop DashScope

- Background: needed an LLM for RAG Q&A; the original plan was Tongyi Qianwen (DashScope)
- Dilemma:
  1. Version: 1.4.0 was planned, but it has no patch releases and its stability was
     doubtful; moved to 1.14.1 (has patch fixes, compatible with Spring Boot 3.5)
  2. Model choice: maintenance manuals are in English, where Tongyi Qianwen trails
     DeepSeek; also langchain4j-dashscope moved to the community repo and is no
     longer in the core BOM
- Decision:
  - LangChain4j version → 1.14.1
  - LLM → DeepSeek (strong English, low cost, stable domestic access)
  - Integration → langchain4j-open-ai (OpenAI-compatible mode)
- Pitfalls and fixes:
  - Pitfall: with langchain4j-bom, dashscope reported "version missing"
  - Fix: dropped dashscope, used open-ai (a core module fully covered by the BOM)
- Verification:
  - ./mvnw dependency:tree -Dincludes=dev.langchain4j → no conflicts
  - ./mvnw clean compile → BUILD SUCCESS
```

---

## Code Review Checklist

Self-check every item before submitting a pull request:

### Functional correctness

- [ ] Does the new feature cover the acceptance criteria in the design document?
- [ ] Are boundary conditions handled (null values, over-long input, special characters)?
- [ ] Is error handling complete (meaningful error responses instead of raw 500s)?

### Documentation sync

- [ ] Is **API documentation** (`docs/API.md`) updated for new/modified endpoints?
- [ ] Does **architecture documentation** (`docs/ARCHITECTURE.md`) reflect new modules or data flows?
- [ ] Does every endpoint have a **curl example** that can be copy-pasted and run?
- [ ] Does the README need a sync (new screenshots, quick-start steps)?

### Code quality

- [ ] Is there obvious duplication? Can it be extracted for reuse?
- [ ] Are names self-explanatory (class, method, variable)?
- [ ] Is logging appropriate (INFO/WARN for operational logs, DEBUG for debug logs)?
- [ ] On Java 21 virtual threads, are blocking operations identified? (`synchronized` and `Thread.sleep` pin platform threads)

### Tests

- [ ] Does new business logic have unit tests?
- [ ] Do key endpoints have integration tests (at least the happy path and the main error paths)?
- [ ] Manual acceptance: after starting the app locally, do all acceptance scenarios from the design document pass?

### Security and compliance

- [ ] Any hard-coded secrets (API keys, passwords, private keys)?
- [ ] Are licenses of new dependencies compatible (prefer Apache 2.0 / MIT, avoid GPL)?
- [ ] Is user input validated and filtered?

---

## Branch Model

### Current: GitHub Flow (PR-based, enforced by CI)

```
main  ─────────/\──────────/\──────────
      \        /          /
       feature/voice-input
       fix/session-timeout
       docs/update-deploy-guide
```

Enabled by the contribution-workflow setup (`.github/workflows/`):

- Every change lands on `main` through a **pull request**; direct pushes to `main` should be reserved for trivial cases and still pass CI.
- CI checks on every push and pull request to `main`:
  - **ci-backend**: `./mvnw -B test` on Eclipse Temurin 21 (JDK 21).
  - **ci-frontend**: `npm ci`, `npm test`, `npm run build`, and `npm audit`.
- Branch naming: `feature/…`, `fix/…`, `docs/…`.
- Before merging, the code review checklist above must be walked through.

### Historical: trunk-based development (single-maintainer era)

> Kept for reference only — this was the workflow before pull requests and CI were introduced, and it no longer describes current practice.

```
main  ────────────────────────────────────────────
        ↑        ↑        ↑
      feat-1   feat-2   fix-1
      (short-lived branches, merged after 1–2 commits)
```

It applied when the project had a single maintainer, simple features, and fast iteration: short-lived branches merged straight into `main`, every commit a complete working state, and no WIP commits pushed (squash before pushing).

---

*Last updated: 2026-11-15*
