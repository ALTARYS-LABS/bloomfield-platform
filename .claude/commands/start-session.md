---
description: Create a worktree and branch for an isolated session
model: haiku
allowed-tools: Bash, Read, Write
---
Create an isolated worktree for this session. The user provides a branch name as `$ARGUMENTS` (e.g. `feat/control-plane-CP-003`, `fix/login-redirect`, `docs/absence-arch`, `chore/ci-cleanup`).

**Steps:**
1. Validate the branch name matches the pattern `<type>/<slug>` where type is one of `feat|fix|chore|docs|refactor|test|style` and slug is lowercase letters, digits, and hyphens only. If invalid, ask the user for a valid name.
2. Run `git fetch origin develop` to ensure develop is up to date.
3. Create the worktree:
   ```
   git worktree add .claude/worktrees/<slug> -b <type>/<slug> origin/develop
   ```
   Where `<slug>` is the part after the `/` in the branch name.
4. Confirm to the user:
   - Worktree path: `.claude/worktrees/<slug>`
   - Branch: `<type>/<slug>`
   - Based on: `origin/develop`
5. Change working directory to the worktree: `cd .claude/worktrees/<slug>`
6. Tell the user: "Session ready. All work will happen in this worktree. Use `/commit-push-pr` when done."
