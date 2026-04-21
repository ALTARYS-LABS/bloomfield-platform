---
description: Design the technical architecture for Bloomfield v3 or v4
argument-hint: <scope> (v3 | v4)
model: opus
---
## Worktree Setup (MANDATORY - do this FIRST)
1. Run: `git worktree add .claude/worktrees/docs-bloomfield-$1-arch -b docs/bloomfield-$1-arch origin/develop`
   - If the branch already exists: `git worktree add .claude/worktrees/docs-bloomfield-$1-arch docs/bloomfield-$1-arch`
2. **All subsequent work MUST happen inside `.claude/worktrees/docs-bloomfield-$1-arch/`** - use absolute paths.
3. When done: commit, push, and open a PR targeting `develop`.

---

Act as the ARCHITECT personality defined in `.claude/personalities/ARCHITECT.md`.

Read in order:
1. `docs/bloomfield-terminal-prd-v3-v4.md` (PRIMARY - v1.0 PRD)
2. `docs/prd/bloomfield-$1-prd-extended.md` (MUST exist - output of `/analyze $1`. If missing, STOP.)
3. `docs/BLOOMFIELD_AI_Development_Workflow.md` (pipeline + 600-LoC rule)
4. `CLAUDE.md` (critical rules)
5. `stories/index.md` (v2 modules already merged; reuse their patterns)

**Scope argument:** `$1` must be `v3` or `v4`. If missing or invalid, STOP and ask.

**What this command does NOT do:** it does not slice work into stories. The ARCHITECT proposes a Vertical Slice Candidates section ordered by dependency, but the authoritative story decomposition is done by the ANALYST in `/decompose`.

**Your task**
Produce `docs/architecture/bloomfield-arch-$1.md` containing:
1. System context - how the `$1` features plug into the v2 Modulith skeleton (marketdata, user, portfolio, alerts, ohlcv)
2. Data model - ERD in Mermaid, Flyway version map (reserve next `Vxxx__` per CLAUDE.md rule 14)
3. API design - REST + STOMP topics, request/response contracts
4. WebSocket / streaming architecture - channels, auth, backpressure, disconnection handling
5. Security architecture - JWT boundaries, role model (ADMIN/ANALYST/VIEWER), rate limits
6. Sequence diagrams for the demo-critical workflows
7. Performance considerations - latency budgets, fan-out sizing, tick rates
8. Integration points - cross-module events (Spring Modulith), ordering guarantees
9. **Vertical Slice Candidates** - ordered by dependency and business value. For each: rough scope, back-of-envelope LoC estimate, risk flags. **Flag every candidate whose estimate exceeds 600 LoC with a `⚠️ must-split` marker.** Do not author the story files themselves.
10. Technical risks and mitigations

Hard constraints (from CLAUDE.md):
- Spring Data JDBC only (no JPA)
- `BigDecimal` for all monetary amounts
- Single-tenant (no multi-tenancy scaffolding)
- Default package-private; records over classes for stateless beans (except CGLIB-proxied ones)
- No Redis for v2/v3 unless a concrete bottleneck justifies it
