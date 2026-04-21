---
description: ANALYST Mode B - decompose approved architecture into 600-LoC vertical-slice stories
argument-hint: <scope> (v3 | v4)
model: opus
---
## Worktree Setup (MANDATORY - do this FIRST)
1. Run: `git worktree add .claude/worktrees/docs-bloomfield-$1-stories -b docs/bloomfield-$1-stories origin/develop`
   - If the branch already exists: `git worktree add .claude/worktrees/docs-bloomfield-$1-stories docs/bloomfield-$1-stories`
2. **All subsequent work MUST happen inside `.claude/worktrees/docs-bloomfield-$1-stories/`** - use absolute paths.
3. When done: commit, push, and open a PR targeting `develop`.

---

Act as the ANALYST personality in **Mode B - Story Decomposition** (see `.claude/personalities/ANALYST.md`).

Read in order:
1. `docs/architecture/bloomfield-arch-$1.md` (REQUIRED - must contain Vertical Slice Candidates)
2. `docs/prd/bloomfield-$1-prd-extended.md` (REQUIRED - acceptance-criteria source)
3. `docs/design/bloomfield-design-$1.md` (REQUIRED - UI states and French copy)
4. `docs/bloomfield-terminal-prd-v3-v4.md` (v1.0 PRD - original acceptance criteria)
5. `docs/BLOOMFIELD_AI_Development_Workflow.md` (600-LoC hard cap + story template)
6. `standards/git-workflow.md` (branch naming, PR size rules)
7. `stories/index.md` (existing story IDs and Flyway version map - do NOT collide)

**Scope argument:** `$1` must be `v3` or `v4`. If missing or invalid, STOP and ask.

**Your task**
Turn the ARCHITECT's Vertical Slice Candidates into authoritative, self-contained story files. You are the only personality allowed to slice.

Hard rules (from the workflow doc):
- **PR ≤ 600 LoC total (frontend + backend + tests)** - if a candidate exceeds the estimate, pre-split it here
- Each story is one vertical slice: end-user-visible outcome + tests, end-to-end
- Story IDs continue the existing sequence in `stories/index.md` (currently ends at STORY-011)
- Each story owns the Flyway migration(s) for tables it introduces (never an umbrella "data model" story)
- 2-4 acceptance criteria per story - split if more
- French copy embedded verbatim for every UI state - never "see design spec"

Output:
- One story file per slice at `stories/STORY-NNN-<slug>.md` (flat layout, matching existing convention)
- Update `stories/index.md` - append new rows, update dependency graph, update Flyway version map, bump Total-stories count

Run every Decomposition Quality Check in `.claude/personalities/ANALYST.md` before finalizing. Do NOT write code. Do NOT modify architecture or design docs.
