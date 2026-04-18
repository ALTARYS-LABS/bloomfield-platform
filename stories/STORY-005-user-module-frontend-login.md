# STORY-005 — User Module (Frontend Login & Protected Routes)

**Type**: feat
**Status**: todo
**Branch**: `feat/user-module-frontend-login`
**Depends on**: STORY-004
**Estimated PR size**: ~300 lines

---

## Context

STORY-004 delivers the backend auth endpoints. This story integrates them into the React frontend: login UI, token storage, authenticated API client, protected routes, and a minimal admin page.

---

## What Needs to Be Done

### Step 1 — Auth context + hook
`frontend/src/auth/`:
- `AuthContext.tsx` — React context: `user`, `accessToken`, `login()`, `logout()`, `isLoading`
- `useAuth.ts` — hook
- `authApi.ts` — fetch wrappers for `/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/me`
- Token storage: **access token in memory only** (state), **refresh token in `httpOnly` cookie** set by the backend (requires STORY-004 to set the cookie on `/auth/login`/`/auth/refresh`) — coordinate with STORY-004 implementation

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
Pass the access token on the STOMP `CONNECT` frame (header `Authorization: Bearer ...`). Backend WS auth handling arrives with STORY-007 — for now, the frontend sends the header and the backend ignores it gracefully.

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

- `frontend/src/auth/**` (new)
- `frontend/src/api/client.ts` (new)
- `frontend/src/pages/{LoginPage,RegisterPage,AdminUsersPage}.tsx` (new)
- `frontend/src/routes/ProtectedRoute.tsx` (new)
- `frontend/src/App.tsx` (router update)
- `frontend/src/hooks/useWebSocket.ts` (pass token on CONNECT)
- `frontend/package.json` (Vitest / RTL if not present)
