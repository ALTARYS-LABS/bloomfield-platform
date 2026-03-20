# STORY-001 — Enforce Gitflow via GitHub Branch Protection

**Type**: chore
**Status**: todo
**Branch**: `chore/enforce-gitflow-branch-protection`

---

## Context

PR #13 adds a GitHub Actions workflow (`.github/workflows/enforce-gitflow.yml`) that fails if a PR targets `main` from any branch other than `develop`. The workflow file is ready and merged, but the GitHub branch protection rule that makes it **required** has not been configured yet — without it, the check runs but does not block the merge button.

---

## What Needs to Be Done

### Step 1 — Merge PR #13 to develop, then develop to main
The workflow file must exist on `main` before GitHub can register it as a required status check.

### Step 2 — Configure branch protection rule on main (manual, GitHub UI)

Go to: GitHub repo → **Settings → Branches → Add branch protection rule**

| Setting | Value |
|---|---|
| Branch name pattern | `main` |
| Require a pull request before merging | ✅ |
| Require status checks to pass before merging | ✅ |
| Required check | `Source branch must be develop` |
| Require branches to be up to date before merging | ✅ |
| Do not allow bypassing the above settings | ✅ (recommended) |

### Step 3 — Configure branch protection rule on develop (optional but recommended)

Same settings on `develop` to prevent direct pushes:

| Setting | Value |
|---|---|
| Branch name pattern | `develop` |
| Require a pull request before merging | ✅ |
| Require status checks to pass before merging | ✅ (CI tests when added) |
| Do not allow bypassing | ✅ |

---

## Acceptance Criteria

- [ ] A PR from `feat/any-branch` → `main` shows a failing required check and the merge button is disabled
- [ ] A PR from `develop` → `main` shows a passing check and can be merged
- [ ] Direct push to `main` is rejected by GitHub
- [ ] Direct push to `develop` is rejected by GitHub

---

## Related Files

- `.github/workflows/enforce-gitflow.yml` — the Actions workflow (already committed in PR #13)
- `standards/git-workflow.md` — the Gitflow rules this story enforces
