# High Availability Architecture — Bloomfield Terminal
## On Hostinger VPS + Coolify

> Audience: technical leads and client-facing stakeholders. Covers the architecture needed to achieve fault tolerance, zero-downtime deployments, and horizontal scaling.

---

## What is High Availability?

**High Availability (HA)** means the system continues to function correctly even when individual components fail. It is measured as uptime percentage:

| SLA | Downtime per year | Downtime per month |
|---|---|---|
| 99% | 87 hours | 7.2 hours |
| 99.9% ("three nines") | 8.7 hours | 43 minutes |
| 99.99% ("four nines") | 52 minutes | 4.3 minutes |
| 99.999% ("five nines") | 5.2 minutes | 26 seconds |

The current single-VPS setup targets approximately 99–99.5% (hardware failures, reboots for OS patches, deployment downtime). A proper HA architecture targets 99.9%+.

---

## Current Architecture (Single VPS)

This is what we run today — simple, cost-effective for a prototype, but has single points of failure.

```
                    INTERNET
                       │
                       ▼
             ┌─────────────────┐
             │   Cloudflare    │
             │   (DNS only,    │
             │   grey cloud)   │
             └────────┬────────┘
                      │
                      ▼
             ┌─────────────────┐
             │  Hostinger VPS  │  ← SINGLE POINT OF FAILURE
             │                 │
             │  ┌───────────┐  │
             │  │  Traefik  │  │
             │  └─────┬─────┘  │
             │        │        │
             │  ┌─────┴─────┐  │
             │  │ ┌───────┐ │  │
             │  │ │Frontend│ │  │  Production (port 3000)
             │  │ │React  │ │  │
             │  │ └───┬───┘ │  │
             │  │     │     │  │
             │  │ ┌───▼───┐ │  │
             │  │ │Backend│ │  │
             │  │ │Spring │ │  │
             │  │ └───┬───┘ │  │
             │  └─────┼─────┘  │
             │        │        │
             │  ┌─────▼─────┐  │
             │  │PostgreSQL │  │  ← SINGLE POINT OF FAILURE
             │  │ + Keycloak│  │
             │  └───────────┘  │
             └─────────────────┘
```

**Single points of failure:**
- VPS goes down → everything is down
- PostgreSQL crashes → backend is down
- Deployment → brief downtime (container restart)

---

## Target HA Architecture

The following architecture eliminates single points of failure with two Hostinger VPS nodes, a managed or replicated database, and a load balancer.

```
                         INTERNET
                            │
                            ▼
                 ┌─────────────────────┐
                 │     Cloudflare      │
                 │  (orange cloud ON)  │  ← DDoS protection, CDN
                 │  DNS load balancing │     static asset caching
                 └──────────┬──────────┘
                            │
               ┌────────────┴────────────┐
               │                         │
               ▼                         ▼
    ┌──────────────────┐      ┌──────────────────┐
    │  Hostinger VPS 1 │      │  Hostinger VPS 2 │
    │  (primary)       │      │  (replica)       │
    │                  │      │                  │
    │  ┌────────────┐  │      │  ┌────────────┐  │
    │  │  Traefik   │  │      │  │  Traefik   │  │
    │  └─────┬──────┘  │      │  └─────┬──────┘  │
    │        │         │      │        │         │
    │  ┌─────┴──────┐  │      │  ┌─────┴──────┐  │
    │  │ Frontend x2│  │      │  │ Frontend x2│  │
    │  │ (replicas) │  │      │  │ (replicas) │  │
    │  └─────┬──────┘  │      │  └─────┬──────┘  │
    │        │         │      │        │         │
    │  ┌─────┴──────┐  │      │  ┌─────┴──────┐  │
    │  │ Backend x2 │  │      │  │ Backend x2 │  │
    │  │ (replicas) │  │      │  │ (replicas) │  │
    │  └────────────┘  │      │  └────────────┘  │
    └──────────────────┘      └──────────────────┘
               │                         │
               └────────────┬────────────┘
                            │
                            ▼
              ┌─────────────────────────┐
              │    Database Layer       │
              │                        │
              │  ┌────────┐ ┌────────┐ │
              │  │Postgres│→│Postgres│ │  Primary → Replica
              │  │Primary │ │Replica │ │  (streaming replication)
              │  └────────┘ └────────┘ │
              │                        │
              │  ┌──────────────────┐  │
              │  │    Keycloak      │  │  (clustered or managed)
              │  └──────────────────┘  │
              │                        │
              │  ┌──────────────────┐  │
              │  │  Redis (cache +  │  │  (session sharing between
              │  │  session store)  │  │   backend replicas)
              │  └──────────────────┘  │
              └─────────────────────────┘
```

---

## Component by Component

### Layer 1 — Cloudflare (Load Balancing + CDN)

**Role**: Global entry point. Distributes traffic between VPS 1 and VPS 2. Caches static frontend assets at Cloudflare's edge (React JS bundles, CSS, images).

**Orange cloud ON** for HA: with two VPS nodes, Cloudflare proxies the traffic. It health-checks both nodes and automatically stops sending traffic to a node that is down. This is Cloudflare's **Load Balancing** feature (paid, ~$5/month).

Without paid load balancing: use Cloudflare DNS failover (two A records, same domain — Cloudflare round-robins, not true health-check-based failover).

**Static asset caching**: The React frontend changes only on deploy. Cloudflare can cache all JS/CSS bundles at the edge globally — users in Paris, Abidjan, Montreal all get the frontend from the nearest Cloudflare PoP in milliseconds.

### Layer 2 — VPS Nodes (App Servers)

Two Hostinger VPS running identical stacks (Coolify + Traefik + app containers). Both nodes run all the time.

**Coolify multi-server**: Coolify supports adding multiple servers to a single Coolify instance. You deploy to both servers from one Coolify dashboard.

**Why two replicas of frontend and backend per node?**
- Enables **zero-downtime deployments**: Traefik drains one container, deploys new image, brings it up, then moves to next
- Handles traffic spikes on a single node without saturation

**Hostinger VPS specs for this setup:**
- VPS 1 (primary): KVM 2 plan minimum (2 vCPU, 8 GB RAM)
- VPS 2 (replica): same spec
- Both in the same datacenter region for low-latency DB connections

### Layer 3 — Database (PostgreSQL Primary/Replica)

The database is the hardest layer to make highly available.

**Option A — PostgreSQL Streaming Replication (self-hosted)**

A second PostgreSQL instance on a separate VPS (or dedicated DB VPS) receives a real-time stream of all writes from the primary.

```
Backend → Primary PostgreSQL (reads + writes)
                │
                │ WAL streaming (Write-Ahead Log)
                ▼
          Replica PostgreSQL (reads only — for reporting, or automatic failover)
```

If primary fails, promote replica to primary (manual or automatic via Patroni). Recovery time: 30–120 seconds.

**Option B — Managed PostgreSQL (recommended)**

Use Hostinger Managed Database or Supabase (hosted PostgreSQL with automatic HA, backups, failover). You pay more but eliminate operational complexity. Recommended for a team focused on product, not infrastructure.

**Why Redis is needed in HA?**

When you run multiple backend instances (replicas), each instance has its own memory. If a user's WebSocket connection or session lands on Backend A, and the next request goes to Backend B — the session is lost.

Redis acts as a **shared external store** for:
- HTTP session data
- WebSocket connection state (if using sticky sessions is not viable)
- Cache (market data, frequently read tenant config)

All backend replicas read/write the same Redis → state is shared.

### Zero-Downtime Deployments

With a single container, deploying means: stop → pull new image → start → downtime (~5 seconds).

With replicas and Traefik:
```
Deploy new version:

1. Traefik stops routing to Backend-A replica 1
2. Backend-A replica 1 is stopped and updated
3. Backend-A replica 1 comes back online (health check passes)
4. Traefik resumes routing to Backend-A replica 1
5. Repeat for Backend-A replica 2
→ Zero downtime. Users never see an interruption.
```

This is called a **rolling deployment**. Coolify supports this natively when you configure multiple replicas.

---

## Upgrade Path — From Current to HA

You do not need to implement this all at once. Here is the incremental path:

### Phase 0 — Current (prototype)
Single VPS, single containers, ~99% uptime. Good for development and early users.

### Phase 1 — Database resilience (~€50/month additional)
Add managed PostgreSQL with automatic failover. Eliminates the biggest single point of failure without changing the app stack. Do this before your first paying client.

### Phase 2 — Replicas on single VPS (~no additional cost)
Run 2 replicas of frontend and backend on the existing VPS via Coolify. Enables zero-downtime deployments. Add Redis for session sharing.

### Phase 3 — Second VPS (~€40/month additional)
Add a second Hostinger VPS to Coolify. Deploy app containers to both. Configure Cloudflare DNS with two A records (or paid load balancing). Now the system survives a full VPS outage.

### Phase 4 — CDN + DDoS protection
Enable Cloudflare orange cloud for static assets. Add rate limiting. Suitable when you have clients who care about availability SLAs.

---

## Cost Estimate (Hostinger, 2026)

| Component | Cost/month |
|---|---|
| VPS 1 — KVM 2 (2 vCPU, 8 GB) | ~€14 |
| VPS 2 — KVM 2 (2 vCPU, 8 GB) | ~€14 |
| Managed PostgreSQL (Hostinger) | ~€20–40 |
| Redis (small instance or on VPS) | €0–10 |
| Cloudflare Load Balancing (optional) | ~€5 |
| **Total (Phase 3 HA)** | **~€55–85/month** |

Compare: AWS/GCP equivalent HA setup costs €200–500/month. Hostinger VPS offers exceptional price/performance for early-stage products.

---

## What to Tell a Client

When explaining the architecture to a client, use this language:

> "The Bloomfield Terminal runs on a multi-node infrastructure with automatic failover. Your data is stored in a managed database with real-time replication — if the primary database server fails, a replica takes over automatically within seconds. Application traffic is distributed across two independent servers, so if one server fails, the other continues serving all requests without interruption. Deployments are rolling — new versions are released without any service interruption. We target 99.9% availability, which translates to less than 45 minutes of potential downtime per month, typically zero in practice."

---

## What We Do NOT Need Now

- Kubernetes: significant operational overhead, justified only at 10+ microservices or 100k+ daily users
- Multiple datacenters: adds latency complexity, needed only for geographic redundancy requirements
- Active-active database: complex, use active-passive (primary + replica) until you need write scalability
