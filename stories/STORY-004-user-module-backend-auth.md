# STORY-004 — User Module (Backend Auth with JWT)

**Type**: feat
**Status**: todo
**Branch**: `feat/user-module-backend-auth`
**Depends on**: STORY-002
**Estimated PR size**: ~500 lines (may split into auth-only + roles/admin if review friction)

---

## Context

v2 requires user authentication with three roles (ADMIN / ANALYST / VIEWER), JWT-based auth, and RBAC enforced via Spring Security annotations. v1 has no auth at all. This story delivers the **backend** pieces only; frontend login is STORY-005.

The application is single-tenant (one company), so no `tenant_id` anywhere.

---

## What Needs to Be Done

### Step 1 — Dependencies
Add to `backend/build.gradle.kts`:
- `spring-boot-starter-security`
- `spring-boot-starter-oauth2-resource-server` (for JWT decoder) **or** manually configure with `jjwt-*` libraries
- Recommendation: use Spring Security's `oauth2-resource-server` with HMAC (shared secret) for simplicity in v2 — no external Keycloak needed

### Step 2 — New module `user`
```
com.bloomfield.terminal.user
├── api/              # UserId value object, public events (e.g. UserRegistered)
├── web/              # AuthController, UserController
├── domain/           # User, Role, RefreshToken records/classes
├── internal/         # UserRepository (Spring Data JDBC), password encoding, JWT issuer
└── package-info.java # @ApplicationModule
```

### Step 3 — Flyway migration
`V002__user_module.sql`:
```sql
CREATE TABLE users (
  id           UUID PRIMARY KEY,
  email        VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  full_name    VARCHAR(255) NOT NULL,
  enabled      BOOLEAN NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE roles (
  id    SMALLINT PRIMARY KEY,
  name  VARCHAR(32) NOT NULL UNIQUE
);

CREATE TABLE user_roles (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_id SMALLINT NOT NULL REFERENCES roles(id),
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
  id         UUID PRIMARY KEY,
  user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(255) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ
);

INSERT INTO roles (id, name) VALUES (1, 'ADMIN'), (2, 'ANALYST'), (3, 'VIEWER');
```

### Step 4 — Security configuration
- `SecurityFilterChain` bean: stateless, JWT bearer auth, CORS already handled by existing `CorsConfig`
- Whitelist: `/api/health`, `/auth/login`, `/auth/register`, `/auth/refresh`, `/ws/**` (temporarily — revisit WS auth)
- Everything else requires authentication
- BCrypt password encoder

### Step 5 — Auth endpoints
- `POST /auth/register` — body `{email, password, fullName}`, default role VIEWER, returns 201
- `POST /auth/login` — body `{email, password}`, returns `{accessToken, refreshToken, expiresIn}`
- `POST /auth/refresh` — body `{refreshToken}`, rotates refresh token, returns new pair
- `POST /auth/logout` — revokes the current refresh token
- `GET /auth/me` — returns current user profile (requires auth)

Access token TTL: 15 min. Refresh token TTL: 7 days. Secret read from `app.jwt.secret` via `@ConfigurationProperties` record.

### Step 6 — Admin endpoints (role ADMIN only)
- `GET /admin/users` — list
- `PATCH /admin/users/{id}/roles` — update roles
- `PATCH /admin/users/{id}/enabled` — enable/disable

Enforced via `@PreAuthorize("hasRole('ADMIN')")`.

### Step 7 — Tests (TestContainers)
- `UserRepositoryTest` — CRUD, unique email, role assignment
- `AuthControllerIT` — register → login → use access token → refresh → reuse old refresh token must fail
- `AdminControllerIT` — VIEWER gets 403, ADMIN gets 200
- `ApplicationModulesTest` — re-run `verify()`, asserts `user` module has no illegal dependencies

---

## Acceptance Criteria

- [ ] `POST /auth/register` followed by `POST /auth/login` returns a valid JWT
- [ ] Accessing a protected endpoint without Bearer token returns 401
- [ ] VIEWER on an ADMIN-only endpoint returns 403
- [ ] Refresh token rotation: old refresh token invalid after use
- [ ] Passwords stored BCrypt (never plain text); test verifies this
- [ ] Flyway migration applies clean on an empty DB
- [ ] `./gradlew test` green

---

## Out of Scope

- Email verification / password reset (post-v2)
- Account lockout / rate limiting (post-v2)
- Frontend login flow (STORY-005)
- WebSocket authentication (STORY-007 tightens this when alerts need per-user subscriptions)

---

## Related Files

- `backend/src/main/java/com/bloomfield/terminal/user/**` (new)
- `backend/src/main/resources/db/migration/V002__user_module.sql` (new)
- `backend/src/test/java/com/bloomfield/terminal/user/**` (new)
- `backend/src/main/resources/application.yml` (`app.jwt.*` properties)
- `backend/build.gradle.kts` (new dependencies)
