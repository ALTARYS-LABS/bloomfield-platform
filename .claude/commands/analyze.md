---
description: Run ANALYST Mode A - extend the v3/v4 PRD with resolved inconsistencies and open-question answers
argument-hint: <scope> (v3 | v4)
model: opus
---
## Worktree Setup (MANDATORY - do this FIRST)
1. Run: `git worktree add .claude/worktrees/docs-bloomfield-$1-prd -b docs/bloomfield-$1-prd origin/develop`
   - If the branch already exists: `git worktree add .claude/worktrees/docs-bloomfield-$1-prd docs/bloomfield-$1-prd`
2. **All subsequent work MUST happen inside `.claude/worktrees/docs-bloomfield-$1-prd/`** - use absolute paths.
3. When done: commit, push, and open a PR targeting `develop`.

---

Act as the ANALYST personality defined in `.claude/personalities/ANALYST.md`. Run in **Mode A - PRD Extension**.

Read in order:
1. `docs/bloomfield-terminal-prd-v3-v4.md` (PRIMARY - the v1.0 PRD; treat as immutable source)
2. `docs/BLOOMFIELD_AI_Development_Workflow.md` (workflow + 600-LoC rule + 7 open inconsistencies)
3. `CLAUDE.md` (critical rules 1-14)
4. `stories/index.md` (v2 backlog, locked decisions, Flyway version map)

**Scope argument:** `$1` must be `v3` or `v4`. If missing or invalid, STOP and ask.

**What this command does NOT do:** it does not slice work into stories. Story decomposition is a separate step (`/decompose`) and also an ANALYST responsibility (Mode B).

**Your task**
Interview me using `AskUserQuestionTool` to resolve every ambiguity in the `$1` scope. At minimum, walk through the 7 inconsistencies listed in the workflow doc that fall into the `$1` scope. Be thorough and non-obvious: edge cases, error paths, data-freshness semantics, auth boundaries, offline / WS-disconnect behavior, demo vs. production trade-offs, persistence boundaries, and integration points with the v2 modules already merged.

Output: `docs/prd/bloomfield-$1-prd-extended.md` - a document that SUPPLEMENTS (not replaces) the v1.0 PRD. It must include:
- Decisions log (one line per resolved inconsistency, with the chosen option)
- Answered open questions (Q/A format, referencing PRD section numbers)
- New or tightened acceptance criteria per feature
- Demo-script implications (what the jury must see for each feature)
- An "out of scope for `$1`" section listing what was deliberately deferred

Do NOT write stories. Do NOT write architecture. Do NOT write code.
