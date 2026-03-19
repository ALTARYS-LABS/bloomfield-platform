# ALTARYS LABS — Claude code Development Context

## Company & Products
**Company**: ALTARYS LABS (the company you are working for)

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
9. **Git workflow**: @standards/git-workflow.md — governs worktree isolation, branch naming, Gitflow model, and PR rules. Loaded automatically by Claude Code.


## Common Mistakes to Avoid

1. When git-adding or git-commit files, ONLY add or commit the files you created or changed


[Update this list after every mistake — LIVING DOCUMENT]
