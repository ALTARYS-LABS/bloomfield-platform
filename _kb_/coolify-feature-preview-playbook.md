# Coolify Feature Preview Playbook

> How to spin up a throwaway Coolify app that runs **one specific feature branch**
> so it can be tested end-to-end before merging to `develop`.

This complements `_kb_/multi-environment-setup.md`, which documents the permanent
`staging` (develop) and `production` (main) environments. A **feature preview** is
a third, short-lived environment attached to an arbitrary branch. It gets its own
subdomain, its own database, its own container set, and is torn down the moment
the PR is merged or abandoned.

---

## When to use a preview app (and when not)

Use a preview app when:

- A feature branch contains a **Flyway migration** you want to rehearse against a
  Postgres image that mirrors staging (TimescaleDB, extensions, etc.) before it
  lands on `develop`.
- A non-technical stakeholder needs to click through a branch.
- Two unmerged branches need to be compared side-by-side on real infra.
- The change is risky enough that you do not want `develop` to be the first
  public surface it touches.

Do **not** use it for every PR - it consumes VPS resources and has real DNS and
TLS footprint. The default remains: merge short-lived branches to `develop`, let
staging do the integration test.

---

## Architecture recap

```
VPS (single host)
│
├── Coolify project "Bloomfield Terminal"
│   ├── Environment: production  → branch main      → bloomfield-intelligence.altaryslabs.com
│   ├── Environment: staging     → branch develop   → staging-bf-terminal.altaryslabs.com
│   └── Environment: preview     → branch feat/xyz  → preview-xyz.altaryslabs.com   ← this doc
│       ├── Resource: "preview-story-008"           (one Compose app per branch)
│       ├── Resource: "preview-alerts-refactor"
│       └── ...
```

Each preview resource is a full copy of the Compose stack (backend + frontend +
its own Postgres). It is **not** shared with staging or production.

---

## Host port allocation

The VPS only has one set of host ports. Each Coolify resource that publishes a
port to the host must pick a unique one. Reserve a block for previews and keep
a running ledger:

| Environment   | `FRONTEND_PORT` | `POSTGRES_PORT` | Notes                             |
|---------------|-----------------|-----------------|-----------------------------------|
| production    | `3000`          | unpublished     | default compose                   |
| staging       | `3001`          | unpublished     | set via env                       |
| preview slot 1| `3010`          | `55432`         | story-008 etc.                    |
| preview slot 2| `3011`          | `55433`         |                                   |
| preview slot 3| `3012`          | `55434`         |                                   |

Traefik routes HTTP by `Host` header, so the frontend port is only relevant for
container-to-host mapping - clients hit the URL, Traefik picks the right
container. But **host port collisions will fail the deploy silently** (container
restarts in a loop). Always pick a free slot and write it down in the Coolify
resource description.

---

## Prerequisites

Before starting, confirm the following. Each is a separate source of a failed
preview deploy:

1. **You know the branch name** you want to preview. Example:
   `feat/story-042-trade-blotter`. Derive a short **slug** from it for DNS and
   resource naming: `story-042` or `trade-blotter` - lowercase, hyphens, under
   20 characters.
2. **The branch builds locally**: `./gradlew build` and `pnpm build` both pass
   in the worktree for that branch. A preview is not a fix-it-in-CI loop - it
   is for testing a branch that already compiles.
3. **The migration number is unique**. If the branch adds `V0NN__*.sql`, check
   that `origin/develop` has not merged another migration with the same number
   since the branch was cut. Bump if needed (CLAUDE.md rule #14).
4. **Cloudflare DNS credentials** are available (or someone who has them is on
   standby - you cannot deploy TLS without DNS).
5. **Coolify admin access** to the Bloomfield Terminal project.

---

## Step 1 - Cloudflare DNS

Each preview needs a subdomain. Convention: `preview-<slug>.altaryslabs.com`.

1. Open Cloudflare → `altaryslabs.com` zone → **DNS → Records**.
2. Click **Add record**.
3. Fill in:
   - **Type**: `A`
   - **Name**: `preview-<slug>` (e.g. `preview-story-042`)
   - **IPv4 address**: the VPS IP (same one staging and production use)
   - **Proxy status**: **DNS only** (grey cloud - required for Let's Encrypt
     HTTP-01 challenge; see `_kb_/multi-environment-setup.md` §3.2)
   - **TTL**: Auto
4. Click **Save**.
5. Verify from your laptop before moving on:
   ```bash
   dig +short preview-<slug>.altaryslabs.com
   ```
   It should return the VPS IP within 30 seconds. If it does not, fix DNS before
   touching Coolify - Traefik will otherwise fail its certificate challenge and
   you will waste time debugging in the wrong layer.

---

## Step 2 - Coolify: ensure the `preview` environment exists

The `preview` environment is a reusable container for all ephemeral preview
resources. Create it once; reuse it for every future preview.

1. Open Coolify → project **Bloomfield Terminal**.
2. Look for an environment called `preview` in the left column.
3. If it does not exist:
   - Click **+ New Environment** (top-right of the project view).
   - **Name**: `preview`
   - **Description**: `Feature branch previews. Resources here are throwaway.`
   - Click **Save**.
4. You should now see three environments: `production`, `staging`, `preview`.

---

## Step 3 - Create the preview resource

Inside the `preview` environment, each branch gets its own resource.

### 3.1 Open the new-resource flow

1. Click into the `preview` environment.
2. Click **+ New Resource** (top-right).
3. Choose **Public Repository** (or **Private Repository** depending on how
   staging is wired - match what production and staging use).

### 3.2 Source

1. **Repository URL**: `https://github.com/ALTARYS-LABS/bloomfield-platform`
   (or the SSH URL if Coolify uses its deploy key).
2. **Branch**: the exact feature branch, e.g. `feat/story-042-trade-blotter`.
3. **Build pack**: **Docker Compose**.
4. **Docker Compose file location**: `/docker-compose.yml`.
5. Click **Continue** / **Save**.

### 3.3 Name and description

1. **Resource name**: `preview-<slug>` (e.g. `preview-story-042`). This name
   appears in the Coolify dashboard and in container names - keep it unique.
2. **Description**: write down the slot you claimed in the port ledger and the
   PR number it tracks. Example:
   ```
   PR #47 - story-042 trade blotter.
   Slot: FRONTEND_PORT=3010, POSTGRES_PORT=55432.
   Delete on merge.
   ```

### 3.4 Environment variables

This is where a preview differs from staging. Open the **Environment Variables**
tab of the resource and set **all** of the following. Use "Build Variable: No"
for every one of these (they are runtime only).

| Key                          | Value                                                   | Why                                                                                              |
|------------------------------|---------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE`     | `staging`                                               | Reuse the staging profile; we override the single value that differs (CORS) below.              |
| `FRONTEND_PORT`              | the slot you claimed (e.g. `3010`)                      | Host port for the frontend container. Must be unique on the VPS.                                |
| `POSTGRES_PORT`              | the slot you claimed (e.g. `55432`)                     | Host port for Postgres. Pick something far from 5432 to avoid clashing with any host tool.      |
| `POSTGRES_DB`                | `bloomfield_preview_<slug>` (e.g. `bloomfield_preview_story_042`) | Isolated DB name. Underscores only.                                                  |
| `POSTGRES_USER`              | `bloomfield`                                            | Same as staging, no reason to differ.                                                           |
| `POSTGRES_PASSWORD`          | a fresh random string (generate with `openssl rand -hex 16`) | Do **not** reuse the staging password.                                                    |
| `APP_CORS_ALLOWEDORIGINS_0`  | `https://preview-<slug>.altaryslabs.com`                | Overrides `app.cors.allowed-origins[0]` from `application-staging.yml` so CORS accepts the preview domain. Spring Boot relaxed binding turns the env var into the list element. |
| `JWT_SECRET`                 | a fresh random string (generate with `openssl rand -hex 32`) | Must match the name used by `application.yml`. Do not reuse staging's secret.            |

> **Check your env var names against `application.yml`.** The CORS property is
> `app.cors.allowed-origins`; Spring binds `APP_CORS_ALLOWEDORIGINS_0` to the
> first list item. If the property name ever changes, this env var changes too.
> Grep the backend for `@ConfigurationProperties` to confirm before you deploy.

Click **Save** at the top of the env var panel.

### 3.5 Domain

1. Open the **Domains** tab of the resource.
2. The frontend is the service Traefik should expose. Set:
   ```
   https://preview-<slug>.altaryslabs.com
   ```
   on the `frontend` service (Coolify lets you pick the service from a
   dropdown - select `frontend`, not `backend` or `postgres`).
3. Make sure **Generate TLS Certificate** is **ON**. Traefik will issue a
   Let's Encrypt cert on first successful request; DNS must already resolve.
4. Click **Save**.

### 3.6 Build settings

1. **Build path**: leave as repo root (`/`).
2. **Port mappings**: Coolify should auto-detect from `docker-compose.yml`. Do
   not manually edit unless the auto-detected values are wrong.
3. **Healthcheck**: keep the one defined in `docker-compose.yml` for the
   backend (`/api/health`). Do not override unless you see it fail.

---

## Step 4 - Deploy

1. Click **Deploy** (top-right of the resource page).
2. Open the **Logs** tab immediately. What to watch for, in order:
   - `postgres` container starts and passes its own healthcheck.
   - Flyway applies all migrations cleanly on the empty DB. If `V0NN` fails,
     stop and read the error before doing anything else - this is precisely
     what a preview is for.
   - Backend reports `Started TerminalApplication in X seconds`.
   - Frontend nginx serves `/` with `200`.
   - Traefik emits a `certificate obtained` line for the domain.
3. First deploy takes 3–5 minutes on cold caches.

---

## Step 5 - Webhook (optional but recommended)

If you want the preview to redeploy on every push to the feature branch:

1. On the resource page → **Webhooks** tab → copy the **Deploy webhook URL**.
2. GitHub → repo **Settings → Webhooks → Add webhook**.
3. **Payload URL**: paste the Coolify URL.
4. **Content type**: `application/json`.
5. **Which events**: **Just the push event**.
6. Leave **Active** checked. Click **Add webhook**.
7. Rename the webhook in the repo settings to `coolify-preview-<slug>` so it is
   easy to find and delete later.

Coolify filters by branch server-side, so the webhook will only trigger on
pushes to the specific branch you set in §3.2.

If you skip this, you can still click **Deploy** in Coolify after each push.
For short-lived previews (a day or two), manual redeploy is usually fine.

---

## Step 6 - Verification checklist

Before handing the URL to anyone, confirm:

- [ ] `https://preview-<slug>.altaryslabs.com` loads the frontend over HTTPS.
- [ ] Padlock shows a valid Let's Encrypt cert for that exact host.
- [ ] `curl https://preview-<slug>.altaryslabs.com/api/health` returns 200.
- [ ] You can register a new account and log in (the DB is empty on a fresh
      preview - there are no seed users).
- [ ] The feature you are previewing actually works end-to-end.
- [ ] `docker ps` on the VPS shows three containers for this preview
      (`preview-<slug>-frontend-*`, `-backend-*`, `-postgres-*`) and they are
      not restarting.
- [ ] No port collision in Coolify logs (look for `bind: address already in use`).

If any box fails, the preview is not ready - do not distribute the URL.

---

## Step 7 - Teardown (mandatory on merge or abandon)

Previews are throwaway. The moment the PR is merged or closed:

1. **Coolify** → `preview` environment → the resource → **Settings → Delete
   Resource**. Confirm. This stops containers, removes the Docker volumes
   (including the preview Postgres data), and drops the Traefik route.
2. **GitHub** → repo **Settings → Webhooks** → delete the
   `coolify-preview-<slug>` webhook.
3. **Cloudflare** → DNS → delete the `preview-<slug>` A record.
4. **Port ledger** (the table in §Host port allocation at the top of this doc)
   → mark the slot as free again. Edit this file as part of the teardown PR if
   you changed the table.

If you skip teardown you leak:

- A container set eating RAM on the VPS.
- A DNS record that will eventually point at nothing (bad hygiene, potential
  subdomain-takeover risk if the IP ever rotates).
- A webhook that will 404 on every push and spam GitHub's logs.

---

## Troubleshooting

### Traefik returns 404 but the container is running

DNS has not propagated yet, or the domain on the resource is set on the wrong
service (e.g. `backend` instead of `frontend`). Fix the service selection in
§3.5 and redeploy.

### Traefik returns 502

The frontend container is up but nginx cannot reach the backend. Almost always
means the backend failed its healthcheck. Open the backend container logs;
usually a Flyway migration error or a missing env var.

### Browser console: `blocked by CORS policy`

`APP_CORS_ALLOWEDORIGINS_0` is missing, misnamed, or set to the wrong URL.
Double-check the env var name against `application.yml` and that the value is
the **exact** preview URL including `https://` and no trailing slash.

### Flyway error `checksum mismatch`

Someone force-pushed the feature branch and rewrote a migration that had
already run on the preview DB. Either revert the migration rewrite, or delete
the preview resource (which drops the volume) and redeploy fresh. Never edit
Flyway history on a shared Postgres.

### `bind: address already in use` in deploy logs

Another Coolify resource (staging, production, or another preview) is already
using that host port. Pick a different slot from the ledger, update the env
vars, redeploy.

### Let's Encrypt certificate never issues

The most common cause is Cloudflare proxy (orange cloud) being on. Flip it to
DNS only, wait a minute, then click **Deploy** again in Coolify to re-trigger
the challenge.

---

## Why this approach and not alternatives

- **Coolify "preview deployments" feature (if/when released)**: as of early
  2026 Coolify does not ship automated per-PR previews the way Vercel or
  Netlify do. This manual recipe is the closest equivalent until that ships.
- **Repoint staging at the feature branch**: cheaper but destroys the staging
  integration baseline for however long the preview lasts. Fine for a quick
  10-minute check; not fine for a multi-day review.
- **Separate VPS per preview**: overkill. A single VPS handles several
  previews as long as ports do not collide.
- **Shared Postgres across previews**: tempting, but defeats the purpose of
  testing migrations in isolation. Keep each preview self-contained.
