# bin/ - Personality launchers

Thin shell wrappers that start a fresh Claude Code session preloaded with a personality system prompt from `.claude/personalities/`.

## Usage

From the repo root (these scripts resolve `.claude/personalities/*.md` relative to the current working directory):

```bash
bin/analyst      # requirements analysis (Mode A) / story decomposition (Mode B)
bin/architect    # technical design, proposes vertical-slice candidates
bin/designer     # terminal-grade UX/UI spec
bin/developer    # story implementation, PR <= 600 LoC
bin/reviewer     # fresh-session code review
```

Add `./bin` to your `PATH` (per-project, e.g. via `direnv`) if you want to drop the `bin/` prefix.

## When to use a launcher vs. a slash command

- **Launcher (`bin/<role>`)** - starts a fresh Claude session with the personality loaded as a system prompt. Use this when you want the role to persist for the whole session (multiple turns, multiple files).
- **Slash command (`/analyze`, `/architect`, `/design`, `/decompose`, `/develop`, `/review`, ...)** - one-shot invocation inside an existing session, with the worktree setup and document references wired in. Use this when you know exactly which artifact you want produced.

The Writer-Reviewer pattern in `docs/BLOOMFIELD_AI_Development_Workflow.md` assumes you `bin/reviewer` in a **separate terminal / new session** from the one that wrote the code. Never review code you just wrote in the same session.

## First-time setup

```bash
chmod +x bin/*
```

(Git preserves the executable bit on commit.)
