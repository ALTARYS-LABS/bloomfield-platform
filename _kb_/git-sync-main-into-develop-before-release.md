# Syncing `main` into `develop` before a release PR (merge, not rebase)

## When you reach for this tutorial

You open a release PR (`develop` → `main`) and GitHub shows **"This branch has conflicts that must be resolved"**. Usually this happens because:

- A hotfix was merged directly to `main` and never replayed on `develop`.
- A previous release PR was merged on GitHub with the "Create a merge commit" option, producing a merge commit on `main` that `develop` has never seen.
- Someone edited a shared file (e.g. `CLAUDE.md`, `docker-compose.yml`) on both branches independently.

Symptom: `main` has commits that are NOT reachable from `develop`. In git terms, `main`'s tip is **not an ancestor of** `develop`.

This tutorial explains:
1. What "ancestor" means and why the release PR needs `main`'s tip to be one.
2. The sync-branch pattern (Path 1): merge `main` into a new branch, open a PR into `develop`, then the release PR becomes a trivial fast-forward.
3. Why **merge** is the correct tool here and why **rebase** would be wrong.

---

## 1. The "ancestor" concept

A commit `X` is an **ancestor** of commit `Y` if you can walk backwards from `Y` through parent pointers and eventually land on `X`.

Every commit has one parent, EXCEPT merge commits which have two (or more). When walking back from a merge commit, BOTH parent chains are explored — everything reachable through either parent counts as an ancestor.

**Why it matters for a PR**: a PR from `develop` → `main` can be merged as a **fast-forward** (no new merge commit, just move `main`'s pointer) if and only if `main`'s tip is already an ancestor of `develop`'s tip. If it is not, git has to build a three-way merge and may hit conflicts.

Our release PRs target a clean fast-forward: `main` simply advances to what `develop` already is. That only works if every commit currently on `main` is reachable from `develop`.

---

## 2. The problem, in a figure

Assume `main` has 5 commits `m1..m5` that `develop` never saw, and `develop` has its own work culminating in `STORY-011`:

```
Before sync:
               main (01eea4d)
                ↓
A ──► m1 ──► m2 ──► m3 ──► m4 ──► m5
 \
  \─► d1 ──► ... ──► STORY-011 (306409b)
                      ↑
                      develop
```

Walking backwards from `STORY-011`: `... → d1 → A`. We never reach `m5`. So `m5` is NOT an ancestor of `develop`. Release PR is blocked.

---

## 3. The fix: a sync branch with a merge commit

### Step 1 — create a branch from `develop` and merge `main` into it

```bash
git fetch origin
git worktree add .claude/worktrees/sync-main-into-develop \
  -b chore/sync-main-into-develop origin/develop
cd .claude/worktrees/sync-main-into-develop
git merge origin/main
```

Resolve any conflicts (see section 5 for the "keep develop" strategy we used for `CLAUDE.md` and `docker-compose.yml`), then commit the merge.

```
After step 1:
               main (01eea4d)
                ↓
A ──► m1 ──► ... ──► m5 ────────────────┐
 \                                       ↘
  \─► d1 ──► ... ──► STORY-011 ────────► SYNC
                                          ↑
                                          chore/sync-main-into-develop
```

`SYNC` is a merge commit with **two parents**: `STORY-011` (from develop) and `m5` (from main). Walking back from `SYNC` now reaches `m5` via the second-parent link. That is the whole trick.

### Step 2 — PR the sync branch into `develop`

```bash
git push -u origin chore/sync-main-into-develop
gh pr create --base develop --title "chore: sync main into develop before release"
```

Review, merge. `develop` now points to `SYNC` (or to its fast-forward equivalent).

```
After step 2:
A ──► m1 ──► ... ──► m5 ────────────────┐
 \                                       ↘
  \─► d1 ──► ... ──► STORY-011 ────────► SYNC
                                          ↑
                                          develop (new tip)
```

### Step 3 — the release PR is now a fast-forward

`main`'s tip is `m5`. Walking back from `develop`'s new tip (`SYNC`) hits `m5` via the second parent. `m5` is now an ancestor of `develop`. The release PR `develop` → `main` becomes a clean fast-forward: GitHub just advances `main` to `SYNC`.

```
After release PR merged:
A ──► m1 ──► ... ──► m5 ────────────────┐
 \                                       ↘
  \─► d1 ──► ... ──► STORY-011 ────────► SYNC
                                          ↑ ↑
                                 main ────┘ └──── develop
```

Both branches now point at the same commit. Future feature branches cut from `develop` will automatically carry `m1..m5` along.

---

## 4. Why merge, not rebase

The obvious-looking alternative — "just rebase `develop` onto `main`" — is tempting because rebase produces a linear history. Here is why it is the wrong tool for this specific job.

### 4.1. Rebase rewrites commit hashes

Rebasing replays each commit on top of a new base, producing NEW commits with new SHAs. Every commit on `develop` between the old base and the tip gets a fresh hash.

`develop` is a **shared, already-pushed** branch. Other worktrees, other teammates, open feature branches, PR URLs, Coolify deploy records, and cron jobs all reference the OLD hashes. Rewriting them means:

- Everyone has to force-pull or their local `develop` diverges.
- Open feature branches based on old `develop` commits have to be rebased too, or they carry the stale commits back in when they merge.
- Any commit referenced in a PR description, a Slack link, or an issue now points at a ghost.

A merge commit, by contrast, adds ONE new commit and leaves every existing hash untouched.

### 4.2. Rebase on a shared branch requires a force push

To publish a rebased `develop`, you need `git push --force` (or `--force-with-lease`). Our `standards/git-workflow.md` explicitly warns against force-pushing shared branches, and for good reason: it is irreversible if someone else pushed in the meantime.

### 4.3. You lose the "where did main's commits come in" marker

A merge commit is a permanent record: "at this point, we brought main's state into develop." It shows up in `git log --graph`, in GitHub's network view, and in `git blame` follow-through. Release auditing ("when did hotfix X reach develop?") becomes a simple `git log --merges` query.

A rebase flattens `m1..m5` into develop's linear history as if they had always been there. The provenance is lost.

### 4.4. Gitflow expects merge commits at release boundaries

Our workflow (per `standards/git-workflow.md`) explicitly says release PRs merge with "squash or merge commit — not rebase, to preserve history". The sync PR follows the same principle. Merges are how we cross branch boundaries; rebase is for tidying up a PRIVATE feature branch before it becomes public.

### 4.5. The one case where rebase would "work" is worse

You COULD rebase `develop` onto `main`, force-push `develop`, and the release PR would fast-forward. The cost: everything in 4.1–4.4. The benefit: slightly prettier `git log --oneline`. Not a trade we make.

### TL;DR table

| Concern | Merge (sync branch) | Rebase develop onto main |
|---|---|---|
| Rewrites existing commit hashes | No | Yes |
| Requires force push to shared branch | No | Yes |
| Preserves "main's commits came in here" record | Yes (merge commit) | No (flattened) |
| Other open feature branches keep working | Yes | No, must rebase too |
| Matches our Gitflow conventions | Yes | No |
| Reversible if something goes wrong | Yes (revert the merge) | Hard (reflog surgery) |

---

## 5. Practical conflict-resolution tip

When the conflict is on files that `develop` has already corrected and expanded (e.g. `CLAUDE.md` with stricter rules, `docker-compose.yml` with fuller service wiring), resolve by keeping `develop`'s version verbatim:

```bash
git checkout --ours CLAUDE.md docker-compose.yml
git add CLAUDE.md docker-compose.yml
```

`--ours` during a merge = the side you are merging INTO = `develop` (your current HEAD on the sync branch). Verify afterwards that nothing from `main`'s version needs to come in — usually `develop` is already a strict superset, so this is information-preserving.

Commit the merge, push, open the PR, and link to the diagnosis in the PR description so the reviewer can confirm the "keep develop" call.

---

## 6. Quick checklist

- [ ] Release PR shows "CONFLICTING"? Confirm via `git fetch origin && git log origin/main ^origin/develop` — if that lists commits, `main` has work develop has never seen.
- [ ] Create `chore/sync-main-into-develop` from `origin/develop`.
- [ ] `git merge origin/main`, resolve conflicts (often `--ours`).
- [ ] Push, open PR into `develop`. Do NOT self-merge; human review.
- [ ] Once merged, the release PR becomes a fast-forward automatically. No more action needed on it beyond the usual review.
- [ ] After release PR lands, tag `vX.Y.0` on `main`.

Never reach for rebase to "fix" this. The linear history is not worth what it costs on a shared branch.
