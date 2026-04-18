# Traefik — Complete Guide for ALTARYS LABS

> Audience: developers and technical leads who need to understand how traffic flows through our infrastructure.

---

## What is a Reverse Proxy?

Before Traefik, understand what a reverse proxy does.

A **reverse proxy** is a server that sits between the internet and your application servers. Every incoming request hits the proxy first, which then decides where to forward it.

```
Internet
   │
   ▼
REVERSE PROXY  ◄── the single public entry point
   │
   ├──► App server A (backend)
   ├──► App server B (frontend)
   └──► App server C (another service)
```

Why not expose your app directly to the internet?
- Your app runs on an arbitrary port (8080, 3000, etc.) — the proxy normalises this to 80/443
- One IP, multiple apps — the proxy routes by domain name
- TLS termination in one place — your apps talk plain HTTP internally
- Single place for access logs, rate limiting, authentication headers

---

## What is Traefik?

Traefik is a **cloud-native reverse proxy and load balancer** built specifically for containerised environments.

Its defining feature: **it configures itself automatically** by watching your container orchestrator (Docker, Kubernetes, etc.) and reading labels attached to containers. When a container starts, Traefik picks up its routing rules immediately. When it stops, Traefik removes the route. No config file reload needed.

**Created**: 2015 by Containous (now Traefik Labs)
**Written in**: Go
**License**: MIT (open source)
**Website**: traefik.io

---

## Traefik vs Nginx — The Core Difference

| | Traefik | Nginx |
|---|---|---|
| **Config style** | Dynamic — reads from Docker labels / K8s annotations | Static — config files you write and reload |
| **TLS certificates** | Automatic via Let's Encrypt (built-in ACME client) | Manual (or with certbot as a separate tool) |
| **Container awareness** | Native — watches Docker/K8s events | None — you configure upstreams by IP/port manually |
| **Dashboard** | Built-in web UI at `:8080` | Third-party (nginx-status, Amplify) |
| **Performance** | Excellent | Excellent (Nginx is slightly faster at raw throughput) |
| **Learning curve** | Low for Docker setups | Higher — requires understanding config syntax |
| **Configuration format** | TOML, YAML, or Docker labels | Nginx config language (its own DSL) |
| **Reload on config change** | Automatic (no restart) | `nginx -s reload` required |
| **Use case** | Docker / Kubernetes environments | Static sites, traditional servers, high-throughput proxying |

### The mental model difference

**Nginx** approach — you tell Nginx everything explicitly:
```nginx
# You write this file and reload Nginx every time you add an app
server {
    listen 443 ssl;
    server_name staging-bf-terminal.altaryslabs.com;

    ssl_certificate /etc/letsencrypt/live/.../fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/.../privkey.pem;

    location / {
        proxy_pass http://localhost:3001;
    }
}
```

**Traefik** approach — you label your containers and Traefik figures it out:
```yaml
# docker-compose.yml — Traefik reads these labels automatically
services:
  frontend:
    labels:
      - "traefik.http.routers.staging.rule=Host(`staging-bf-terminal.altaryslabs.com`)"
      - "traefik.http.routers.staging.tls.certresolver=letsencrypt"
```

Coolify generates these labels for you based on the domain you enter in the UI. You never write them manually.

---

## Traefik's Core Concepts

### Entrypoints
The ports Traefik listens on.

```
port 80  (HTTP)  → "web" entrypoint
port 443 (HTTPS) → "websecure" entrypoint
```

All traffic enters through one of these two entrypoints.

### Routers
Rules that match an incoming request to a service.

A router answers: *"For this Host header (and optional path), which service should handle this request?"*

```
Request: GET https://staging-bf-terminal.altaryslabs.com/api/orders
         │
         ▼
Router:  Host(`staging-bf-terminal.altaryslabs.com`) && PathPrefix(`/api`)
         │
         ▼
Service: backend container on port 8080
```

### Services
The actual backend servers Traefik forwards requests to.

A service is a container (or group of containers) with a port. In Docker mode, Traefik discovers the container's internal IP automatically.

### Middlewares
Transformations applied to requests or responses between the router and the service.

Examples:
- Redirect HTTP → HTTPS
- Add security headers (`X-Frame-Options`, `HSTS`)
- Rate limiting
- Basic authentication
- Strip path prefix (`/api` → `/`)

### Certificates (TLS)
Traefik has a built-in ACME client. You configure a **certificate resolver** pointing to Let's Encrypt, and Traefik requests and renews certificates automatically for every domain it routes.

---

## The HTTP-01 Challenge — How Traefik Gets Certificates

Let's Encrypt won't issue a certificate without proof you control the domain. The HTTP-01 challenge is the verification mechanism:

```
1. Traefik asks Let's Encrypt: "Give me a cert for staging-bf-terminal.altaryslabs.com"

2. Let's Encrypt responds: "Prove you control that domain.
   Create a file at:
   http://staging-bf-terminal.altaryslabs.com/.well-known/acme-challenge/<random-token>
   containing this value: <secret>"

3. Traefik: creates a temporary HTTP endpoint with the token (in memory, no file)

4. Let's Encrypt: makes an HTTP GET to that URL from its own servers
   → If the response matches: domain ownership proved ✓ → certificate issued
   → If the response fails: challenge failed ✗ → no certificate

5. Traefik: receives the certificate, stores it, begins serving HTTPS
   Certificate auto-renews 30 days before expiry.
```

**Why Cloudflare grey cloud is required:**
When Cloudflare proxy is ON (orange cloud), Let's Encrypt's HTTP request goes to Cloudflare's edge server — not your VPS. Cloudflare doesn't know about the ACME challenge token → returns 404 → challenge fails → no certificate. Grey cloud = DNS only, HTTP goes straight to your VPS.

There is an alternative — the **DNS-01 challenge** — which doesn't require port 80 access. It works by creating a DNS TXT record to prove domain ownership. This would allow orange cloud but requires giving Traefik API access to your Cloudflare account to create DNS records automatically. More complex, but unlocks Cloudflare proxy for DDoS protection.

---

## How Traefik Works in Our Setup

```
                    COOLIFY
                       │
                       │ generates Docker labels
                       ▼
              docker-compose.yml
         ┌─────────────┴────────────┐
         │                          │
    prod resource               staging resource
    (branch: main)              (branch: develop)
    domain: bloomfield-...      domain: staging-bf-...
         │                          │
         ▼                          ▼
   frontend:3000              frontend:3001
   backend:8080               backend:8081
         │                          │
         └──────────┬───────────────┘
                    ▼
                TRAEFIK
         (Coolify installs this,
          you never touch it directly)
                    │
          ┌─────────┴──────────┐
          │                    │
       port 80              port 443
    (redirect to 443)     (TLS termination)
          │                    │
          └─────────┬──────────┘
                    │
               INTERNET
```

Coolify runs Traefik as a system-level container on your VPS. When you create a new application and set its domain in Coolify, Coolify:
1. Injects Traefik labels into the container
2. Traefik detects the new container within seconds
3. Traefik requests a Let's Encrypt certificate for the domain
4. HTTPS is live

You configure nothing in Traefik directly. The Coolify UI is the abstraction layer.

---

## Traefik Dashboard

Traefik exposes a built-in dashboard at port `8080` on your VPS (usually protected by Coolify). It shows:
- All active routers, services, and middlewares
- Certificate status per domain
- Request counts and errors
- Which container each route points to

Useful for debugging routing issues or certificate failures.

---

## When You Would Choose Nginx Over Traefik

| Scenario | Better choice |
|---|---|
| Containerised apps on Docker/Kubernetes | **Traefik** |
| Static website on a bare VM | **Nginx** |
| High-throughput API gateway (100k+ req/s) | Nginx (marginally faster) |
| Complex caching rules (assets, CDN integration) | Nginx |
| You already have Nginx expertise on the team | Either — skill matters |
| Coolify manages your infrastructure | **Traefik** (it's built in) |

For ALTARYS LABS running on Coolify: Traefik is the right choice and is already there. There is no reason to introduce Nginx.

---

## Quick Reference

| Concept | What it does |
|---|---|
| Entrypoint | Port Traefik listens on (80, 443) |
| Router | Matches request by Host/path → sends to service |
| Service | The backend container receiving the request |
| Middleware | Transform request/response (redirect, auth, headers) |
| Certificate resolver | Automatic Let's Encrypt certificate management |
| HTTP-01 challenge | Let's Encrypt domain verification via HTTP |
| Docker labels | How Traefik discovers routing config from containers |
