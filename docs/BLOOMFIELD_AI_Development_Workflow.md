# Bloomfield Terminal - AI-Assisted Development Workflow

*v1.0 - April 2026*
*Tech stack: Java 25 / Spring Boot 4.0.3 / React 19 / TypeScript / Vite / PostgreSQL + TimescaleDB*
*Context: AO_BI_2026_001 - Bloomfield Intelligence RFP (groupement IBEMS + ALTARYS LABS)*

---

## Purpose

This document defines the AI-assisted development workflow for **Bloomfield Terminal prototype v3 and v4**. It is a trimmed, single-app adaptation of the ALTARYS ENTERPRISE workflow (v7.1). Bloomfield is a prototype, not a 12-module multi-tenant platform, so several stages from the parent workflow are removed:

| Removed from ALTARYS workflow | Why |
|---|---|
| LEGAL_AUDITOR + Perplexity research pipeline | Bloomfield is a financial-analytics terminal, not an OHADA-legal-rules engine. No cited regulatory rules to audit. |
| PRODUCT_OWNER (story decomposition) | Collapsed into ANALYST (see below). |
| QA_VALIDATOR | Absorbed by REVIEWER - one pass, code + browser check + French copy. |
| FRENCH_TRANSLATOR | French is primary from day one; no EN→FR translation stage. |
| 3-Tier specification (Platform Vision → Module PRDs → Slices) | Single app, single PRD - one tier. |
| Multi-tenant / 4 DB profile concerns | Single-tenant (see `CLAUDE.md` decision log). |

---

## Deployed Files Reference

| Content | Location |
|---|---|
| 5 personality system prompts | `.claude/personalities/*.md` |
| Project-wide AI context | `CLAUDE.md` (existing - authoritative) |
| PRD (input) | `docs/bloomfield-terminal-prd-v3-v4.md` |
| PRD+FSD (extended output, ANALYST) | `docs/prd/bloomfield-prd-v3-v4.md` |
| Architecture document | `docs/architecture/bloomfield-arch-v3.md`, `-v4.md` |
| Design specification | `docs/design/bloomfield-design-v3.md`, `-v4.md` |
| Story files | `stories/STORY-<NNN>-*.md` (existing convention) |
| Story index | `stories/index.md` |
| Wireframes | `docs/wireframes/<STORY-ID>/` |
| Reviews | `docs/reviews/<STORY-ID>-review.md` |
| Coding standards | `standards/*.md` |

---

## The 5-Personality Pipeline

Human steps are mandatory gates - they cannot be automated or skipped.

| # | Personality | File | Step | Role |
|---|---|---|---|---|
| 1 | **ANALYST** | `ANALYST.md` | 1a | Extend PRD: FSD, state machines, decision tables, fix inconsistencies |
| - | *Human: You* | - | 1b | Approve PRD v1.1 |
| 1' | **ANALYST** | `ANALYST.md` | 1c | Decompose into stories (≤600 LoC PR each) |
| - | *Human: You* | - | 1d | Approve story backlog |
| 2 | **ARCHITECT** | `ARCHITECT.md` | 2a | Technical design + vertical-slice candidates |
| 3 | **DESIGNER** | `DESIGNER.md` | 2b | Visual spec (parallel with ARCHITECT) |
| - | *Human: You* | - | 3 | Review arch + design |
| - | *Human: You* | - | 4 *(optional)* | Create wireframe for UI stories (see below) |
| 4 | **DEVELOPER** | `DEVELOPER.md` | 5 | Implement one story at a time |
| 5 | **REVIEWER** | `REVIEWER.md` | 6 | Code review + security + browser/QA check |
| - | *Human: You* | - | 7 | Merge PR |
| - | *Human: You* | - | 8 | Update `CLAUDE.md` if lessons learned |

**Why ANALYST slices (not ARCHITECT)**: ARCHITECT designs the system; enforcing a PR-size budget is a separate discipline that benefits from knowing the PRD verbatim (what can be deferred, what is a foundation block). Collapsing it into ANALYST keeps architecture decisions *technical* and story decisions *scope-driven*.

**Why REVIEWER also does QA**: single-reviewer gate matches the size of the team and the prototype scope. REVIEWER's prompt includes a mandatory browser-verification checklist.

**Fresh sessions matter**: launch each personality with `claude --system-prompt-file .claude/personalities/<PERSONALITY>.md` so Claude is not biased by context from a previous role (Writer/Reviewer pattern).

---

## The 600-Line PR Rule - Non-Negotiable

Every story must be defined so its resulting PR does **not exceed 600 lines of code**, combined frontend + backend, **including tests** (excluding lockfiles, generated code, and binary assets).

**ANALYST enforces this at decomposition time.** If a feature naturally exceeds 600 lines, it is split into multiple sequentially-numbered stories with explicit dependencies. No story is merged "just a bit over" - if the PR blows the budget during implementation, DEVELOPER stops and files a split.

**Budget breakdown (informal):**
- ≤200 lines: S - usually one concern (new endpoint, UI component, migration)
- 200–400 lines: M - full vertical slice of one small feature
- 400–600 lines: L - foundation slice, split candidate if friction

**Foundation stories** (multi-window shell, back-office scaffolding, real-time order book panel) will push against 600 lines. ANALYST must pre-split them, for example:
- F-V3-01 Multi-Window Floating Interface → 3 stories: (a) panel manager + drag/resize, (b) per-user persistence + restore, (c) named layout presets + admin default.
- F-V3-05 Back-Office → 3 stories: (a) admin shell + routing + audit log, (b) ratings CRUD form, (c) generic data-domain form pattern reused for macro/fundamentals/calendar.

**What the LoC cap buys you**: short-lived branches (≤2 days), reviewable PRs, predictable cadence, low merge-conflict surface, faster jury-facing demo iterations.

---

## Wireframe Step (optional, UI stories only)

For stories introducing a new screen or a complex UI (multi-window workspace, heatmap, order book, back-office forms), create a wireframe before DEVELOPER.

### Two-step process

**Step 1 - Layout sketch (v0.dev)**
1. Go to https://v0.dev
2. Describe the screen from the story's acceptance criteria
3. Screenshot → `docs/wireframes/<STORY-ID>/v0-preview.png`

**Step 2 - Runnable prototype (frontend-design skill)**
```bash
# In Claude Code:
/frontend-design
# Input: acceptance criteria + v0 layout intent + brand tokens
# Output: docs/wireframes/<STORY-ID>/prototype.tsx
```

### When to wireframe

| Create wireframe | Skip wireframe |
|---|---|
| Multi-window workspace shell (F-V3-01) | Pure backend stories |
| Order book panel (F-V3-02) | Minor label / copy changes |
| Heatmap (F-V3-03) | Stories reusing an existing wireframe |
| Back-office forms (F-V3-05) | |
| Fundamental sheet (F-V4-02) | |
| Macro dashboard (F-V4-04) | |

### Convention

```
docs/wireframes/
└── <STORY-ID>/
    ├── v0-preview.png
    ├── prototype.tsx
    └── README.md (optional design notes)
```

---

## Launching Personalities

```bash
claude --system-prompt-file .claude/personalities/<PERSONALITY>.md
```

Each personality reads `CLAUDE.md` automatically. Always confirm the target prototype version (v3 vs v4) at session start.

---

## Specification Flow

Bloomfield is a single app, so the 3-tier strategy from the parent workflow collapses to a single-tier flow:

1. **Input PRD** - `docs/bloomfield-terminal-prd-v3-v4.md` (exists, BT-PRD-002 v1.0).
2. **Extended PRD+FSD** - produced by ANALYST in step 1a. Adds functional rules, state machines (Mermaid), decision tables, resolves the seven inconsistencies flagged during workflow setup (default-layout conflict, back-office dependency chain, deferred 4-eyes, bond-math precision, demo-data honesty, etc.), and fills Open Questions.
3. **Stories** - produced by ANALYST in step 1c. One story per vertical slice, each under the 600-LoC cap. Stories live at `stories/STORY-<NNN>-<kebab-title>.md`, index at `stories/index.md`. The existing v2 numbering continues: v3 stories start at the first unused STORY-NNN.
4. **Architecture** - produced by ARCHITECT per prototype version (`docs/architecture/bloomfield-arch-v3.md`, `-v4.md`). Covers data model, API, real-time topics, TimescaleDB usage, migration plan, and *proposes* vertical-slice candidates. **Does not author stories** - those are ANALYST's output.
5. **Design** - produced by DESIGNER per prototype version (`docs/design/bloomfield-design-v3.md`, `-v4.md`). Covers visual reference (Bloomberg / Refinitiv density), color semantics (bid/ask, rating outlook, staleness, confirmed/indicative), typography, panel chrome, and the five UI states (loading / empty / error / offline / success) in French.

Steps 4 and 5 run in parallel after the story backlog is approved.

---

## Decisions Locked for v3 and v4 Workflow Setup

These were confirmed at workflow-definition time (21/04/2026):

| Decision | Choice |
|---|---|
| PRD posture | ANALYST extends existing PRD, produces PRD v1.1, then slices stories. |
| PR size | **Hard cap 600 lines** (FE+BE combined, including tests). |
| Personalities | 5: ANALYST, ARCHITECT, DESIGNER, DEVELOPER, REVIEWER. |
| Visual fidelity | DESIGNER persona mandatory - visual impression drives jury evaluation. |
| Back-office 4-eyes (F-V3-05) | **Deferred to v4.** v3 ships single-ADMIN edit with audit log. |
| Email alerts (F-V4-06) | Flag as optional in v4 - SMTP is new infra. |
| PDF report (F-V4-09) | Confirm library choice at architecture time. |
| Bond math (F-V4-03) | Architecture must fix YTM method (recommended: Newton-Raphson, tolerance 1e-6). |
| Demo honesty | Every "live" label must read "simulé" in the demo build; real feed is Sikafinance adapter only (STORY-010). |

---

## Inconsistencies ANALYST Must Resolve in PRD v1.1

Seven items surfaced during workflow setup. ANALYST interviews on each before writing PRD v1.1:

1. **Default-layout collision** - F-V3-01 (ADMIN sets default per role) vs F-V4-10 (profile drives default). Reconcile: v3 layout default is scoped to *role*; v4 supersedes with *profile*. Flag as refactor in v4.
2. **Back-office dependency chain** - F-V3-04, F-V4-02, F-V4-04, F-V4-07 all depend on F-V3-05. Make explicit.
3. **F-V3-05 four-eyes** - deferred to v4 per decision above; PRD v1.1 rewords v3 scope accordingly.
4. **F-V4-03 bond math precision** - "standard formulae" is ambiguous; fix algorithm and tolerance.
5. **F-V3-02 order book wording** - "Live BRVM" must carry a simulated-data disclaimer consistent with §5.
6. **F-V4-06 email** - downgrade to "in-app only, email is stretch goal".
7. **F-V4-09 PDF report** - flag library choice as an Open Question for ARCHITECT.

---

## Story File Format (inherited from v2 conventions)

Stories must match the existing format used in `stories/STORY-002` through `STORY-011`: goal, prerequisite reading, step-by-step plan, Flyway migration (if any), acceptance criteria, manual verification, out-of-scope, PR-size estimate, related files.

**Additions for v3/v4:**
- `PRD Features`: list of F-V3-XX / F-V4-XX covered by this story.
- `LoC budget`: estimated and hard-capped at 600.
- `UI states required`: checklist of the 5 states with French copy.
- `Wireframe`: path to `docs/wireframes/<STORY-ID>/` or "N/A - backend only".

---

## Hard Blockers (inherited from `CLAUDE.md`)

Every DEVELOPER session must respect these before proposing a merge:

- `./gradlew test` green, `./gradlew spotlessApply` clean.
- `pnpm lint` AND `pnpm build` green (`tsc -b` catches broken TS not imported by tests).
- `docker compose up -d && ./gradlew bootRun` starts the app.
- CI actually runs and passes on the PR branch (check runs visible).
- Flyway version reserved in PR description; bump if another migration lands first.
- All source-code comments in French (Rule 11, `CLAUDE.md`).
- No em dash anywhere (Rule 10).
- BigDecimal for all monetary amounts (Rule 2).
- Client-generated UUID entities implement `Persistable<UUID>` (Rule 13).

---

## File Summary

Workflow setup produces:

```
docs/BLOOMFIELD_AI_Development_Workflow.md  ← this file
.claude/personalities/ANALYST.md
.claude/personalities/ARCHITECT.md
.claude/personalities/DESIGNER.md
.claude/personalities/DEVELOPER.md
.claude/personalities/REVIEWER.md
```

Nothing else is generated at workflow-setup time. PRD v1.1, architecture, design, and stories are produced by running the personalities themselves.

---

*Document version: 1.0 - 21st April 2026*
*Based on: ALTARYS ENTERPRISE AI-Assisted Development Workflow v7.1*
*Scope: Bloomfield Terminal prototype v3 and v4*
