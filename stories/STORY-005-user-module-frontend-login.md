# STORY-005 — User Module (Frontend Login & Protected Routes)

**Type**: feat
**Status**: todo
**Branch**: `feat/user-module-frontend-login`
**Depends on**: STORY-004
**Estimated PR size**: ~300 lines

---

## Context

STORY-004 delivers the backend auth endpoints. This story integrates them into the React frontend: login UI, token storage, authenticated API client, protected routes, and a minimal admin page.

**Decomposition note (carry-over from STORY-004)**: STORY-004 as shipped returns the refresh token in the JSON body and accepts it in request bodies. STORY-005 requires the refresh token to live in an `httpOnly` cookie (see Step 0). This story therefore also modifies `AuthController` + `SecurityConfig` to set/read/clear the cookie. The scope expansion is intentional — see `_kb_/web-auth-security-tutorial.md` for the security rationale.

---

## What Needs to Be Done

### Step 0 — Backend cookie contract (carry-over)
Modify `backend/src/main/java/com/bloomfield/terminal/user/api/AuthController.java` and `SecurityConfig`:
- `POST /auth/login` — set a `Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Strict; Path=/auth; Max-Age=<7d>` header. Remove `refreshToken` from the JSON body; response is `{accessToken, expiresIn}`.
- `POST /auth/refresh` — read refresh token via `@CookieValue("refresh_token")` (remove `RefreshRequest` body), rotate, set new cookie.
- `POST /auth/logout` — read cookie, revoke in DB, send a clearing `Set-Cookie` (Max-Age=0).
- `SecurityConfig` — add a minimal STOMP `ChannelInterceptor` that reads the `Authorization: Bearer …` header on `CONNECT` frames and sets the authenticated `Principal` for `/user/*` destinations (unblocks STORY-006 and STORY-007). If header missing/invalid, connection proceeds anonymously (market data remains public).
- `CorsConfig` — verify `allowCredentials=true` is honored with exact-origin allowlist for prod profile (no wildcard). Add a test.
- Update `AuthControllerIT` and `AdminControllerIT` to exercise the cookie flow.

### Step 1 — Auth context + hook
`frontend/src/auth/`:
- `AuthContext.tsx` — React context: `user`, `accessToken`, `login()`, `logout()`, `isLoading`
- `useAuth.ts` — hook
- `authApi.ts` — fetch wrappers for `/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/me`, all with `credentials: 'include'` for cookie-bearing endpoints (`/auth/login`, `/auth/refresh`, `/auth/logout`)
- Token storage: **access token in memory only** (React state). Refresh token is in an `httpOnly` cookie set by the backend (Step 0) — unreadable from JS, auto-attached by the browser.

### Step 2 — HTTP client with auto-refresh
`frontend/src/api/client.ts` — thin wrapper around `fetch` that:
- Attaches `Authorization: Bearer <accessToken>`
- On 401, calls `/auth/refresh` once, retries the original request, and if that also 401s, triggers logout

### Step 3 — Pages
- `LoginPage.tsx` — email + password form, error display, redirects to `/terminal` on success
- `RegisterPage.tsx` — optional for v2 demo; minimal form, same redirect
- `AdminUsersPage.tsx` — list users, toggle enabled, edit roles (ADMIN only)

### Step 4 — Route guards
`frontend/src/routes/ProtectedRoute.tsx` — wrapper that:
- Redirects to `/login` if not authenticated
- Optional `requiredRole` prop for ADMIN pages → redirects to `/terminal` with a toast if role missing

Update `App.tsx` router:
- `/` → LandingPage (public)
- `/login` → LoginPage (public)
- `/register` → RegisterPage (public)
- `/terminal` → Terminal (protected, any role)
- `/admin/users` → AdminUsersPage (protected, ADMIN)

### Step 5 — WebSocket authentication
Pass the access token on the STOMP `CONNECT` frame (header `Authorization: Bearer ...`). The backend `ChannelInterceptor` added in Step 0 authenticates the CONNECT and associates the session with the JWT subject — this unblocks `/user/queue/*` destinations used by STORY-006 (Portfolio) and STORY-007 (Alerts).

### Step 6 — Tests
React testing approach for this repo is TBD (Vitest + React Testing Library recommended). If not yet set up, include minimal Vitest config in this PR. Minimum tests:
- `LoginPage` — renders, submits, handles 401
- `ProtectedRoute` — redirects when unauthenticated
- `authApi` — refresh-on-401 flow

---

## Acceptance Criteria

- [ ] Visiting `/terminal` when logged out redirects to `/login`
- [ ] Successful login lands on `/terminal` and the market data stream connects
- [ ] Refresh flow works: let access token expire (or force via short TTL in dev), next API call succeeds transparently
- [ ] VIEWER user cannot open `/admin/users` — redirected back with a visible notice
- [ ] Logout clears access token, revokes refresh token on backend, redirects to `/login`
- [ ] `pnpm lint` and `pnpm build` green

---

## Out of Scope

- Password reset / email verification UI
- "Remember me" option
- Social login
- Fine-grained per-widget permissions

---

## Related Files

- `backend/src/main/java/com/bloomfield/terminal/user/api/AuthController.java` (cookie set/read/clear)
- `backend/src/main/java/com/bloomfield/terminal/user/internal/SecurityConfig.java` (STOMP ChannelInterceptor)
- `backend/src/main/java/com/bloomfield/terminal/user/api/dto/TokenResponse.java` (drop `refreshToken` field)
- `backend/src/main/java/com/bloomfield/terminal/user/api/dto/RefreshRequest.java` / `LogoutRequest.java` (remove; cookie-driven now)
- `backend/src/test/java/com/bloomfield/terminal/user/AuthControllerIT.java` (update assertions)
- `frontend/src/auth/**` (new)
- `frontend/src/api/client.ts` (new)
- `frontend/src/pages/{LoginPage,RegisterPage,AdminUsersPage}.tsx` (new)
- `frontend/src/routes/ProtectedRoute.tsx` (new)
- `frontend/src/App.tsx` (router update)
- `frontend/src/hooks/useWebSocket.ts` (pass token on CONNECT)
- `frontend/package.json` (Vitest / RTL if not present)

---

## Estimated PR size (revised)

~450–500 lines (backend cookie + WS auth ~150, frontend ~300). Acceptable per git-workflow standards given the scope expansion is necessary to deliver a secure auth flow. Original `~300` estimate did not account for the STORY-004 cookie work.
