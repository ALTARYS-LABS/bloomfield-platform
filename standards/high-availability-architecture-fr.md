# Architecture Haute Disponibilité — Bloomfield Terminal
## Sur Hostinger VPS + Coolify

> Destinataires : responsables techniques et interlocuteurs client. Couvre l'architecture nécessaire pour atteindre la tolérance aux pannes, les déploiements sans interruption et la mise à l'échelle horizontale.

---

## Qu'est-ce que la Haute Disponibilité ?

La **Haute Disponibilité (HA)** signifie que le système continue de fonctionner correctement même en cas de défaillance de composants individuels. Elle se mesure en pourcentage de disponibilité :

| SLA | Indisponibilité par an | Indisponibilité par mois |
|---|---|---|
| 99% | 87 heures | 7,2 heures |
| 99,9% ("trois neuf") | 8,7 heures | 43 minutes |
| 99,99% ("quatre neuf") | 52 minutes | 4,3 minutes |
| 99,999% ("cinq neuf") | 5,2 minutes | 26 secondes |

L'architecture actuelle sur un seul VPS cible environ 99–99,5% (pannes matérielles, redémarrages pour les mises à jour OS, interruptions lors des déploiements). Une architecture HA correcte vise 99,9%+.

---

## Architecture Actuelle (VPS Unique)

Voici ce que nous faisons tourner aujourd'hui — simple, économique pour un prototype, mais avec des points de défaillance uniques.

```
                    INTERNET
                       │
                       ▼
             ┌─────────────────┐
             │   Cloudflare    │
             │  (DNS uniquement│
             │   nuage gris)   │
             └────────┬────────┘
                      │
                      ▼
             ┌─────────────────┐
             │  Hostinger VPS  │  ← POINT DE DÉFAILLANCE UNIQUE
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
             │  │PostgreSQL │  │  ← POINT DE DÉFAILLANCE UNIQUE
             │  │ + Keycloak│  │
             │  └───────────┘  │
             └─────────────────┘
```

**Points de défaillance uniques :**
- Le VPS tombe → tout est indisponible
- PostgreSQL plante → le backend est indisponible
- Déploiement → brève interruption (redémarrage du conteneur)

---

## Architecture HA Cible

L'architecture suivante élimine les points de défaillance uniques grâce à deux nœuds VPS Hostinger, une base de données managée ou répliquée, et un load balancer.

```
                         INTERNET
                            │
                            ▼
                 ┌─────────────────────┐
                 │     Cloudflare      │
                 │  (nuage orange ON)  │  ← Protection DDoS, CDN
                 │  Load balancing DNS │     cache des assets statiques
                 └──────────┬──────────┘
                            │
               ┌────────────┴────────────┐
               │                         │
               ▼                         ▼
    ┌──────────────────┐      ┌──────────────────┐
    │  Hostinger VPS 1 │      │  Hostinger VPS 2 │
    │   (principal)    │      │    (réplique)    │
    │                  │      │                  │
    │  ┌────────────┐  │      │  ┌────────────┐  │
    │  │  Traefik   │  │      │  │  Traefik   │  │
    │  └─────┬──────┘  │      │  └─────┬──────┘  │
    │        │         │      │        │         │
    │  ┌─────┴──────┐  │      │  ┌─────┴──────┐  │
    │  │Frontend x2 │  │      │  │Frontend x2 │  │
    │  │ (répliques)│  │      │  │ (répliques)│  │
    │  └─────┬──────┘  │      │  └─────┬──────┘  │
    │        │         │      │        │         │
    │  ┌─────┴──────┐  │      │  ┌─────┴──────┐  │
    │  │ Backend x2 │  │      │  │ Backend x2 │  │
    │  │ (répliques)│  │      │  │ (répliques)│  │
    │  └────────────┘  │      │  └────────────┘  │
    └──────────────────┘      └──────────────────┘
               │                         │
               └────────────┬────────────┘
                            │
                            ▼
              ┌─────────────────────────┐
              │     Couche Base de      │
              │         Données         │
              │                         │
              │  ┌────────┐ ┌────────┐  │
              │  │Postgres│→│Postgres│  │  Primaire → Réplique
              │  │Primaire│ │Réplique│  │  (réplication en streaming)
              │  └────────┘ └────────┘  │
              │                         │
              │  ┌──────────────────┐   │
              │  │    Keycloak      │   │  (clusterisé ou managé)
              │  └──────────────────┘   │
              │                         │
              │  ┌──────────────────┐   │
              │  │  Redis (cache +  │   │  (partage de sessions entre
              │  │  session store)  │   │   les répliques backend)
              │  └──────────────────┘   │
              └─────────────────────────┘
```

---

## Composant par Composant

### Couche 1 — Cloudflare (Load Balancing + CDN)

**Rôle** : Point d'entrée global. Distribue le trafic entre VPS 1 et VPS 2. Met en cache les assets statiques du frontend sur les serveurs edge de Cloudflare (bundles JS React, CSS, images).

**Nuage orange activé** pour la HA : avec deux nœuds VPS, Cloudflare proxifie le trafic. Il vérifie la santé des deux nœuds et arrête automatiquement d'envoyer du trafic vers un nœud tombé. C'est la fonctionnalité **Load Balancing** de Cloudflare (payante, ~5€/mois).

Sans load balancing payant : utilisez le failover DNS Cloudflare (deux enregistrements A sur le même domaine — Cloudflare fait du round-robin, pas un vrai basculement basé sur des health checks).

**Cache des assets statiques** : le frontend React ne change qu'au déploiement. Cloudflare peut mettre en cache tous les bundles JS/CSS sur ses serveurs edge — les utilisateurs à Paris, Abidjan, Montréal reçoivent le frontend du PoP Cloudflare le plus proche en quelques millisecondes.

### Couche 2 — Nœuds VPS (Serveurs Applicatifs)

Deux VPS Hostinger faisant tourner des stacks identiques (Coolify + Traefik + conteneurs applicatifs). Les deux nœuds tournent en permanence.

**Multi-serveur Coolify** : Coolify supporte l'ajout de plusieurs serveurs sur une seule instance. Vous déployez sur les deux serveurs depuis un seul dashboard Coolify.

**Pourquoi deux répliques de frontend et backend par nœud ?**
- Permet les **déploiements sans interruption** : Traefik draine un conteneur, déploie la nouvelle image, la redémarre, puis passe au suivant
- Absorbe les pics de trafic sur un seul nœud sans saturation

**Specs VPS Hostinger pour ce setup :**
- VPS 1 (principal) : plan KVM 2 minimum (2 vCPU, 8 Go RAM)
- VPS 2 (réplique) : même spec
- Les deux dans la même région datacenter pour des connexions DB à faible latence

### Couche 3 — Base de Données (PostgreSQL Primaire/Réplique)

La base de données est la couche la plus difficile à rendre hautement disponible.

**Option A — Réplication en Streaming PostgreSQL (auto-hébergé)**

Un second serveur PostgreSQL sur un VPS séparé (ou un VPS DB dédié) reçoit en temps réel le flux de tous les écritures depuis le primaire.

```
Backend → PostgreSQL Primaire (lectures + écritures)
                │
                │ WAL streaming (Write-Ahead Log)
                ▼
          PostgreSQL Réplique (lectures uniquement — pour les rapports, ou basculement automatique)
```

Si le primaire tombe, on promeut la réplique en primaire (manuellement ou automatiquement via Patroni). Temps de récupération : 30–120 secondes.

**Option B — PostgreSQL Managé (recommandé)**

Utilisez Hostinger Managed Database ou Supabase (PostgreSQL hébergé avec HA automatique, sauvegardes, basculement). Plus coûteux mais élimine la complexité opérationnelle. Recommandé pour une équipe focalisée sur le produit, pas l'infrastructure.

**Pourquoi Redis est nécessaire en HA ?**

Quand vous faites tourner plusieurs instances backend (répliques), chacune a sa propre mémoire. Si la connexion WebSocket ou la session d'un utilisateur atterrit sur Backend A, et que la requête suivante va vers Backend B — la session est perdue.

Redis joue le rôle de **store externe partagé** pour :
- Les données de session HTTP
- L'état des connexions WebSocket (si les sticky sessions ne sont pas viables)
- Le cache (données de marché, config tenant fréquemment lue)

Toutes les répliques backend lisent/écrivent dans le même Redis → l'état est partagé.

### Déploiements Sans Interruption

Avec un seul conteneur, déployer signifie : arrêt → pull de la nouvelle image → démarrage → interruption (~5 secondes).

Avec des répliques et Traefik :
```
Déploiement d'une nouvelle version :

1. Traefik arrête de router vers Backend-A réplique 1
2. Backend-A réplique 1 est arrêté et mis à jour
3. Backend-A réplique 1 redémarre (health check passé)
4. Traefik reprend le routage vers Backend-A réplique 1
5. Répéter pour Backend-A réplique 2
→ Zéro interruption. Les utilisateurs ne remarquent rien.
```

C'est ce qu'on appelle un **déploiement en roulement (rolling deployment)**. Coolify le supporte nativement quand vous configurez plusieurs répliques.

---

## Chemin de Migration — De l'Architecture Actuelle vers la HA

Inutile de tout implémenter d'un coup. Voici la progression incrémentale :

### Phase 0 — Actuel (prototype)
VPS unique, conteneurs uniques, ~99% de disponibilité. Adapté au développement et aux premiers utilisateurs.

### Phase 1 — Résilience de la base de données (~50€/mois supplémentaires)
Ajouter un PostgreSQL managé avec basculement automatique. Élimine le plus grand point de défaillance unique sans changer la stack applicative. À faire avant le premier client payant.

### Phase 2 — Répliques sur VPS unique (~sans coût supplémentaire)
Faire tourner 2 répliques de frontend et backend sur le VPS existant via Coolify. Permet les déploiements sans interruption. Ajouter Redis pour le partage de sessions.

### Phase 3 — Second VPS (~40€/mois supplémentaires)
Ajouter un second VPS Hostinger à Coolify. Déployer les conteneurs applicatifs sur les deux. Configurer Cloudflare DNS avec deux enregistrements A (ou load balancing payant). Le système survit désormais à une panne VPS complète.

### Phase 4 — CDN + Protection DDoS
Activer le nuage orange Cloudflare pour les assets statiques. Ajouter du rate limiting. Adapté quand vous avez des clients qui s'intéressent aux SLA de disponibilité.

---

## Estimation des Coûts (Hostinger, 2026)

| Composant | Coût/mois |
|---|---|
| VPS 1 — KVM 2 (2 vCPU, 8 Go) | ~14€ |
| VPS 2 — KVM 2 (2 vCPU, 8 Go) | ~14€ |
| PostgreSQL Managé (Hostinger) | ~20–40€ |
| Redis (petite instance ou sur VPS) | 0–10€ |
| Cloudflare Load Balancing (optionnel) | ~5€ |
| **Total (HA Phase 3)** | **~55–85€/mois** |

Comparaison : un setup HA équivalent sur AWS/GCP coûte 200–500€/mois. Le VPS Hostinger offre un rapport qualité/prix exceptionnel pour les produits en phase early-stage.

---

## Ce Qu'on Dit au Client

Pour expliquer l'architecture à un client, utilisez ce langage :

> « Le Bloomfield Terminal tourne sur une infrastructure multi-nœuds avec basculement automatique. Vos données sont stockées dans une base de données managée avec réplication en temps réel — si le serveur de base de données principal tombe, une réplique prend le relai automatiquement en quelques secondes. Le trafic applicatif est distribué sur deux serveurs indépendants : si l'un tombe, l'autre continue à traiter toutes les requêtes sans interruption. Les déploiements sont en roulement — les nouvelles versions sont mises en production sans aucune interruption de service. Nous visons 99,9% de disponibilité, soit moins de 45 minutes d'indisponibilité potentielle par mois, et en pratique zéro. »

---

## Ce Dont Nous N'Avons Pas Besoin Maintenant

- **Kubernetes** : charge opérationnelle importante, justifiée seulement à partir de 10+ microservices ou 100k+ utilisateurs quotidiens
- **Multi-datacenter** : ajoute de la complexité de latence, nécessaire uniquement pour des exigences de redondance géographique
- **Base de données active-active** : complexe, utilisez actif-passif (primaire + réplique) jusqu'à avoir besoin de scalabilité en écriture
