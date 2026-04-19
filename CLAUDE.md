# ALTARYS LABS - Claude code Development Context

## Company & Products
**Company**: ALTARYS LABS (the company you are working for)

## Tech Stack
- Backend: Java 25 / Spring Boot 4.0.3 , gradle
- Frontend: React 19.2 / TypeScript
- Infrastructure: Docker + Coolify

## Critical Rules (NEVER violate)
1. Data access goes through Spring Data JDBC repositories - never raw `JdbcTemplate` outside `internal/` packages. No JPA.
2. Financial calculations: BigDecimal ONLY - never double/float
3. Use `@ConfigurationProperties` records for externalized configuration - never hardcode values that belong in `application.yml`
4. Only use `public` when the field, method, class, or record is accessed from outside its package. Default to package-private.
5. Prefer records over classes for controllers and stateless/immutable Spring beans (Java 25 best practice). A bean MUST stay a class when Spring needs a CGLIB proxy around it - this includes any bean carrying `@Transactional`, `@Scheduled`, `@Async`, `@TransactionalEventListener`, `@ApplicationModuleListener`, `@Cacheable`, or `@Retryable`. See `_kb_/spring-modulith-transactional-event-listeners.md` for the `@ApplicationModuleListener` case specifically (do not stack `@Transactional` on top of it - Spring 7 rejects the combination).
6. Always run tests before committing: `./gradlew test` must be green, and on the frontend both `pnpm lint` AND `pnpm build` must pass (`tsc -b` inside `pnpm build` catches unreferenced / broken TS files that `pnpm test` does not import). Never disable or skip failing tests.
7. Always format before committing: ./gradlew spotlessApply
8. The app MUST start locally: docker-compose.yml must exist with all external dependencies (PostgreSQL, Keycloak, etc.). Verify with `docker compose up -d && ./gradlew bootRun`.
9. **Git workflow**: @standards/git-workflow.md - governs worktree isolation, branch naming, Gitflow model, and PR rules. Loaded automatically by Claude Code.
10. **Never use em dash (U+2014) in any file** - not in code strings, comments, JSX, documentation, or CLAUDE.md itself. Always use a plain hyphen `-` instead.
11. **All source code comments must be written in French** - applies to every new or modified comment in backend (`.java`), frontend (`.ts`, `.tsx`, `.js`, `.jsx`, `.css`), config (`.yml`, `.yaml`, `.properties`, `.sql`, Dockerfile, shell), and Javadoc/JSDoc blocks. Identifiers (class, method, variable, package names), commit messages, PR descriptions, documentation under `stories/`, `standards/`, `_kb_/`, and this `CLAUDE.md` remain in English. When editing an existing English comment, translate it to French as part of the change; do not retro-translate untouched comments.
12. **CI must actually run and pass on the PR branch before review.** A PR is not reviewable on the strength of local tests alone. If CI did not trigger (no check runs appear on the PR), stop and fix the workflow `on:` triggers before asking for review - do not re-narrow triggers to `main` / `feature/**` only. `push` and `pull_request` must cover `[main, develop]` at minimum.
13. **Client-generated UUID entities must implement `Persistable<UUID>`.** Our default is to assign UUIDs in Java before `save()`. A plain record with `@Id UUID id` makes Spring Data JDBC see a non-null id and issue `UPDATE ... WHERE id = ?`, which affects 0 rows and silently drops the insert. Implement `Persistable<UUID>` with a `@Transient boolean isNew` flag and expose a `new<Entity>(...)` static factory that sets `isNew = true` for creation. `Portfolio` is the canonical example.
14. **Reserve the next Flyway `Vxxx__` number in your PR description.** When you open a migration-bearing branch, call out the version you claimed (e.g. "uses V005"). If another migration merges first, bump yours before merging. Two branches both claiming the same `Vxxx__` is a guaranteed deploy failure.


## Common Mistakes to Avoid

1. When git-adding or git-commit files, ONLY add or commit the files you created or changed


[Update this list after every mistake - LIVING DOCUMENT]
