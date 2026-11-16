# Real-Model Smoke Report

- Date: 2026-11-16T06:40:59.000Z
- Backend: http://127.0.0.1:18091
- Label: Release acceptance: provider deepseek (api.deepseek.com), model deepseek-flash, synthetic quickstart corpus, single run
- Judge: enabled (model deepseek-flash)
- Model calls consumed by this run: 48 (hard cap 80)
- Set: evals/aviation/golden-set.json

> Real-model smoke against the configured provider and model on the synthetic
> quickstart corpus, at one point in time. It covers exactly that one
> provider/model/corpus combination and nothing else: not other providers, not
> other models, not real manuals, and not production deployments. Credentials
> are supplied through server-side environment variables only and never appear
> in this report. `out_of_scope` items are evaluated against the NO_ANSWER
> outcome — a documented mapping, not a native product route.

## Channel: http

| Gate | Value | Result |
|------|-------|--------|
| route accuracy = 100% | 0.9500 | FAIL |
| zero-call violations = 0 | 1 | FAIL |
| no_answer false-answer rate = 0 | 0.1429 | FAIL |
| rag doc-level hit@5 = 100% | 1 | PASS |
| citations integrity = 100% | 1 | PASS |
| judge keypoint coverage = 100% | 0.3750 | FAIL |
| MRR (report only, no gate) | 1.0000 | - |

| Item | Route | Expected | Actual | Calls | Hit@5 | Result |
|------|-------|----------|--------|-------|-------|--------|
| AVI-001 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | PASS |
| AVI-002 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | PASS |
| AVI-003 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | FAIL (judge: key points not covered: Gives the nose tire cold pressure target (45 psi with a small tolerance). | Gives the main tire cold pressure target (50 psi with a small tolerance).) |
| AVI-004 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | FAIL (judge: key points not covered: Covers propeller and spinner condition. | Covers windshield cleanliness and crack limits. | Covers wing leading edges, control surface movement, antennas and static ports.) |
| AVI-005 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | FAIL (judge: key points not covered: Warns against filling past the upper dipstick mark.) |
| AVI-006 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | FAIL (judge: key points not covered: Covers propeller and spinner condition. | Covers windshield cleanliness and crack limits. | Covers wing leading edges, control surface movement, antennas and static ports.) |
| AVI-007 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | FAIL (judge: key points not covered: Notes that sidewall cracks in the bead-to-shoulder zone and any flat spot are not permitted.) |
| AVI-008 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | PASS |
| AVI-009 | validation_block | VALIDATION_BLOCKED | VALIDATION_BLOCKED | 0 (0..0) | - | PASS |
| AVI-010 | validation_block | VALIDATION_BLOCKED | VALIDATION_BLOCKED | 0 (0..0) | - | PASS |
| AVI-011 | validation_unknown | VALIDATION_UNKNOWN | VALIDATION_UNKNOWN | 0 (0..0) | - | PASS |
| AVI-012 | clarify_model | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | 0 (0..0) | - | PASS |
| AVI-013 | clarify_model | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | 0 (0..0) | - | PASS |
| AVI-014 | no_answer | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-015 | no_answer | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-016 | no_answer | NO_ANSWER | ANSWERED | 2 (0..1) | - | FAIL (route: expected NO_ANSWER, got ANSWERED; calls: expected 0..1, got 2; citations: must be empty on this route; reply: expected fixed notice "未在手册中找到相关内容。") |
| AVI-017 | no_answer | NO_ANSWER | NO_ANSWER | 1 (0..1) | - | PASS |
| AVI-018 | out_of_scope | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-019 | out_of_scope | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-020 | out_of_scope | NO_ANSWER | NO_ANSWER | 1 (0..1) | - | PASS |

## Channel: mcp

| Gate | Value | Result |
|------|-------|--------|
| route accuracy = 100% | 0.9500 | FAIL |
| zero-call violations = 0 | 1 | FAIL |
| no_answer false-answer rate = 0 | 0.1429 | FAIL |
| rag doc-level hit@5 = 100% | 1 | PASS |
| citations integrity = 100% | 1 | PASS |
| judge keypoint coverage = 100% | 0.5000 | FAIL |
| MRR (report only, no gate) | 1.0000 | - |
| Judge faithfulness (report only, no gate) | 8/8 faithful | - |

| Item | Route | Expected | Actual | Calls | Hit@5 | Result |
|------|-------|----------|--------|-------|-------|--------|
| AVI-001 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | PASS |
| AVI-002 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | PASS |
| AVI-003 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | FAIL (judge: key points not covered: Gives the nose tire cold pressure target (45 psi with a small tolerance). | Gives the main tire cold pressure target (50 psi with a small tolerance).) |
| AVI-004 | rag | ANSWERED | ANSWERED | 1 (1..1) | yes | FAIL (judge: key points not covered: Covers propeller and spinner condition. | Covers windshield cleanliness and crack limits. | Covers wing leading edges, control surface movement, antennas and static ports.) |
| AVI-005 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | PASS |
| AVI-006 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | FAIL (judge: key points not covered: Covers wing leading edges, control surface movement, antennas and static ports.) |
| AVI-007 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | FAIL (judge: key points not covered: States the minimum tread depth and that visible fabric requires replacement. | States the cut length and depth limits beyond which the tire is replaced. | Notes that sidewall cracks in the bead-to-shoulder zone and any flat spot are not permitted.) |
| AVI-008 | rag | ANSWERED | ANSWERED | 2 (2..2) | yes | PASS |
| AVI-009 | validation_block | VALIDATION_BLOCKED | VALIDATION_BLOCKED | 0 (0..0) | - | PASS |
| AVI-010 | validation_block | VALIDATION_BLOCKED | VALIDATION_BLOCKED | 0 (0..0) | - | PASS |
| AVI-011 | validation_unknown | VALIDATION_UNKNOWN | VALIDATION_UNKNOWN | 0 (0..0) | - | PASS |
| AVI-012 | clarify_model | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | 0 (0..0) | - | PASS |
| AVI-013 | clarify_model | CLARIFICATION_REQUIRED | CLARIFICATION_REQUIRED | 0 (0..0) | - | PASS |
| AVI-014 | no_answer | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-015 | no_answer | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-016 | no_answer | NO_ANSWER | ANSWERED | 2 (0..1) | - | FAIL (route: expected NO_ANSWER, got ANSWERED; calls: expected 0..1, got 2; citations: must be empty on this route; reply: expected fixed notice "未在手册中找到相关内容。") |
| AVI-017 | no_answer | NO_ANSWER | NO_ANSWER | 1 (0..1) | - | PASS |
| AVI-018 | out_of_scope | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-019 | out_of_scope | NO_ANSWER | NO_ANSWER | 0 (0..0) | - | PASS |
| AVI-020 | out_of_scope | NO_ANSWER | NO_ANSWER | 1 (0..1) | - | PASS |
