---
description: Find 3-5 high-leverage improvements in recent changes
disable-model-invocation: true
---
Find 3-5 high-leverage improvements in the recent codebase changes on this branch.

For each finding, list:
- Files and line ranges
- Impact (why it matters for the RFP demo or for correctness)
- Risk of fixing now vs. later
- Verification steps (tests to add/run, commands to execute)

Focus areas, in order:
1. Duplication (copy-paste between modules)
2. Missing error handling on WS / REST boundaries
3. Missing tests on money math (`BigDecimal` paths especially)
4. Performance issues (N+1 queries, missing indexes on hot paths, unbounded fan-out)
5. CLAUDE.md rule violations that slipped through (em dashes, English comments, raw `JdbcTemplate` outside `internal/`, `public` defaulted where not needed)

Keep each finding ≤ 8 lines. No essays.
