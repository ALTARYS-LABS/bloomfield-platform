# Deploying a React + Spring Boot App on Coolify / Hostinger

*A practical guide based on a real deployment of the Bloomfield Intelligence Platform — a BRVM real-time market terminal built with React 19 + Spring Boot 4.0.3, containerized with Docker Compose.*

---

## 1. Architecture Overview

Before touching any server, understand what each layer does:

```
Browser
  └─► Cloudflare DNS (resolves domain → VPS IP)
        └─► Hostinger VPS (Ubuntu 24.04)
              └─► Coolify (self-hosted PaaS, port 8000)
                    └─► Traefik (reverse proxy, handles HTTPS)
                          ├─► frontend container (Nginx, port 80)
                          │     └─► proxies /api and /ws → backend:8080 (internal)
                          └─► backend container (Spring Boot, port 8080, internal only)
```

Key insight: **the backend never needs to be reachable from the internet**. Only the frontend Nginx is public-facing. It talks to the backend via Docker's internal network using the service name `backend` as hostname.

---

## 2. Hostinger VPS Setup

### Choosing the right VPS plan
Hostinger offers VPS plans pre-installed with Coolify. This saves you from installing Docker, Coolify, and Traefik manually — they come ready.

### First access
After provisioning (~10 min), you get a VPS with:
- Ubuntu 24.04
- Docker pre-installed
- Coolify running on port `8000`
- Traefik running as the reverse proxy

Access Coolify at `http://<your-vps-ip>:8000` and create your admin account.

### Choosing server type in Coolify
Coolify asks you to choose a server type:

| Option | When to use |
|--------|-------------|
| **This Machine** | Coolify is on the same VPS as your app — single server setup |
| Remote Server | Coolify manages a separate VPS via SSH |
| Hetzner Cloud | Managed Hetzner integration |

**Choose "This Machine"** — Coolify and the app live on the same VPS. Correct for a single-server deployment.

---

## 3. Cloudflare DNS Configuration

### Why Cloudflare, not Hostinger DNS
Our domain `altaryslabs.com` was registered and managed at Cloudflare (nameservers: `pedro.ns.cloudflare.com`, `teresa.ns.cloudflare.com`). Hostinger showed a screen asking us to switch nameservers to Hostinger. **Ignore it entirely** — there is no reason to move DNS management to Hostinger.

### Adding the A record
In Cloudflare → your domain → DNS → Add record:

| Type | Name | Value | Proxy |
|------|------|-------|-------|
| A | `bloomfield-intelligence` | `<VPS IP>` | **OFF (grey cloud)** |

This creates `bloomfield-intelligence.altaryslabs.com` → VPS IP.

### Why proxy must be OFF (grey cloud)

Cloudflare has two modes:
- **Orange cloud (proxy ON):** Traffic goes through Cloudflare's CDN. Cloudflare terminates SSL and re-encrypts to your server. The SSL certificate is Cloudflare's.
- **Grey cloud (DNS only):** Cloudflare just resolves the domain to your IP. Traffic goes directly to your VPS. Your server terminates SSL.

**Use grey cloud** because Coolify uses Let's Encrypt to auto-issue SSL certificates via Traefik. For Let's Encrypt to work, it must be able to reach your server directly on ports 80 and 443 to complete the HTTP challenge. If Cloudflare's proxy intercepts traffic, Let's Encrypt cannot verify ownership and certificate issuance fails.

---

## 4. Connecting the GitHub Repository in Coolify

### Public vs Private repository
Choose based on your repo visibility:
- **Public Repository** — no authentication needed, Coolify clones directly
- **Private Repository (with GitHub App)** — installs a GitHub App to grant Coolify read access to your org/repo

### What Coolify auto-detects
Coolify reads the repository and detects:
- **Branch:** `main`
- **Build Pack:** Docker Compose (because `docker-compose.yml` exists at root)
- **Compose file location:** `/docker-compose.yml`

### Domain configuration
In the Coolify configuration screen:
- **Domains for frontend:** `https://bloomfield-intelligence.altaryslabs.com`
- **Domains for backend:** leave empty — backend is internal only

Coolify injects Traefik labels into the frontend container based on the domain you set, routing HTTPS traffic to it automatically and issuing a Let's Encrypt certificate.

### Pre/Post deployment commands
Leave these empty unless you have database migrations to run (e.g. `php artisan migrate` for Laravel, `./gradlew flywayMigrate` for Spring with Flyway).

---

## 5. Errors Encountered, Causes, and Fixes

### Error 1 — Port already allocated

**Error message:**
```
Bind for :::8080 failed: port is already allocated
```

**Cause:**
The `docker-compose.yml` had:
```yaml
backend:
  ports:
    - "8080:8080"
```
This binds port 8080 on the **host machine** to the container. On a Coolify VPS, port 8080 may already be in use by Coolify's own infrastructure or a previous failed container.

**Fix:**
Remove the `ports` section from the backend service entirely:
```yaml
backend:
  build: ./backend
  restart: unless-stopped
  environment:
    SPRING_PROFILES_ACTIVE: prod
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 60s
```

**Why this works:**
Docker Compose creates a private network for all services in the same compose file. The frontend container can reach the backend at `backend:8080` using the service name as hostname — without any host port binding. This is Docker's internal DNS resolution. The backend never needs to be reachable from outside the Docker network, so there is no reason to bind a host port. Coolify's Traefik only needs to reach the frontend container.

> **Rule of thumb:** In production, only bind host ports for services that must be directly reachable from the internet. Internal services (databases, backends behind a frontend proxy) should never expose host ports.

---

### Error 2 — Backend container unhealthy (`curl: not found`)

**Error message in Coolify logs:**
```
Container backend ... Waiting
Container backend ... Error
dependency failed to start: container backend is unhealthy
```

**Diagnosis:**
Connect to the backend container via Coolify's Terminal tab and run:
```bash
curl http://localhost:8080/api/health
# bash: curl: command not found
```

**Cause:**
The `docker-compose.yml` health check was:
```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
```

But the `backend/Dockerfile` used `eclipse-temurin:25-jre` as the runtime image:
```dockerfile
FROM eclipse-temurin:25-jre
```

This is a **minimal JRE image** — it contains only what is needed to run Java. It does not include system utilities like `curl`, `wget`, or even `bash` by default. The health check command therefore failed immediately on every attempt, making the container permanently unhealthy, which blocked the frontend container from starting (`depends_on: condition: service_healthy`).

**Fix:**
Install `curl` in the Dockerfile runtime stage:
```dockerfile
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Why this works:**
`eclipse-temurin:25-jre` is based on Ubuntu/Debian, so `apt-get` is available. Installing `curl` during the image build makes it present at runtime when Docker executes the health check. The `--no-install-recommends` flag keeps the image small by skipping optional packages. The `rm -rf /var/lib/apt/lists/*` cleans the apt cache to avoid bloating the image layer.

**Alternative approaches:**
- `wget -qO- http://localhost:8080/api/health || exit 1` — but `wget` has the same availability problem in minimal images
- `eclipse-temurin:25-jre-alpine` — Alpine includes `wget` but uses `apk` not `apt`, and Alpine can have glibc compatibility issues with some JVM builds
- Spring Boot Actuator with a Java-based health check — still needs an external tool to query it from the health check command
- On Kubernetes: `httpGet` probes are handled natively by K8s — no curl needed inside the container at all (see Section 6)

> **Rule of thumb:** Always verify that tools used in `healthcheck.test` are actually present in your runtime Docker image. Minimal images (JRE, distroless, alpine) strip out most utilities.

---

## 6. What It Would Take on Kubernetes

Kubernetes (K8s) achieves the same result — running your containers in production — but with far more infrastructure to manage. Here is what the equivalent setup would look like.

### Infrastructure you would need to provision yourself

| Component | Docker Compose + Coolify | Kubernetes |
|-----------|--------------------------|------------|
| Container runtime | Docker (Coolify manages) | containerd (you configure) |
| Reverse proxy / ingress | Traefik (Coolify manages) | Ingress controller (nginx-ingress, Traefik — you install) |
| SSL certificates | Let's Encrypt via Traefik (Coolify manages) | cert-manager (you install and configure) |
| DNS | One A record | Same one A record |
| Service discovery | Docker internal DNS (`backend:8080`) | K8s Service objects |
| Health checks | `healthcheck:` in docker-compose.yml | `livenessProbe` + `readinessProbe` in Pod spec |
| Restart policy | `restart: unless-stopped` | Pod restart policy + Deployment controller |
| Image registry | Built on VPS from source (Coolify handles) | Push to registry (GHCR, Docker Hub), reference by tag |

### Kubernetes manifests you would write

Instead of one `docker-compose.yml`, you would write multiple YAML files:

**`backend-deployment.yaml`**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
spec:
  replicas: 1
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      containers:
        - name: backend
          image: ghcr.io/altarys-labs/bloomfield-backend:latest
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: prod
          readinessProbe:
            httpGet:
              path: /api/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /api/health
              port: 8080
            initialDelaySeconds: 90
            periodSeconds: 30
```

**`backend-service.yaml`** (internal only — ClusterIP, equivalent to no host port binding)
```yaml
apiVersion: v1
kind: Service
metadata:
  name: backend
spec:
  selector:
    app: backend
  ports:
    - port: 8080
      targetPort: 8080
  type: ClusterIP
```

You would also write: `frontend-deployment.yaml`, `frontend-service.yaml`, `ingress.yaml` (with TLS), and a `Certificate` resource for cert-manager.

### Key differences to understand

**Health checks without curl:** On Kubernetes, `livenessProbe` and `readinessProbe` support `httpGet` natively — K8s makes the HTTP request itself, no curl required inside the container. This eliminates the curl problem we hit entirely.

**Image management:** With Coolify, images are built directly from source on the VPS. On Kubernetes, you build images in CI (GitHub Actions, etc.), push to a registry, and K8s pulls from there. This enables proper versioning and rollbacks by tag.

**Scaling:** Set `replicas: 2` in a Deployment and Kubernetes load-balances automatically. With Docker Compose, scaling requires manual intervention and a load balancer.

**Complexity vs control:**

| Concern | Coolify | Kubernetes |
|---------|---------|------------|
| Setup time | ~30 min | Days to weeks |
| SSL | Automatic | Requires cert-manager |
| Horizontal scaling | Manual | Native, automatic |
| Rolling deployments | Basic | Fine-grained control |
| Multi-node | No | Yes |
| Operational overhead | Low | High |

### When to choose Kubernetes

Use Coolify/Docker Compose until you have a real scaling problem or organizational need for K8s. Kubernetes becomes worthwhile when you need:

- Multi-node horizontal scaling (multiple VPS/nodes)
- Rolling deployments with zero downtime across many replicas
- Complex networking between many microservices
- You are already on a managed cluster (GKE, EKS, AKS) with a platform team
- Compliance requirements that mandate K8s-native tooling

**Most apps never reach this threshold.** For a prototype or small production app, Kubernetes is significant overkill — you would spend days configuring what Coolify gives you in 30 minutes.
