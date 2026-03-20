# Traefik — Guide Complet pour ALTARYS LABS

> Destinataires : développeurs et responsables techniques qui ont besoin de comprendre comment le trafic circule dans notre infrastructure.

---

## Qu'est-ce qu'un Reverse Proxy ?

Avant de parler de Traefik, il faut comprendre ce que fait un reverse proxy.

Un **reverse proxy** est un serveur qui se place entre internet et vos serveurs applicatifs. Chaque requête entrante frappe d'abord le proxy, qui décide ensuite où la transférer.

```
Internet
   │
   ▼
REVERSE PROXY  ◄── point d'entrée public unique
   │
   ├──► Serveur A (backend)
   ├──► Serveur B (frontend)
   └──► Serveur C (autre service)
```

Pourquoi ne pas exposer son application directement sur internet ?
- Votre application tourne sur un port arbitraire (8080, 3000, etc.) — le proxy normalise ça en 80/443
- Un seul IP, plusieurs applications — le proxy route selon le nom de domaine
- La terminaison TLS en un seul endroit — vos applis parlent HTTP en interne
- Un point unique pour les logs d'accès, le rate limiting, les headers d'authentification

---

## Qu'est-ce que Traefik ?

Traefik est un **reverse proxy et load balancer cloud-native** conçu spécifiquement pour les environnements conteneurisés.

Sa particularité : **il se configure automatiquement** en surveillant votre orchestrateur de conteneurs (Docker, Kubernetes, etc.) et en lisant les labels attachés aux conteneurs. Quand un conteneur démarre, Traefik récupère ses règles de routage immédiatement. Quand il s'arrête, Traefik supprime la route. Aucun rechargement de fichier de configuration nécessaire.

**Créé en** : 2015 par Containous (aujourd'hui Traefik Labs)
**Langage** : Go
**Licence** : MIT (open source)
**Site** : traefik.io

---

## Traefik vs Nginx — La Différence Fondamentale

| | Traefik | Nginx |
|---|---|---|
| **Style de config** | Dynamique — lit les labels Docker / annotations K8s | Statique — fichiers de config que vous écrivez et rechargez |
| **Certificats TLS** | Automatiques via Let's Encrypt (client ACME intégré) | Manuels (ou avec certbot en outil séparé) |
| **Connaissance des conteneurs** | Native — surveille les événements Docker/K8s | Aucune — vous configurez les upstreams par IP/port manuellement |
| **Dashboard** | Interface web intégrée sur `:8080` | Tiers (nginx-status, Amplify) |
| **Performances** | Excellentes | Excellentes (Nginx légèrement plus rapide en débit brut) |
| **Courbe d'apprentissage** | Faible pour les setups Docker | Plus élevée — syntaxe de config spécifique à Nginx |
| **Format de configuration** | TOML, YAML, ou labels Docker | Langage de config Nginx (DSL propre) |
| **Rechargement à chaud** | Automatique (sans redémarrage) | `nginx -s reload` requis |
| **Cas d'usage** | Environnements Docker / Kubernetes | Sites statiques, serveurs traditionnels, proxy à haut débit |

### La différence de philosophie

**Approche Nginx** — vous dites tout à Nginx explicitement :
```nginx
# Vous écrivez ce fichier et rechargez Nginx à chaque nouvel app
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

**Approche Traefik** — vous ajoutez des labels à vos conteneurs et Traefik s'occupe du reste :
```yaml
# docker-compose.yml — Traefik lit ces labels automatiquement
services:
  frontend:
    labels:
      - "traefik.http.routers.staging.rule=Host(`staging-bf-terminal.altaryslabs.com`)"
      - "traefik.http.routers.staging.tls.certresolver=letsencrypt"
```

Coolify génère ces labels pour vous à partir du domaine que vous saisissez dans l'interface. Vous ne les écrivez jamais manuellement.

---

## Les Concepts Clés de Traefik

### Entrypoints (Points d'entrée)
Les ports sur lesquels Traefik écoute.

```
port 80  (HTTP)  → entrypoint "web"
port 443 (HTTPS) → entrypoint "websecure"
```

Tout le trafic entre par l'un de ces deux entrypoints.

### Routers (Routeurs)
Les règles qui associent une requête entrante à un service.

Un router répond à la question : *"Pour ce header Host (et ce chemin optionnel), quel service doit traiter cette requête ?"*

```
Requête : GET https://staging-bf-terminal.altaryslabs.com/api/orders
          │
          ▼
Router :  Host(`staging-bf-terminal.altaryslabs.com`) && PathPrefix(`/api`)
          │
          ▼
Service : conteneur backend sur le port 8080
```

### Services
Les serveurs backend réels vers lesquels Traefik transfère les requêtes.

Un service est un conteneur (ou groupe de conteneurs) avec un port. En mode Docker, Traefik découvre l'IP interne du conteneur automatiquement.

### Middlewares
Des transformations appliquées aux requêtes ou réponses entre le router et le service.

Exemples :
- Redirection HTTP → HTTPS
- Ajout de headers de sécurité (`X-Frame-Options`, `HSTS`)
- Rate limiting
- Authentification basique
- Suppression de préfixe de chemin (`/api` → `/`)

### Certificats (TLS)
Traefik intègre un client ACME. Vous configurez un **certificate resolver** pointant vers Let's Encrypt, et Traefik demande et renouvelle les certificats automatiquement pour chaque domaine qu'il route.

---

## Le Challenge HTTP-01 — Comment Traefik Obtient les Certificats

Let's Encrypt ne délivre pas de certificat sans preuve que vous contrôlez le domaine. Le challenge HTTP-01 est le mécanisme de vérification :

```
1. Traefik demande à Let's Encrypt : "Donne-moi un certificat pour staging-bf-terminal.altaryslabs.com"

2. Let's Encrypt répond : "Prouve que tu contrôles ce domaine.
   Crée un fichier à :
   http://staging-bf-terminal.altaryslabs.com/.well-known/acme-challenge/<token-aléatoire>
   contenant cette valeur : <secret>"

3. Traefik : crée un endpoint HTTP temporaire avec le token (en mémoire, pas de fichier)

4. Let's Encrypt : fait un GET HTTP vers cette URL depuis ses propres serveurs
   → Si la réponse correspond : propriété du domaine prouvée ✓ → certificat délivré
   → Si la réponse échoue : challenge échoué ✗ → pas de certificat

5. Traefik : reçoit le certificat, le stocke, commence à servir en HTTPS
   Le certificat se renouvelle automatiquement 30 jours avant expiration.
```

**Pourquoi le cloud gris Cloudflare est obligatoire :**
Quand le proxy Cloudflare est activé (nuage orange), la requête HTTP de Let's Encrypt arrive sur les serveurs edge de Cloudflare — pas sur votre VPS. Cloudflare ne connaît pas le token ACME → retourne 404 → challenge échoué → pas de certificat. Nuage gris = DNS uniquement, les requêtes HTTP arrivent directement sur votre VPS.

Il existe une alternative — le **challenge DNS-01** — qui ne nécessite pas l'accès au port 80. Il fonctionne en créant un enregistrement DNS TXT pour prouver la propriété du domaine. Cela permettrait d'activer le proxy Cloudflare (protection DDoS) mais nécessite de donner à Traefik un accès API à votre compte Cloudflare. Plus complexe, mais envisageable.

---

## Comment Traefik Fonctionne dans Notre Setup

```
                    COOLIFY
                       │
                       │ génère les labels Docker
                       ▼
              docker-compose.yml
         ┌─────────────┴────────────┐
         │                          │
   ressource prod               ressource staging
   (branche: main)              (branche: develop)
   domaine: bloomfield-...      domaine: staging-bf-...
         │                          │
         ▼                          ▼
   frontend:3000              frontend:3001
   backend:8080               backend:8081
         │                          │
         └──────────┬───────────────┘
                    ▼
                TRAEFIK
         (Coolify l'installe,
          vous ne le touchez jamais directement)
                    │
          ┌─────────┴──────────┐
          │                    │
       port 80              port 443
    (redirection 443)    (terminaison TLS)
          │                    │
          └─────────┬──────────┘
                    │
               INTERNET
```

Coolify fait tourner Traefik comme conteneur système sur votre VPS. Quand vous créez une nouvelle application et définissez son domaine dans Coolify, Coolify :
1. Injecte les labels Traefik dans le conteneur
2. Traefik détecte le nouveau conteneur en quelques secondes
3. Traefik demande un certificat Let's Encrypt pour le domaine
4. Le HTTPS est opérationnel

Vous ne configurez rien dans Traefik directement. L'interface Coolify est la couche d'abstraction.

---

## Dashboard Traefik

Traefik expose un dashboard intégré sur le port `8080` de votre VPS (généralement protégé par Coolify). Il affiche :
- Tous les routers, services et middlewares actifs
- Le statut des certificats par domaine
- Le nombre de requêtes et les erreurs
- Quel conteneur chaque route cible

Utile pour déboguer les problèmes de routage ou les échecs de certificats.

---

## Quand Choisir Nginx Plutôt que Traefik

| Scénario | Meilleur choix |
|---|---|
| Applications conteneurisées sur Docker/Kubernetes | **Traefik** |
| Site statique sur une VM bare metal | **Nginx** |
| API gateway à haut débit (100k+ req/s) | Nginx (marginalement plus rapide) |
| Règles de cache complexes (assets, intégration CDN) | Nginx |
| L'équipe a déjà une expertise Nginx | Les deux — les compétences comptent |
| Coolify gère votre infrastructure | **Traefik** (il est intégré) |

Pour ALTARYS LABS sur Coolify : Traefik est le bon choix et est déjà en place. Il n'y a aucune raison d'introduire Nginx.

---

## Référence Rapide

| Concept | Ce qu'il fait |
|---|---|
| Entrypoint | Port sur lequel Traefik écoute (80, 443) |
| Router | Associe une requête par Host/chemin → envoie au service |
| Service | Le conteneur backend qui reçoit la requête |
| Middleware | Transforme la requête/réponse (redirection, auth, headers) |
| Certificate resolver | Gestion automatique des certificats Let's Encrypt |
| Challenge HTTP-01 | Vérification du domaine par Let's Encrypt via HTTP |
| Labels Docker | Comment Traefik découvre la config de routage depuis les conteneurs |
