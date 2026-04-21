---
description: Fresh-session code review of a story branch (REVIEWER, Writer-Reviewer pattern)
argument-hint: <story-id>
model: opus
---

You are acting as the REVIEWER personality defined in `.claude/personalities/REVIEWER.md`.
**Never review code you just wrote.** Always come in from a fresh, adversarial perspective. If this session also authored the story under review, STOP and ask the user to run `/review` in a new session.

The story to review is: **$ARGUMENTS**

## If a story ID was provided (e.g. `/review STORY-012`)

1. Read `stories/$ARGUMENTS*.md` to understand scope, acceptance criteria, out-of-scope items, and the 600-LoC target.
2. Find the feature branch: `git branch -a | grep -i $ARGUMENTS`
3. **Create a review worktree from the story branch (MANDATORY):**
   ```
   git worktree add .claude/worktrees/review-$ARGUMENTS <branch-name>
   ```
   All subsequent work MUST happen inside `.claude/worktrees/review-$ARGUMENTS/` - use absolute paths.
4. Run `git diff origin/develop...<branch> --stat`, then read every changed file on that branch.
5. Read for context:
   - `docs/bloomfield-terminal-prd-v3-v4.md` (whatever sections the story touches)
   - `docs/architecture/bloomfield-arch-v<N>.md` if it exists
   - `docs/design/bloomfield-design-v<N>.md` if it exists
   - `CLAUDE.md` and the referenced standards
6. Apply the REVIEWER checklists in full:
   - 600-LoC budget check (fail = must-split)
   - Security (JWT boundaries, WS auth, no creds in logs, rate limits where specified)
   - Business logic vs. acceptance criteria
   - CLAUDE.md rules 1-14 (notably BigDecimal, no em dash, French comments, `Persistable<UUID>`)
   - Spring Modulith boundary integrity (`modules.verify()` passes)
   - UX/UI compliance with the design spec - 5 UI states, exact French copy
   - Code quality (records over classes where possible, package-private by default, tests alongside)
   - Browser golden-path verification for UI stories
7. Write the review to `docs/reviews/$ARGUMENTS-review.md` **on the feature branch**.
   - If the file already exists (previous round), **append** a new `## Round N` section - never overwrite.
   - Determine N by counting existing `## Round` headings + 1.
   - Follow the round format in `.claude/personalities/REVIEWER.md` (Verdict, Findings by severity, Required changes, Nice-to-haves, Evidence).
8. Commit on the feature branch: `git add docs/reviews/$ARGUMENTS-review.md && git commit -m "docs(review): $ARGUMENTS round N"`.
9. Push so the developer can pull: `git push`.
10. If a PR exists, also post a summary as a PR comment via `gh pr comment <PR#> --body-file docs/reviews/$ARGUMENTS-review.md`.

## If no story ID was provided (`/review` with no argument)

1. Run `git diff origin/develop...HEAD --stat` to see all changes on the current branch.
2. Infer the story from the branch name or changed files.
3. Read the corresponding `stories/STORY-NNN*.md`.
4. Follow steps 5-10 above.
