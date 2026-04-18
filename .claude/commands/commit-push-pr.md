---
description: Commit changes, push, and create a PR
model: haiku
---
Run the following steps:

**Pre-flight: Worktree check**
1. Run `git rev-parse --show-toplevel` and `git branch --show-current`
2. If you are on `main` or `develop`, or on the main checkout (not inside `.claude/worktrees/`), STOP and warn the user: "You are on main or develop — run `/start-session <type>/<slug>` first to create a worktree."
3. Only proceed if you are on a feature/fix/chore/docs/refactor/test/style branch inside a worktree.

**Commit flow**
4. Run `git status` and `git diff --stat` to understand changes
5. Run the test suite: `./gradlew test` and `cd frontend && pnpm test`
6. Run linting: `./gradlew spotlessCheck` and `cd frontend && pnpm run lint`
7. If tests and lint pass, create a meaningful conventional commit message
8. Push to the worktree's branch (use `git push -u origin <branch>` if first push)
9. Create a PR targeting `develop` with a clear description linking to the relevant PRD/story
