# Tutoriel Configuration Multi-Environnements
## De Cloudflare à Coolify en passant par GitHub — Le Guide Complet

> Rédigé pour ALTARYS LABS. Réutilisable pour tout futur projet déployant une application Spring Boot + React sur un VPS via Coolify.

---

## Pourquoi Cette Configuration Existe

Faire tourner un seul environnement de production est un piège : chaque changement non testé est mis en ligne immédiatement. Quand vous avez des clients, cela signifie des bugs en production sans filet de sécurité.

L'objectif de cette configuration est un **pipeline à deux environnements** :

```
Poste du développeur
      │
      ▼
branche feature (courte durée, 1-2 jours)
      │
      ▼  PR + review
origin/develop ──► auto-déploiement ──► STAGING  (tests internes)
      │
      ▼  PR + validation (événement de release)
origin/main ──────► auto-déploiement ──► PRODUCTION  (clients)
```

**Pourquoi `develop` comme staging ?** Parce que le staging doit toujours refléter ce qui partira en production. `develop` est la branche d'intégration — toutes les fonctionnalités testées s'y accumulent avant d'être promues sur `main`.

---

## Partie 1 — La Couche Applicative

### 1.1 Les Profils Spring Boot

Spring Boot supporte une configuration spécifique à chaque environnement via les profils. Un profil est activé au runtime avec `SPRING_PROFILES_ACTIVE=staging`, ce qui amène Spring à charger `application-staging.yml` par-dessus le `application.yml` de base.

**Pourquoi c'est important pour le CORS** : le CORS oblige le backend à lister explicitement les origines autorisées. Si vous codez en dur `https://votre-app.com` dans le code ou un fichier de config partagé, votre backend de staging rejettera les requêtes venant de `https://staging-votre-app.com`. Les profils règlent ça proprement.

```
src/main/resources/
├── application.yml           ← valeurs partagées (config DB, JWT, etc.)
├── application-prod.yml      ← surcharges production (origine CORS prod)
└── application-staging.yml   ← surcharges staging (origine CORS staging)
```

**`application-staging.yml`** :
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

**Pourquoi `@ConfigurationProperties` est le bon pattern** :

```java
@ConfigurationProperties(prefix = "app.cors")
record CorsProperties(List<String> allowedOrigins) {}
```

Ce record lit `app.cors.allowed-origins` depuis le profil actif. Aucun `if (profile == "staging")` dans le code. Le YAML est la source de vérité unique pour les valeurs spécifiques à chaque environnement. C'est la bonne pratique Spring Boot.

### 1.2 Docker Compose — Un Seul Fichier pour Tous les Environnements

Vous ne voulez pas deux fichiers `docker-compose.yml`. Ils vont diverger. À la place, paramétrez les valeurs qui diffèrent entre environnements avec une substitution de variables d'environnement et des valeurs par défaut :

```yaml
services:
  backend:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}  # défaut : prod
  frontend:
    ports:
      - "${FRONTEND_PORT:-3000}:80"  # défaut : 3000
```

**Syntaxe** : `${NOM_VAR:-valeur_par_defaut}` — utilise la variable d'env si elle est définie, sinon la valeur par défaut.

**Pourquoi des ports séparés ?** Staging et production tournent sur le **même VPS**. Le port hôte (à gauche de `:`) doit être unique par processus. Le port conteneur (à droite) reste 80. Production prend 3000, staging prend 3001. Traefik (le reverse proxy de Coolify) route par nom de domaine, pas par port — les clients ne voient jamais ces numéros de port.

| Environnement | `SPRING_PROFILES_ACTIVE` | `FRONTEND_PORT` |
|---|---|---|
| Production (défaut) | `prod` | `3000` |
| Staging | `staging` | `3001` |

Cette approche signifie que vous ne surchargez que ce qui diffère réellement — tout le reste hérite des valeurs par défaut du fichier compose.

---

## Partie 2 — La Couche Infrastructure (Coolify)

### Qu'est-ce que Coolify ?

Coolify est un PaaS (Platform-as-a-Service) open-source auto-hébergé. Pensez à Heroku, mais qui tourne sur votre propre VPS. Il gère :
- Les builds de conteneurs (Docker Compose ou Dockerfile)
- Le routage reverse proxy (Traefik sous le capot)
- L'émission de certificats TLS (Let's Encrypt via Traefik)
- La gestion des variables d'environnement
- Les webhooks de déploiement
- Le streaming de logs

### 2.1 Le Modèle Mental de Coolify

```
Serveur
└── Projet (ex. "Bloomfield Terminal")
    ├── Environnement : production
    │   └── Ressource : application Docker Compose (branche : main)
    └── Environnement : staging
        └── Ressource : application Docker Compose (branche : develop)
```

**Projet** = regroupement d'apps liées (même repo, environnements différents).
**Environnement** = un contexte de déploiement (production, staging, preview, etc.).
**Ressource** = l'application réellement en cours d'exécution.

### 2.2 Créer l'Environnement de Staging

1. Dans Coolify, ouvrez votre projet → cliquez **"+ New Environment"** → nommez-le `staging`
2. Dans l'environnement staging → **"+ New Resource"** → Docker Compose
3. Pointez vers le même repo GitHub, **branche : `develop`**
4. Chemin du fichier compose : `/docker-compose.yml` (même fichier que la production)

**Définissez les variables d'environnement** pour la ressource staging :
```
SPRING_PROFILES_ACTIVE=staging
FRONTEND_PORT=3001
```

Ces deux variables sont la seule différence entre vos apps Coolify staging et production. Tout le reste vient des valeurs par défaut du fichier compose.

**Définissez le domaine** : `https://staging-bf-terminal.altaryslabs.com`

Traefik demandera automatiquement un certificat Let's Encrypt pour ce domaine une fois le DNS correctement configuré.

### 2.3 Webhooks pour le Déploiement Automatique

Sans webhooks, Coolify ne sait pas quand vous poussez sur `develop`. Vous devez connecter les deux :

**Dans Coolify** (ressource staging → onglet Webhooks ou Deploy) :
- Copiez l'URL du webhook (format : `https://votre-coolify.com/webhooks/deploy/...`)

**Dans GitHub** (repo → Settings → Webhooks → Add webhook) :
- Payload URL : collez l'URL Coolify
- Content type : `application/json`
- Event : "Just the push event"

Coolify filtre par branche de son côté — le webhook staging ne déclenche un déploiement que quand le push est sur `develop`.

Faites la même chose pour la production (push sur `main` → déploiement production). Cela est probablement déjà configuré si la production a été mise en place via l'intégration GitHub de Coolify.

**Flux après configuration** :
```
git push origin develop
       │
       ▼ (webhook)
Coolify reçoit l'événement push
       │
       ▼
Coolify build l'image Docker
       │
       ▼
Coolify remplace les conteneurs en cours
       │
       ▼
Staging est en ligne en ~2 minutes
```

---

## Partie 3 — La Couche DNS (Cloudflare)

### 3.1 Ce que Fait le DNS Ici

Le DNS traduit `staging-bf-terminal.altaryslabs.com` en adresse IP de votre VPS. Sans lui, la demande de certificat Let's Encrypt de Traefik échoue et le navigateur ne trouve pas le serveur.

### 3.2 La Règle du Nuage Gris (Critique)

Cloudflare propose deux modes pour chaque enregistrement DNS :
- **Nuage orange (proxy ON)** : le trafic passe par le CDN de Cloudflare. Cloudflare termine le TLS. Votre serveur origin reçoit l'IP de Cloudflare, pas celle du client.
- **Nuage gris (proxy OFF)** : DNS uniquement. Le trafic va directement sur votre VPS. Votre serveur origin termine le TLS.

**Pour Coolify + Traefik + Let's Encrypt : utilisez le nuage gris.**

Pourquoi ? Le challenge HTTP-01 de Let's Encrypt nécessite une connexion HTTP directe depuis les serveurs de Let's Encrypt vers votre VPS sur le port 80. Quand le proxy Cloudflare est ON, Let's Encrypt frappe Cloudflare au lieu de votre VPS — le challenge échoue — pas de certificat.

Une fois le certificat émis et fonctionnel, vous _pouvez_ passer en nuage orange si vous voulez la protection DDoS de Cloudflare. Mais commencez en gris jusqu'à confirmation que le TLS fonctionne.

### 3.3 Enregistrement DNS à Ajouter

| Type | Nom | Valeur | Proxy |
|---|---|---|---|
| A | `staging-bf-terminal` | `<IP de votre VPS>` | OFF (gris) |

L'enregistrement A de production (`bloomfield-intelligence` ou `@`) existe déjà — le staging a besoin de son propre sous-domaine pointant vers la même IP.

**Pourquoi la même IP ?** Les deux environnements tournent sur le même VPS. Traefik écoute sur les ports 80 et 443 et route le trafic par le header `Host` (nom de domaine). Deux domaines, une IP, deux conteneurs — Traefik s'en occupe.

---

## Partie 4 — La Couche Git

### 4.1 Modèle de Branches

```
main    → toujours déployable en production
develop → toujours déployable en staging, représente la "prochaine release"
```

Les branches feature sont créées depuis `develop`, fusionnées dans `develop` via PR. Quand `develop` est stable et testé, une PR de release le promeut sur `main`.

**Pourquoi pas une seule branche ?** Sans `develop`, chaque branche feature va directement en production. Vous perdez la capacité d'intégrer et tester plusieurs fonctionnalités ensemble avant une release.

### 4.2 Règles de Protection de Branches (GitHub)

Configurez ces règles dans GitHub → Settings du repo → Branches → Add rule :

**Pour `main`** :
- Exiger une pull request avant de merger
- Exiger que les status checks passent (tests CI)
- Ne pas autoriser le contournement (même pour les admins, dans un contexte d'équipe)

**Pour `develop`** :
- Identique à `main`
- Empêche les pushs directs accidentels

Ces règles appliquent le workflow même si quelqu'un oublie.

### 4.3 La PR de Release

Une PR `develop → main` est un événement de release délibéré, pas un merge de routine. Elle doit inclure :
- Résumé de tous les changements depuis la dernière release
- Checklist de vérification (quoi tester en production après le déploiement)
- Plan de rollback (reverter le commit de merge si la prod casse)

Voir `standards/git-workflow.md` pour le template complet de PR de release.

---

## Partie 5 — Peut-on Automatiser Tout Ça avec Terraform ?

**Réponse courte** : Partiellement oui, et ça vaut le coup pour les équipes.

### Ce Qui Peut Être Automatisé

| Composant | Provider Terraform | Ce Que Vous Pouvez Gérer |
|---|---|---|
| Cloudflare DNS | `cloudflare/cloudflare` | Enregistrements A, CNAME, proxy, page rules |
| GitHub | `integrations/github` | Repos, règles de protection de branches, webhooks, permissions d'équipe |
| Coolify | Pas de provider officiel | Limité — Coolify a une API REST mais pas encore de provider Terraform |

### Cloudflare via Terraform

```hcl
resource "cloudflare_record" "staging" {
  zone_id = var.cloudflare_zone_id
  name    = "staging-bf-terminal"
  value   = var.vps_ip
  type    = "A"
  proxied = false  # nuage gris — obligatoire pour Let's Encrypt
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

### Coolify — Le Manque

Coolify n'a pas de provider Terraform (début 2026). Vous devez le configurer via l'interface ou son API REST. Votre configuration Coolify n'est donc pas encore de l'infrastructure-as-code.

Options :
1. **Manuel (approche actuelle)** — acceptable pour une petite équipe, documentez les étapes
2. **API Coolify + scripts shell** — setup scripté, pas idempotent mais reproductible
3. **Migrer vers un provider avec support Terraform** — Railway, Render, ou fly.io ont un meilleur support IaC si ça devient un problème
4. **Utiliser Kubernetes + Helm** — IaC complet pour l'infrastructure, mais complexité opérationnelle nettement plus importante

**Recommandation pour ALTARYS LABS maintenant** : automatisez Cloudflare DNS et les paramètres GitHub avec Terraform (haute valeur, simple). Laissez Coolify en manuel et documentez-le (Coolify est rapide à configurer à la main et ne change pas souvent).

---

## Partie 6 — Quel Rôle Peut Jouer GitHub Actions ?

GitHub Actions peut compléter ou partiellement remplacer l'auto-déploiement de Coolify. Voici le spectre :

### Option A — Webhooks Uniquement (Configuration Actuelle)

```
push sur develop → webhook GitHub → Coolify déploie
```

Simple. Coolify gère tout. GitHub Actions n'est pas impliqué. Fonctionne bien pour les petites équipes.

**Inconvénient** : Coolify déploie à chaque push, même si les tests échouent.

### Option B — GitHub Actions comme Portail CI + Coolify comme CD

```
push sur develop
      │
      ▼
GitHub Actions : lance tests + lint
      │
   OK ? ──NON──► marque le commit en échec, ne déploie PAS
      │OUI
      ▼
déclenche le webhook Coolify via appel API
      │
      ▼
Coolify déploie le staging
```

C'est l'amélioration recommandée. Vous gardez Coolify comme moteur de déploiement mais ne le déclenchez qu'après la validation du CI.

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
    needs: test  # s'exécute uniquement si le job test passe
    runs-on: ubuntu-latest
    steps:
      - name: Trigger Coolify deploy
        run: |
          curl -X POST "${{ secrets.COOLIFY_STAGING_WEBHOOK_URL }}"
```

### Option C — GitHub Actions comme CD Complet (Sans Webhooks Coolify)

GitHub Actions se connecte en SSH sur le VPS et lance `docker compose pull && docker compose up -d`. Plus de contrôle, plus de complexité. À éviter sauf si vous dépassez les capacités de Coolify.

### Option D — GitHub Actions pour Automatiser les Releases

Automatisez les parties rébarbatives d'une release :

```yaml
# .github/workflows/release-pr.yml
# Déclenché manuellement : crée une PR de develop vers main
name: Open Release PR

on:
  workflow_dispatch:  # déclenchement manuel depuis l'interface GitHub

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

Un clic dans l'interface GitHub → la PR de release s'ouvre avec le changelog pré-rempli.

### Résumé : Ce Qu'il Faut Implémenter Maintenant vs. Plus Tard

| Action | Priorité | Effort |
|---|---|---|
| Webhooks Coolify (push → auto-déploiement) | **Maintenant** | Faible |
| Règles de protection de branches GitHub | **Maintenant** | Faible |
| DNS Cloudflare via Terraform | Bientôt | Moyen |
| GitHub Actions comme portail CI avant déploiement | Bientôt | Moyen |
| GitHub Actions pour automatiser les PR de release | Plus tard | Faible |
| Terraform complet pour GitHub + Cloudflare | Plus tard | Moyen |

---

## Annexe — Schéma d'Architecture Complet

```
                          DÉVELOPPEUR
                              │
                    git push origin feat/xyz
                              │
                         REPO GITHUB
                    ┌─────────┴──────────┐
                    │                    │
               PR vers develop      (bloqué : pas de
                    │               push direct)
                    ▼
              origin/develop
                    │
                    │ webhook (événement push)
                    ▼
               GITHUB ACTIONS (portail CI optionnel)
                    │
                    │ tests passent
                    ▼
        COOLIFY (environnement staging)
                    │
                    │ docker compose up -d
                    │ SPRING_PROFILES_ACTIVE=staging
                    │ FRONTEND_PORT=3001
                    ▼
              TRAEFIK (reverse proxy)
                    │
                    │ route par header Host
                    ▼
    staging-bf-terminal.altaryslabs.com
                    │
              DNS CLOUDFLARE
              (enregistrement A, nuage gris)
                    │
                    ▼
                  IP VPS


              ─ ─ ─ ─ ─ ─ ─ ─ RELEASE ─ ─ ─ ─ ─ ─ ─ ─


              origin/develop
                    │
               PR vers main
               (testé, validé)
                    ▼
               origin/main
                    │
                    │ webhook
                    ▼
        COOLIFY (environnement production)
                    │
                    │ docker compose up -d
                    │ SPRING_PROFILES_ACTIVE=prod (défaut)
                    │ FRONTEND_PORT=3000 (défaut)
                    ▼
              TRAEFIK (reverse proxy)
                    │
                    ▼
 bloomfield-intelligence.altaryslabs.com
```
