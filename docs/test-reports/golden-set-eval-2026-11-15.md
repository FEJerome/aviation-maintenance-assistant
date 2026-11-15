# Golden-Set Evaluation Report

- Date: 2026-11-15T15:56:17.992Z
- Backend: http://127.0.0.1:18091 (loopback)
- Fake LLM: http://127.0.0.1:18089 (loopback, deterministic; translation map via FAKE_TRANSLATION_MAP)
- Set: evals/aviation/golden-set.json

> Loopback-only deterministic evaluation. This report does not cover real models,
> real providers, public networks, HTTPS, or production deployments.
> `out_of_scope` items are evaluated against the NO_ANSWER outcome — a documented
> mapping, not a native product route.

## Channel: http

| Gate | Value | Result |
|------|-------|--------|
| route accuracy = 100% | 1 | PASS |
| zero-call violations = 0 | 0 | PASS |
| no_answer false-answer rate = 0 | 0 | PASS |
| rag doc-level hit@5 = 100% | 1 | PASS |
| citations integrity = 100% | 1 | PASS |
| MRR (report only, no gate) | 1.0000 | - |

| Item | Route | Expected | Actual | Calls | Hit@5 | Result |
|------|-------|----------|--------|-------|-------|--------|
| AVI-001 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | PASS |
| AVI-002 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | PASS |
| AVI-003 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | PASS |
| AVI-004 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | PASS |
| AVI-005 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | PASS |
| AVI-006 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | PASS |
| AVI-007 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | PASS |
| AVI-008 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | PASS |
| AVI-009 | validation_block | VALIDATION_BLOCKED | VALIDATION_BLOCKED | 0 (0..0) | - | PASS |
| AVI-010 | validation_block | VALIDATION_BLOCKED | VALIDATION_BLOCKED | 0 (0..0) | - | PASS |
| AVI-011 | validation_unknown | VALIDATION_UNKNOWN | VALIDATION_UNKNOWN | 0 (0..0) | - | PASS |
| AVI-012 | clarify_model | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | 0 (0..0) | - | PASS |
| AVI-013 | clarify_model | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | 0 (0..0) | - | PASS |
| AVI-014 | no_answer | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-015 | no_answer | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-016 | no_answer | NO_ANSWER | NO_ANSWER | 1 (0..1) | - | PASS |
| AVI-017 | no_answer | NO_ANSWER | NO_ANSWER | 1 (0..1) | - | PASS |
| AVI-018 | out_of_scope | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-019 | out_of_scope | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-020 | out_of_scope | NO_ANSWER | NO_ANSWER | 1 (0..1) | - | PASS |

## Channel: mcp

| Gate | Value | Result |
|------|-------|--------|
| route accuracy = 100% | 1 | PASS |
| zero-call violations = 0 | 0 | PASS |
| no_answer false-answer rate = 0 | 0 | PASS |
| rag doc-level hit@5 = 100% | 1 | PASS |
| citations integrity = 100% | 1 | PASS |
| MRR (report only, no gate) | 1.0000 | - |

| Item | Route | Expected | Actual | Calls | Hit@5 | Result |
|------|-------|----------|--------|-------|-------|--------|
| AVI-001 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | PASS |
| AVI-002 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | PASS |
| AVI-003 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | PASS |
| AVI-004 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | PASS |
| AVI-005 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | PASS |
| AVI-006 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | PASS |
| AVI-007 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | PASS |
| AVI-008 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | PASS |
| AVI-009 | validation_block | VALIDATION_BLOCKED | VALIDATION_BLOCKED | 0 (0..0) | - | PASS |
| AVI-010 | validation_block | VALIDATION_BLOCKED | VALIDATION_BLOCKED | 0 (0..0) | - | PASS |
| AVI-011 | validation_unknown | VALIDATION_UNKNOWN | VALIDATION_UNKNOWN | 0 (0..0) | - | PASS |
| AVI-012 | clarify_model | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | 0 (0..0) | - | PASS |
| AVI-013 | clarify_model | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | 0 (0..0) | - | PASS |
| AVI-014 | no_answer | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-015 | no_answer | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-016 | no_answer | NO_ANSWER | NO_ANSWER | 1 (0..1) | - | PASS |
| AVI-017 | no_answer | NO_ANSWER | NO_ANSWER | 1 (0..1) | - | PASS |
| AVI-018 | out_of_scope | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-019 | out_of_scope | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-020 | out_of_scope | NO_ANSWER | NO_ANSWER | 1 (0..1) | - | PASS |
