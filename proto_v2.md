## Bloomfield Terminal — Maquette évoluée v2

### Contexte business
Nous avons répondu à l'AO Bloomfield Intelligence (AO_BI_2026_001) pour la réalisation
de Bloomfield Terminal — plateforme d'information financière professionnelle sur les
marchés africains (BRVM). Une première maquette de la maquette, dont le code source réside dans le répertoire actuel a déjà été livrée au client.
Il s'agit de l'affichage multi-écran responsive (adapté aux écrans mobiles) des cours BRVM en temps réel (Java/Spring Boot/STOMP + React 19).

Objectif de ce nouveau développement : faire la v2 de la précédente.
1. Soutenance si shortlisté — démonstration live impressionnante
2. Socle technique réutilisable en production si marché remporté
3. Knowledge base pour l'équipe de développement ALTARYS

---

### Stack imposée  - la même que la stack actuelle
- **Backend** : Java 25 / Spring Boot 4 / Spring Modulith / Spring Data JDBC (pas JPA)
- **Frontend** : React 19 / TypeScript / ShadCN UI / Tailwind CSS
- **Temps réel** : WebSocket / STOMP (déjà fonctionnel sur la v1)
- **BDD** : PostgreSQL + TimescaleDB (séries temporelles) + Redis (cache + pub/sub)
- **Auth** : JWT / Spring Security
- **DevOps** : Docker Compose (dev local) / GitHub Actions

---

### Contraintes architecturales non négociables
- **Spring Modulith** : boundaries définies dès le départ, zéro appel direct inter-modules
- **Spring Data JPA** 
- **RBAC** : rôles ADMIN / ANALYST / VIEWER — contrôle d'accès par annotation Spring Security
- **BigDecimal** pour tous les montants et cours financiers — jamais double/float
- **TestContainers** pour les tests de repository
- **Flyway** pour toutes les migrations SQL — pas de ddl-auto

---

### Modules à implémenter (par priorité)

**PRIORITÉ 1 — Soutenance (3-4 semaines)**
1. Auth : inscription/login JWT, rôles ADMIN/ANALYST/VIEWER, refresh token
2. Cours BRVM live : amélioration de l'existant, multi-instruments, filtres secteur/type
3. Dashboard portefeuille : positions, valorisation temps réel, P&L (données simulées OK)
4. Alertes cours : seuils configurables par instrument, notifications WebSocket

**PRIORITÉ 2 — Socle production**
5. TimescaleDB : ingestion historique OHLCV, API graphique (1j/1s/1m/1an)
6. Spring Modulith boundaries posées et testées avec ArchUnit

---

### Données de marché BRVM
- Pas d'API officielle BRVM disponible — utiliser un **simulateur réaliste**
- Simulateur doit produire : ticker, cours, variation %, volume, timestamp
- Les ~45 titres côtés BRVM doivent être présents
- Architecture : le simulateur est un **composant remplaçable** par un vrai feed en production
- Pattern imposé : interface `MarketDataProvider` avec impl `SimulatedMarketDataProvider`
- La négociation d'un accès API officiel BRVM est prévue en Phase 0 du projet réel

---

### Structure des modules Spring Modulith attendue

com.bloomfield.terminal
├── user/             # auth, rôles, JWT, Spring Security
├── market-data/      # cours, historique OHLCV, simulateur
├── portfolio/        # portefeuilles, positions, P&L
├── alerts/           # règles, déclenchement, notifications WS
└── shared-kernel/    # ValueObjects, events, exceptions communes

---

### Modèle utilisateur

Bloomfield Terminal (mono-instance)
└── Users
├── ADMIN     → gestion utilisateurs, configuration plateforme
├── ANALYST   → accès complet données, création watchlists/alertes
└── VIEWER    → lecture seule, accès limité selon abonnement

---

### Ce que j'attends en premier

1. **Analyse et questions** si des choix architecturaux nécessitent arbitrage
2. **Structure du projet** : arborescence complète Maven multi-module + Spring Modulith
3. **Docker Compose** : PostgreSQL + TimescaleDB + Redis + app backend
4. **Module user** : entités User/Role, Spring Security JWT, FilterChain, endpoints auth
5. **Module market-data** : interface MarketDataProvider + SimulatedMarketDataProvider,
   broadcast WebSocket STOMP, schéma TimescaleDB hypertable OHLCV
6. **Tests** : TestContainers sur chaque repository, ArchUnit sur les boundaries Modulith

## Questions
1. je me demande si j'ai vraiment besoin de redis à ce stade
2. je crois que je n'ai pas besoin ArchUnit car avec SpringModulith, le test suivant permets de véfier que l'architecture modulaire est respectée :
```java
var modules = ApplicationModules.of(TerminalApplication.class);
    modules.verify(); 
````

---

### Conventions de livraison
- Code complet et compilable — pas de pseudo-code
- Chaque classe avec ses imports
- Migrations SQL via Flyway (pas de ddl-auto)
- README de démarrage : `docker compose up` puis `./mvnw spring-boot:run`
  → application fonctionnelle en moins de 5 minutes

## Avant tout
- Au regard de ce qui a déja été fait, que penses-tu honnêtement de cette nouvelle demande ?
- Vois-tu des incohérences ?
- Agis comme mon conseiller stratégique et comme l'architecte logiciel en chef de ma société ALTARYS LABS.
- Corrige-moi si j'ai tord.
- Sois pédagogue.
- Dans toute cette session propose-moi toujours un plan que je validerai avant toute action
