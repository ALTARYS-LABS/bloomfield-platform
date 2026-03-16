# ALTARYS LABS — Claude code Development Context

## Company & Products
**Company**: ALTARYS LABS (the company you are working for)


## A the begining
## Tech Stack
- Backend: Java 25 / Spring Boot 4.0.3 , gradle
- Frontend: React 19.2 / TypeScript
- Infrastructure: Docker + Coolify

## Critical Rules (NEVER violate)
1. Every database query MUST include tenant isolation context
2. Financial calculations: BigDecimal ONLY — never double/float
3. Use `@ConfigurationProperties` records for externalized configuration — never hardcode values that belong in `application.yml`
4. Only use `public` when the field, method, class, or record is accessed from outside its package. Default to package-private.
5. Prefer records over classes for controllers, any stateless/immutable Spring beans (Java 25 best practice). Only use classes when mutable state is required or when Spring can use CGLIB to create runtime subclasses .
6. Always run tests before committing: `./gradlew test` must be green. Never disable or skip failing tests.
7. Always format before committing: ./gradlew spotlessApply
8. The app MUST start locally: docker-compose.yml must exist with all external dependencies (PostgreSQL, Keycloak, etc.). Verify with `docker compose up -d && ./gradlew bootRun`.  
9. **Worktree isolation**: Every Claude Code session (personality or ad-hoc) MUST work in a dedicated worktree — never on the main checkout. 
    **The very first action of every session is to run `/start-session <type>/<slug>` before touching any file or running any command.**  
    If Claude has already made changes without a worktree, it MUST stop, warn the user, and offer to create the worktree + move the work there before continuing.  
    If Claude detects it is working on `main` or on a branch not matching the current task, it MUST warn the user and offer to create a worktree before proceeding. Worktrees live in `.claude/worktrees/`. 
    **Branch naming**: `<type>/<slug>` where type is one of `feat|fix|chore|docs|refactor|test|style` and slug is lowercase letters, digits, underscore, and hyphens only. Slug must be meaningful


## Common Mistakes to Avoid

1. When git-adding or git-commit files, ONLY add or commit the files you created or changed


[Update this list after every mistake — LIVING DOCUMENT]
