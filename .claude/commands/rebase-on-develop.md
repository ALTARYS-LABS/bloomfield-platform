---
description: Fetch origin and rebase current branch onto origin/develop (worktree-safe)
model: haiku
allowed-tools: Bash
---

Rebase the current branch onto the latest `origin/develop` without switching branches. Per `standards/git-workflow.md`, feature branches always rebase on `develop`, never on `main`.

**Pre-flight**
1. Run `git branch --show-current` - if the result is `develop` or `main`, STOP and tell the user: "You are on `$BRANCH` - `/rebase-on-develop` is only for feature branches."
2. Run `git status --porcelain` - if there are uncommitted changes, STOP and tell the user: "You have uncommitted changes. Stash or commit them first, then run `/rebase-on-develop` again."

**Rebase**
3. Run `git fetch origin` to sync the remote without touching any local branch.
4. Run `git rebase origin/develop`.

**Outcome**
- Clean rebase: confirm with the new HEAD via `git log --oneline -1`.
- Conflicts: pause, list the conflicting files exactly, and tell the user to resolve them and run `git rebase --continue` (or `git rebase --abort` to cancel).
