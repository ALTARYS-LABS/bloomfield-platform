# ALTARYS LABS — Git Workflow Standards

## Branch Model (Gitflow)

```
origin/main      — production (bloomfield-intelligence.altaryslabs.com)
origin/develop   — staging    (staging-bf-terminal.altaryslabs.com)
```

Feature, fix, and all other work branches are created from `origin/develop` and merged back into `develop` via Pull Request. When `develop` is stable and tested on staging, a PR from `develop` → `main` triggers the production deploy.

---

## Full Workflow (ASCII)

```
origin/develop
      │
      ├──► feat/market-data ──────────────────────────────► PR to develop ──┐
      │                                                                      │
      ├──► fix/cors-headers ──────────────────────────────► PR to develop ──┤
      │                                                                      ▼
      │                                                             origin/develop
      │                                                             (deploys to staging)
      │                                                             (test here)
      │                                                                      │
      │                                                         when stable ▼
      │                                                             PR: develop → main
      │                                                                      │
      └──────────────────────────────────────────────────────────────────────┤
                                                                             ▼
                                                                       origin/main
                                                                    (deploys to prod)
```

**Rule**: Never commit directly to `develop` or `main`.

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

## Keeping Branches Short-Lived (Critical for Team Health)

Long-lived feature branches are the #1 source of painful merge conflicts. Rules to prevent them:

### The golden rule: merge within 1–2 days

A feature branch must be mergeable to `develop` within 1–2 days of creation. If it is not, the branch is too large — split it.

### How to split large features

Use **feature flags** to merge incomplete work safely:

```java
// application.yml
feature-flags:
  new-order-flow: false   // disabled on staging/prod, enabled locally

// In code
if (featureFlags.isEnabled("new-order-flow")) {
    return newOrderFlow.handle(request);
}
return legacyOrderFlow.handle(request);
```

This lets junior devs merge their in-progress work to `develop` without breaking anything. The flag is flipped only when the feature is complete and tested.

### PR size guidelines

| PR size | Lines changed | Status |
|---|---|---|
| Ideal | < 200 lines | Fast to review, easy to merge |
| Acceptable | 200–400 lines | OK for complex features |
| Too large | > 400 lines | Must be split |

### Daily rebase habit

Junior devs should rebase on `develop` every morning to catch conflicts early:

```bash
git fetch origin
git rebase origin/develop
```

Claude Code sessions do this automatically via `/start-session` (which always branches from `origin/develop`).

### No "waiting for review" branches

If a branch is blocked on review for more than a day, the author should ping the reviewer. Branches must not sit idle — they accumulate conflicts.

---

## Release Process (develop → main)

A **release** is the promotion of `develop` to `main`. It is a deliberate event, not a routine merge.

### When to release

- All planned features for the release are merged to `develop`
- `develop` has been tested on staging (check `https://staging-bf-terminal.altaryslabs.com`)
- No known critical bugs on staging

### Steps

1. **Verify staging is green**
   - Open `https://staging-bf-terminal.altaryslabs.com` — app loads correctly
   - Smoke test the key user flows (login, market data, terminal)

2. **Create the release PR**
   ```
   PR: develop → main
   Title: "Release YYYY-MM-DD"
   ```
   The PR description must include:
   - **What's new**: bullet list of all features/fixes merged to `develop` since the last release
   - **How to verify**: what to test on production after deploy
   - **Rollback plan**: what to do if something breaks (usually: revert the merge commit)

3. **Merge and monitor**
   - Merge the PR (squash or merge commit — not rebase, to preserve history)
   - Coolify auto-deploys `main` to production
   - Verify production is live and green within 5 minutes of deploy

4. **Tag the release** (optional but recommended)
   ```bash
   git tag -a v1.2.0 -m "Release 1.2.0 — market data streaming, tenant isolation fix"
   git push origin v1.2.0
   ```

### Release PR template

```markdown
## Release YYYY-MM-DD

### Changes since last release
- feat: market data streaming (PR #X)
- fix: tenant isolation query (PR #Y)
- chore: upgrade Spring Boot (PR #Z)

### Verification
- [ ] Login flow works on prod
- [ ] Market data stream connects
- [ ] No errors in Coolify logs for 5 minutes post-deploy

### Rollback
If prod breaks: revert the merge commit on `main` and push.
Coolify will redeploy the previous state automatically.
```

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
| Rebase on latest develop | `git fetch origin && git rebase origin/develop` |
| Tag a release | `git tag -a v1.x.0 -m "Release ..." && git push origin v1.x.0` |
