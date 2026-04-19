# Bloomfield Terminal — Prototype Module 1

> **AO_BI_2026_001 — Groupement IBEMS · ALTARYS LABS**

---

## Présentation du projet

Bloomfield Terminal est un prototype fonctionnel du **Module 1 : Opérations boursières en temps réel** développé dans le cadre de l'appel d'offres AO_BI_2026_001 de Bloomfield Intelligence.

L'objectif est de démontrer la capacité du groupement à concevoir et livrer une plateforme de suivi des marchés financiers de la **BRVM (Bourse Régionale des Valeurs Mobilières)** : cotations en temps réel, graphiques de cours, carnet d'ordres et indices de marché, accessibles depuis n'importe quel navigateur.

Ce prototype tourne sur des **données simulées** — aucun flux de données réel n'est connecté à ce stade. Les cours évoluent selon un moteur de simulation dont la mécanique respecte les conventions de cotation des marchés financiers (voir section suivante). L'architecture et les interfaces sont représentatives de ce que serait le système en production avec un flux BRVM live.

---

## Règles de cotation appliquées

Même en simulation, chaque calcul respecte les conventions en vigueur sur les marchés boursiers. Voici les règles implémentées :

### Cotation et variation intraday

La **variation** d'un titre est toujours calculée par rapport au **cours d'ouverture** de la séance :

```
Variation (FCFA) = Cours actuel − Cours d'ouverture
Variation (%)    = Variation / Cours d'ouverture × 100
```

Le **plus haut** et le **plus bas** de séance sont maintenus en continu : le plus haut est mis à jour chaque fois que le cours dépasse le précédent sommet, le plus bas chaque fois qu'il descend sous le précédent plancher.

### Historique des cours — chandeliers japonais

Les données historiques (30 jours glissants) suivent la construction standard des **chandeliers japonais (OHLC)** :

- **Open** : premier cours de la séance
- **Close** : dernier cours de la séance
- **High** : `max(Open, Close) × (1 + mèche haute)` — le plus haut est toujours supérieur ou égal au maximum entre l'ouverture et la clôture
- **Low** : `min(Open, Close) × (1 − mèche basse)` — le plus bas est toujours inférieur ou égal au minimum entre l'ouverture et la clôture

Cette contrainte fondamentale (`High ≥ max(Open, Close)` et `Low ≤ min(Open, Close)`) est appliquée algorithmiquement à chaque bougie générée.

### Carnet d'ordres (Order Book)

Le carnet d'ordres affiche 5 niveaux de prix côté achat (**Bid**) et 5 niveaux côté vente (**Ask**). Les prix sont construits symétriquement autour du dernier cours :

```
Bid niveau i = Cours − (Cours × 0,1% × i)
Ask niveau i = Cours + (Cours × 0,1% × i)
```

Les **bids** sont toujours inférieurs au cours, les **asks** toujours supérieurs. L'écart (spread) s'élargit avec la profondeur du carnet — comportement conforme à la microstructure réelle des marchés peu liquides comme la BRVM.

### Indices BRVM

La variation des indices est calculée par rapport à la **valeur de base de référence** :

```
Variation indice (pts) = Valeur actuelle − Valeur de base
Variation indice (%)   = Variation / Valeur de base × 100
```

### Précision des calculs financiers

Tous les calculs monétaires utilisent des types à précision arbitraire (`BigDecimal` en Java) avec arrondi `HALF_UP`. Aucun calcul financier n'utilise de nombre flottant (`float` ou `double`) — conformément aux bonnes pratiques pour les systèmes financiers.

---

## Technologies utilisées

### Backend

| Composant | Choix | Justification |
|-----------|-------|---------------|
| Langage | Java 25 | Dernière LTS, Records natifs, performance |
| Framework | Spring Boot 4.0.3 | Standard de l'industrie, Spring Framework 7 / Jakarta EE 11 |
| Temps réel | WebSocket STOMP | Protocole pub/sub léger, reconnexion native côté client |
| Build | Gradle (Kotlin DSL) | Build reproductible, cache incrémental |
| Qualité | Spotless (Google Java Format) | Format de code uniforme et automatisé |

Le backend expose :
- Un endpoint WebSocket STOMP (`/ws`) publiant les cotations, le carnet d'ordres et les indices à intervalles réguliers
- Une API REST (`/api/brvm/history/{ticker}`) retournant 30 jours d'historique OHLCV

### Frontend

| Composant | Choix | Justification |
|-----------|-------|---------------|
| Framework | React 19 + TypeScript | Typage strict, composants réactifs |
| Graphiques | lightweight-charts (TradingView) | Bibliothèque de référence pour les graphiques financiers |
| Layout | react-grid-layout | Widgets repositionnables et redimensionnables |
| Styles | Tailwind CSS 4 | Design system utilitaire, cohérence visuelle |
| WebSocket | @stomp/stompjs + SockJS | Compatible STOMP, fallback HTTP |
| Build | Vite 8 + pnpm | Build ultra-rapide, dépendances strictes |

### Infrastructure

| Composant | Choix |
|-----------|-------|
| Conteneurisation | Docker (multi-stage build) |
| Serveur web | Nginx (sert le frontend, proxy vers le backend) |
| Orchestration | Docker Compose |
| CI/CD | GitHub Actions (lint, tests, build, images Docker) |
| Hébergement | Hetzner Cloud via Coolify |

---

## Lancer le projet localement

### Prérequis

- [Docker](https://www.docker.com/) et Docker Compose installés
- Ports 3000 et 8080 disponibles

### Avec Docker Compose (recommandé)

```bash
git clone <url-du-repo>
cd bloomfield

docker compose up --build
```

- Terminal : [http://localhost:3000](http://localhost:3000)
- API backend : [http://localhost:8080](http://localhost:8080)

### En développement (sans Docker)

Pour le développement quotidien, on lance uniquement l'infrastructure (PostgreSQL/TimescaleDB) dans Docker, puis backend et frontend nativement pour profiter du hot-reload.

**Infrastructure seule** — postgres uniquement :

```bash
docker compose up -d postgres
```

**Backend** — Java 25 requis :

```bash
cd backend
./gradlew bootRun
```

Le backend démarre sur le port 8080.

**Frontend** — Node 22+ et pnpm requis :

```bash
cd frontend
pnpm install
pnpm dev
```

Le frontend démarre sur [http://localhost:5173](http://localhost:5173) avec proxy automatique vers le backend.

### Vérification rapide

```bash
# Santé du backend
curl http://localhost:8080/api/health

# Historique d'un titre
curl http://localhost:8080/api/brvm/history/SGBCI
```

---

*Groupement IBEMS — ALTARYS LABS · AO_BI_2026_001 · Prototype — données simulées*
