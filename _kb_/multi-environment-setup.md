# Multi-Environment Setup Tutorial
## From Cloudflare to Coolify to GitHub — The Complete Guide

> Written for ALTARYS LABS. Reusable for any future project deploying a Spring Boot + React app on a VPS via Coolify.

---

## Why This Setup Exists

Running a single production environment is a trap: every untested change goes live immediately. When you have clients, that means bugs in production with no buffer.

The goal of this setup is a **two-environment pipeline**:

```
Developer laptop
      │
      ▼
feature branch (short-lived, 1-2 days)
      │
      ▼  PR + review
origin/develop ──► auto-deploy ──► STAGING  (internal testing)
      │
      ▼  PR + sign-off (release event)
origin/main ──────► auto-deploy ──► PRODUCTION  (clients)
```

**Why `develop` as staging?** Because staging must always reflect what will go to production next. `develop` is the integration branch — all tested features accumulate there before being promoted to `main`.

---

## Part 1 — The Application Layer

### 1.1 Spring Boot Profiles

Spring Boot supports environment-specific configuration via profiles. A profile is activated at runtime with `SPRING_PROFILES_ACTIVE=staging` and causes Spring to load `application-staging.yml` on top of the base `application.yml`.

**Why this matters for CORS**: CORS requires the backend to explicitly list allowed origins. If you hardcode `https://your-app.com` in code or in a shared config file, your staging backend will reject requests from `https://staging-your-app.com`. Profiles solve this cleanly.

```
src/main/resources/
├── application.yml           ← shared defaults (DB config, JWT settings, etc.)
├── application-prod.yml      ← production overrides (prod CORS origin)
└── application-staging.yml   ← staging overrides (staging CORS origin)
```

**`application-staging.yml`**:
```yaml
app:
  cors:
    allowed-origins:
      - "https://staging-bf-terminal.altaryslabs.com"
logging:
  level:
    root: WARN
    com.bloomfield: INFO
```

**Why `@ConfigurationProperties` is the right pattern**:

```java
@ConfigurationProperties(prefix = "app.cors")
record CorsProperties(List<String> allowedOrigins) {}
```

This record reads `app.cors.allowed-origins` from whichever profile is active. No `if (profile == "staging")` branching in code. The YAML is the single source of truth for environment-specific values. This is the Spring Boot best practice.

### 1.2 Docker Compose — One File for All Environments

You do not want two `docker-compose.yml` files. They will drift. Instead, parameterize the values that differ between environments using env var substitution with defaults:

```yaml
services:
  backend:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}  # default: prod
  frontend:
    ports:
      - "${FRONTEND_PORT:-3000}:80"  # default: 3000
```

**Syntax**: `${VAR_NAME:-default_value}` — uses the env var if set, falls back to the default.

**Why separate ports?** Both staging and production run on the **same VPS**. The host port (left of `:`) must be unique per process. Container port (right) stays 80. Production gets 3000, staging gets 3001. Traefik (the reverse proxy inside Coolify) routes by domain name, not port — so clients never see these port numbers.

| Environment | `SPRING_PROFILES_ACTIVE` | `FRONTEND_PORT` |
|---|---|---|
| Production (default) | `prod` | `3000` |
| Staging | `staging` | `3001` |

This approach means you only override what actually differs — everything else inherits from the compose file defaults.

---

## Part 2 — The Infrastructure Layer (Coolify)

### What is Coolify?

Coolify is an open-source self-hosted PaaS (Platform-as-a-Service). Think Heroku, but running on your own VPS. It manages:
- Container builds (Docker Compose or Dockerfile)
- Reverse proxy routing (Traefik under the hood)
- TLS certificate issuance (Let's Encrypt via Traefik)
- Environment variable management
- Deploy webhooks
- Log streaming

### 2.1 Coolify's Mental Model

```
Server
└── Project (e.g., "Bloomfield Terminal")
    ├── Environment: production
    │   └── Resource: Docker Compose application (branch: main)
    └── Environment: staging
        └── Resource: Docker Compose application (branch: develop)
```

**Project** = grouping for related apps (same repo, different environments).
**Environment** = a deployment context (production, staging, preview, etc.).
**Resource** = the actual running application.

### 2.2 Creating the Staging Environment

1. In Coolify, open your project → click **"+ New Environment"** → name it `staging`
2. Inside the staging environment → **"+ New Resource"** → Docker Compose
3. Point to the same GitHub repo, **branch: `develop`**
4. Compose file path: `/docker-compose.yml` (same file as production)

**Set environment variables** for the staging resource:
```
SPRING_PROFILES_ACTIVE=staging
FRONTEND_PORT=3001
```

These two variables are the only difference between your staging and production Coolify apps. Everything else comes from the compose file defaults.

**Set the domain**: `https://staging-bf-terminal.altaryslabs.com`

Traefik will automatically request a Let's Encrypt certificate for this domain once DNS is configured correctly.

### 2.3 Webhooks for Auto-Deploy

Without webhooks, Coolify doesn't know when you push to `develop`. You must connect the two:

**In Coolify** (staging resource → Webhooks or Deploy tab):
- Copy the webhook URL (format: `https://your-coolify.com/webhooks/deploy/...`)

**In GitHub** (repo → Settings → Webhooks → Add webhook):
- Payload URL: paste the Coolify URL
- Content type: `application/json`
- Event: "Just the push event"

Coolify filters by branch on its side — staging webhook only triggers a deploy when the push is to `develop`.

Do the same for production (push to `main` → deploy production). This is likely already configured if production was set up through Coolify's GitHub integration.

**Flow after setup**:
```
git push origin develop
       │
       ▼ (webhook)
Coolify receives push event
       │
       ▼
Coolify builds Docker image
       │
       ▼
Coolify replaces running containers
       │
       ▼
Staging is live within ~2 minutes
```

---

## Part 3 — The DNS Layer (Cloudflare)

### 3.1 What DNS Does Here

DNS translates `staging-bf-terminal.altaryslabs.com` into the IP address of your VPS. Without it, Traefik's Let's Encrypt certificate request fails and the browser can't find the server.

### 3.2 The Grey Cloud Rule (Critical)

Cloudflare offers two modes for each DNS record:
- **Orange cloud (proxy ON)**: Traffic goes through Cloudflare's CDN. Cloudflare terminates TLS. Your origin server receives Cloudflare's IP, not the client's.
- **Grey cloud (proxy OFF)**: DNS only. Traffic goes directly to your VPS. Your origin server terminates TLS.

**For Coolify + Traefik + Let's Encrypt: use grey cloud.**

Why? Let's Encrypt's HTTP-01 challenge requires a direct HTTP connection from Let's Encrypt's servers to your VPS on port 80. When Cloudflare proxy is ON, Let's Encrypt hits Cloudflare instead of your VPS — the challenge fails — no certificate.

Once the certificate is issued and working, you _can_ switch to orange cloud if you want Cloudflare's DDoS protection. But start grey until TLS is confirmed working.

### 3.3 DNS Record to Add

| Type | Name | Value | Proxy |
|---|---|---|---|
| A | `staging-bf-terminal` | `<your VPS IP>` | OFF (grey) |

The production A record (`bloomfield-intelligence` or `@`) already exists — staging needs its own subdomain pointing to the same IP.

**Why same IP?** Both environments run on the same VPS. Traefik listens on ports 80 and 443 and routes traffic by the `Host` header (domain name). Two domains, one IP, two containers — Traefik sorts it out.

---

## Part 4 — The Git Layer

### 4.1 Branch Model

```
main    → always deployable to production
develop → always deployable to staging, represents "next release"
```

Feature branches are created from `develop`, merged to `develop` via PR. When `develop` is stable and tested, a release PR promotes it to `main`.

**Why not just one branch?** Without `develop`, every feature branch goes directly to production. You lose the ability to integrate and test multiple features together before release.

### 4.2 Branch Protection Rules (GitHub)

Set these in GitHub → repo Settings → Branches → Add rule:

**For `main`**:
- Require a pull request before merging
- Require status checks to pass (CI tests)
- Do not allow bypassing (even for admins, in a team context)

**For `develop`**:
- Same as `main`
- This prevents accidental direct pushes

These rules enforce the workflow even if someone forgets.

### 4.3 The Release PR

A `develop → main` PR is a deliberate release event, not a routine merge. It must include:
- Summary of all changes since the last release
- Verification checklist (what to test on production after deploy)
- Rollback plan (revert the merge commit if prod breaks)

See `standards/git-workflow.md` for the full release PR template.

---

## Part 5 — Can Terraform Automate This?

**Short answer**: Partially yes, and it is worth doing for teams.

### What Can Be Automated

| Component | Terraform Provider | What You Can Manage |
|---|---|---|
| Cloudflare DNS | `cloudflare/cloudflare` | A records, CNAME, proxy settings, page rules |
| GitHub | `integrations/github` | Repos, branch protection rules, webhooks, team permissions |
| Coolify | No official provider | Limited — Coolify has a REST API but no Terraform provider yet |

### Cloudflare via Terraform

```hcl
resource "cloudflare_record" "staging" {
  zone_id = var.cloudflare_zone_id
  name    = "staging-bf-terminal"
  value   = var.vps_ip
  type    = "A"
  proxied = false  # grey cloud — required for Let's Encrypt
}

resource "cloudflare_record" "production" {
  zone_id = var.cloudflare_zone_id
  name    = "bloomfield-intelligence"
  value   = var.vps_ip
  type    = "A"
  proxied = false
}
```

### GitHub via Terraform

```hcl
resource "github_branch_protection" "main" {
  repository_id = github_repository.app.node_id
  pattern       = "main"

  required_pull_request_reviews {
    required_approving_review_count = 1
  }

  required_status_checks {
    strict   = true
    contexts = ["ci / test"]
  }
}

resource "github_repository_webhook" "coolify_staging" {
  repository = github_repository.app.name

  configuration {
    url          = var.coolify_staging_webhook_url
    content_type = "json"
    insecure_ssl = false
  }

  events = ["push"]
}
```

### Coolify — The Gap

Coolify does not have a Terraform provider (as of early 2026). You must configure it via the UI or its REST API. This means your Coolify setup is not yet infrastructure-as-code.

Options:
1. **Manual (current approach)** — acceptable for a small team, document the steps
2. **Coolify API + shell scripts** — scripted setup, not idempotent but reproducible
3. **Migrate to a provider with Terraform support** — Railway, Render, or fly.io have better IaC support if this becomes a pain point
4. **Use Kubernetes + Helm** — full IaC for infrastructure, but significantly more operational complexity

**Recommendation for ALTARYS LABS now**: Automate Cloudflare DNS and GitHub settings with Terraform (high value, straightforward). Leave Coolify manual and document it (Coolify is fast to configure by hand and doesn't change often).

---

## Part 6 — What Role Can GitHub Actions Play?

GitHub Actions can complement or partially replace Coolify's auto-deploy. Here is the spectrum:

### Option A — Webhooks Only (Current Setup)

```
push to develop → GitHub webhook → Coolify deploys
```

Simple. Coolify manages everything. GitHub Actions not involved. Works well for small teams.

**Downside**: Coolify deploys on every push, even if tests are failing.

### Option B — GitHub Actions as CI Gate + Coolify as CD

```
push to develop
      │
      ▼
GitHub Actions: run tests + lint
      │
   pass? ──NO──► mark commit as failed, do NOT deploy
      │YES
      ▼
trigger Coolify webhook via API call
      │
      ▼
Coolify deploys staging
```

This is the recommended upgrade. You keep Coolify as the deployment engine but only trigger it after CI passes.

```yaml
# .github/workflows/deploy-staging.yml
name: Deploy to Staging

on:
  push:
    branches: [develop]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run backend tests
        run: cd backend && ./gradlew test
      - name: Run frontend tests
        run: cd frontend && pnpm test --run

  deploy:
    needs: test  # only runs if test job passes
    runs-on: ubuntu-latest
    steps:
      - name: Trigger Coolify deploy
        run: |
          curl -X POST "${{ secrets.COOLIFY_STAGING_WEBHOOK_URL }}"
```

### Option C — GitHub Actions as Full CD (No Coolify Webhooks)

GitHub Actions SSH into the VPS and run `docker compose pull && docker compose up -d`. More control, more complexity. Skip this unless you outgrow Coolify.

### Option D — GitHub Actions for Release Automation

Automate the boring parts of a release:

```yaml
# .github/workflows/release-pr.yml
# Triggered manually: creates a PR from develop to main
name: Open Release PR

on:
  workflow_dispatch:  # manual trigger from GitHub UI

jobs:
  release-pr:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Create release PR
        run: |
          gh pr create \
            --base main \
            --head develop \
            --title "Release $(date +%Y-%m-%d)" \
            --body "$(git log origin/main..origin/develop --oneline | sed 's/^/- /')"
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

One click in GitHub UI → release PR opens with the full changelog pre-filled.

### Summary: What to Implement Now vs. Later

| Action | Priority | Effort |
|---|---|---|
| Coolify webhooks (push → auto-deploy) | **Now** | Low |
| GitHub branch protection rules | **Now** | Low |
| Cloudflare DNS via Terraform | Soon | Medium |
| GitHub Actions CI gate before deploy | Soon | Medium |
| GitHub Actions release PR automation | Later | Low |
| Full Terraform for GitHub + Cloudflare | Later | Medium |

---

## Appendix — Full Architecture Diagram

```
                          DEVELOPER
                              │
                    git push origin feat/xyz
                              │
                        GITHUB REPO
                    ┌─────────┴──────────┐
                    │                    │
               PR to develop        (blocked: no
                    │               direct push)
                    ▼
              origin/develop
                    │
                    │ webhook (push event)
                    ▼
               GITHUB ACTIONS (optional CI gate)
                    │
                    │ tests pass
                    ▼
        COOLIFY (staging environment)
                    │
                    │ docker compose up -d
                    │ SPRING_PROFILES_ACTIVE=staging
                    │ FRONTEND_PORT=3001
                    ▼
              TRAEFIK (reverse proxy)
                    │
                    │ routes by Host header
                    ▼
    staging-bf-terminal.altaryslabs.com
                    │
              CLOUDFLARE DNS
              (A record, grey cloud)
                    │
                    ▼
                  VPS IP


              ─ ─ ─ ─ ─ ─ ─ ─ RELEASE ─ ─ ─ ─ ─ ─ ─ ─


              origin/develop
                    │
               PR to main
               (tested, signed off)
                    ▼
               origin/main
                    │
                    │ webhook
                    ▼
        COOLIFY (production environment)
                    │
                    │ docker compose up -d
                    │ SPRING_PROFILES_ACTIVE=prod (default)
                    │ FRONTEND_PORT=3000 (default)
                    ▼
              TRAEFIK (reverse proxy)
                    │
                    ▼
 bloomfield-intelligence.altaryslabs.com
```
