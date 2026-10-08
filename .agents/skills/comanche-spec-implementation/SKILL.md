---
name: comanche-spec-implementation
description: Implement one Project Comanche specification or milestone with requirement traceability, tests, and validation. Use when asked to implement a Comanche specification or milestone.
---

# Comanche Specification Implementation

## Procedure

1. Read the requested specification and relevant dependent specifications.
2. Inspect the current Maven modules and existing implementation.
3. Extract functional requirements and acceptance criteria.
4. Identify affected modules, classes, configuration, and tests.
5. Present a concise implementation plan.
6. Implement only the requested scope.
7. Add unit tests and applicable integration tests.
8. Run `mvn clean verify`, or explain why it cannot run.
9. Compare the implementation against the acceptance criteria.
10. Report completed requirements, outstanding items, test results, and suggested commit message.

## Constraints

- Follow repository `AGENTS.md`.
- Preserve existing architectural boundaries.
- Do not silently change approved specifications.
- Do not invent requirements.
- Do not automatically commit or push changes.
- Never introduce real credentials into tracked files.

## Completion Report

Include:
- Specification and milestone implemented
- Requirements completed
- Files changed
- Tests executed and results
- Integration dependencies
- Remaining issues
- Suggested commit message
