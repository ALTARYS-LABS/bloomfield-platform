---
description: Implement a single Bloomfield story (vertical slice)
argument-hint: <story-id>
model: opus
---

Act as the DEVELOPER personality defined in `.claude/personalities/DEVELOPER.md`. Read that file first — it contains the workflow, hard blockers, and code rules you must follow.

The story to implement is: **$ARGUMENTS** (e.g. `STORY-004`).

## Steps

1. **Worktree (MANDATORY).** If you are not already inside a worktree for this story, run `/start-session <type>/<story-id-lower>-<slug>` where `<story-id-lower>` is `$ARGUMENTS` lowercased (e.g. `story-003`) and `<slug>` is derived from the story title. Example: `/start-session feat/story-003-market-data-provider`. All subsequent work happens inside `.claude/worktrees/<story-id-lower>-<slug>/` using absolute paths.
2. Read the story at `stories/$ARGUMENTS*.md` as your PRIMARY input. It contains scope, acceptance criteria, and UI states for one vertical slice.
3. Read `CLAUDE.md` and any standards files the story references.
4. Enter **Plan Mode** — outline the implementation approach and the slice boundary. Do not write code yet.
5. **Get plan approval.**
6. Implement end-to-end (API + logic + persistence + UI if applicable).
7. Write tests (unit + integration as appropriate for the slice).
8. Run the hard-blocker checks:
   - `./gradlew test` — all green
   - `./gradlew spotlessApply`
   - `docker compose up -d && ./gradlew bootRun` — app starts
   - For UI stories: run the frontend dev server and verify the golden path in a browser
9. Run `/simplify` to clean up.
10. **Return to Plan Mode** — summarize changes, list files, present a checklist, wait for human approval.
11. Once approved, run `/commit-push-pr`. The PR targets `develop` (per `standards/git-workflow.md`).

If the story references information that isn't present or conflicts with the code, **STOP and flag it as a decomposition defect** — do not guess.
