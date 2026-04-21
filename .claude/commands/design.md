---
description: Produce the terminal-grade UX/UI design for Bloomfield v3 or v4
argument-hint: <scope> (v3 | v4)
model: opus
---
## Worktree Setup (MANDATORY - do this FIRST)
1. Run: `git worktree add .claude/worktrees/docs-bloomfield-$1-design -b docs/bloomfield-$1-design origin/develop`
   - If the branch already exists: `git worktree add .claude/worktrees/docs-bloomfield-$1-design docs/bloomfield-$1-design`
2. **All subsequent work MUST happen inside `.claude/worktrees/docs-bloomfield-$1-design/`** - use absolute paths.
3. When done: commit, push, and open a PR targeting `develop`.

---

Act as the DESIGNER personality defined in `.claude/personalities/DESIGNER.md`.

Read in order:
1. `docs/bloomfield-terminal-prd-v3-v4.md` (PRIMARY - v1.0 PRD)
2. `docs/prd/bloomfield-$1-prd-extended.md` (output of `/analyze $1`)
3. `docs/architecture/bloomfield-arch-$1.md` (output of `/architect $1`)
4. `docs/BLOOMFIELD_AI_Development_Workflow.md`

**Scope argument:** `$1` must be `v3` or `v4`. If missing or invalid, STOP and ask.

**Phase 1 - UX Interview (mandatory)**
Before writing the design doc, interview me using `AskUserQuestionTool` about every non-trivial UX decision for the `$1` scope. Identify 5-15 decisions, present options with tradeoffs, explain the reasoning, and let me choose. Use ASCII wireframes in the `markdown` preview field when comparing layouts.

**Phase 2 - Design Document**
Produce `docs/design/bloomfield-design-$1.md` incorporating all interview decisions. Required sections:
1. Design principles (terminal-grade density; Bloomberg/Refinitiv posture; no SaaS dashboard aesthetics)
2. Color semantics - up-tick / down-tick / stale / halted / alert-fired / auth-state; contrast ratios
3. Typography & numeric alignment - monospace for prices, tabular-nums, right-aligned
4. Screen-by-screen wireframe descriptions (ASCII + prose)
5. Interaction specs - keyboard shortcuts, right-click context, drag-resize panes
6. **5 UI states for every data surface** - loading, empty, error, stale (WS disconnected), success. Include exact French micro-copy.
7. Responsive behavior - desktop-first (1440+); tablet (768+) acceptable; mobile not a target for terminal views
8. Stale-data semantics - when staleness threshold trips, what changes visually and functionally
9. Component specifications for any new primitives

At the start of each wireframe section, note which Phase 1 decision(s) shaped it.

This is a terminal for financial professionals. Information density, glanceability, and keyboard efficiency beat whitespace and gentle motion every time.
