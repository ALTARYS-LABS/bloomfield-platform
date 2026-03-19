# ALTARYS LABS — Git Workflow Standards

## Branch Model (Gitflow)

```
origin/main      — production (bloomfield-intelligence.altaryslabs.com)
origin/develop   — staging    (staging-bf-terminal.altaryslabs.com)
```

Feature, fix, and all other work branches are created from `origin/develop` and merged back into `develop` via Pull Request. When `develop` is stable and tested on staging, a PR from `develop` → `main` triggers the production deploy.

```
origin/develop ──► feature branch ──► PR to develop ──► PR to main ──► production
```

Never commit directly to `develop` or `main`.

---

## Branch Naming Convention

Pattern: `<type>/<slug>`

| Type | When to use |
|---|---|
| `feat` | New feature or capability |
| `fix` | Bug fix |
| `chore` | Tooling, dependencies, CI/CD, configuration |
| `docs` | Documentation only |
| `refactor` | Code restructuring without behavior change |
| `test` | Test additions or corrections |
| `style` | Formatting, linting, whitespace |

Rules for `<slug>`:
- Lowercase letters, digits, underscores, and hyphens only
- Must be meaningful (describes the work)
- Examples: `feat/market-data-streaming`, `fix/tenant-isolation-query`, `chore/upgrade-spring-boot`

---

## Worktree Isolation (Claude Code Sessions)

Every Claude Code session MUST work in a dedicated git worktree — never on the main checkout or directly on `develop` or `main`.

Worktrees live under `.claude/worktrees/`.

### Starting a session

The very first action of every session is:

```
/start-session <type>/<slug>
```

This creates an isolated worktree at `.claude/worktrees/<slug>` on a new branch based on `origin/develop`.

If Claude has already made changes without a worktree, it MUST stop, warn the user, and offer to create the worktree and move the work there before continuing.

If Claude detects it is working on `main`, `develop`, or a branch not matching the current task, it MUST warn the user and offer to create a worktree before proceeding.

### Worktree creation (what `/start-session` does internally)

```bash
git fetch origin develop
git worktree add .claude/worktrees/<slug> -b <type>/<slug> origin/develop
```

### Finishing a session

Run `/commit-push-pr` from inside the worktree. The PR targets `develop`, not `main`.

---

## Pull Request Rules

- All feature/fix/chore PRs target `develop` (never directly to `main`)
- `develop` → `main` PRs are release-level events: must include a summary of all changes since the last release
- CI must be green before merge (tests + lint)
- PR description must describe what changed and why

---

## Quick Reference

| Action | Command |
|---|---|
| Start a session | `/start-session feat/my-feature` |
| Commit, push, open PR | `/commit-push-pr` |
| List active worktrees | `git worktree list` |
| Remove a merged worktree | `git worktree remove .claude/worktrees/<slug>` |
