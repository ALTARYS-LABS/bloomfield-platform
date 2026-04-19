# Bloomfield Terminal v2 — Demo Script (8 minutes)

> Audience: RFP jury (AO_BI_2026_001, Bloomfield Intelligence). Language of speech: French. UI labels already in French. Data is simulated end-to-end.

Prerequisites:
- `SPRING_PROFILES_ACTIVE=demo` is active on the target environment (local or staging).
- Seeded accounts are reachable:
  - ADMIN `admin@altaryslabs.com` / `ChangeMe!Admin2026`
  - ANALYST `analyst@demo.bloomfield` / `ChangeMe!Analyst2026`
  - VIEWER `viewer@demo.bloomfield` / `ChangeMe!Viewer2026`
- Two browser windows open side by side (ANALYST on the left, ADMIN prepped on the right).
- Network tab closed to avoid distractions. Audio/video rehearsed.

---

## T+0:00 — Opening (30s)

Open landing page. Say:

> « Bloomfield Terminal, Module 1 — Opérations boursières BRVM en temps réel. Je vais vous montrer le parcours analyste puis la console administrateur, sur architecture Spring Modulith. Les données affichées sont simulées mais respectent les conventions de cotation réelles. »

Click **Se connecter** as ANALYST.

**Fallback** if login fails: « Le backend est resté sur le profil demo, donc les comptes sont pré-créés par un ApplicationRunner idempotent. »

## T+0:30 — Terminal (2:00)

Wait ~5 seconds for the WebSocket to connect. Highlight:

1. **Cotations live** — price ticker at top scrolling, 45 BRVM securities.
2. **Indices** — BRVM Composite and BRVM 10 with variation vs. base.
3. **Carnet d'ordres** — 5 levels bid / 5 levels ask, symmetric around last price.
4. **Graphique** — 30 jours OHLC (chandeliers japonais), contrainte `High ≥ max(Open, Close)` respectée.

Say:

> « Chaque tick arrive en WebSocket STOMP. Les bougies historiques sortent d'une hypertable TimescaleDB, et l'agrégation temps réel est faite côté service dans un bean Spring. »

**Fallback** if the feed stalls: « Le simulateur tourne sur un pas d'une seconde, je vais attendre un tick de plus — l'architecture se branchera identiquement sur un flux BRVM live via l'interface `MarketDataProvider`. »

## T+2:30 — Portfolio (1:30)

Click **Portfolio** tab. Highlight:

1. Six positions (SNTS, BOAC, SGBC, ONTBF, PALC, SOGC) diversifiées Finance / Télécoms / Agriculture.
2. Valorisation live (quantity × prix courant).
3. Couleurs vert / rouge du P&L latent, total en bas.
4. Soumettre un trade d'achat sur SGBC (10 unités) pour montrer l'update instantanée du coût moyen pondéré et du ledger.

Say:

> « P&L latent recalculé côté serveur à chaque tick, pas de double précision — tout est en `BigDecimal`. Le module `portfolio` écoute `QuoteTick` via Spring Modulith. »

**Fallback** si le trade échoue: « Le simulateur ne connaît pas ce ticker sur cette session, je passe au volet alertes. »

## T+4:00 — Alerts (2:00)

Click **Alerts** tab. Point to the three pré-seedées:

- `BOAC ABOVE 5215.00`
- `PALC BELOW 4885.00`
- `SNTS CROSSES_UP 18920.00`

Wait for a toast to pop (typically ≤ 2 min avec la dérive ±0,3 %). Quand ça tombe:

1. Toast en haut à droite + icône cloche qui s'incrémente.
2. Ouvrir le slide-over pour montrer l'event (prix déclencheur, timestamp).
3. Montrer que la règle est passée à `enabled=false` (one-shot).

Say:

> « L'`AlertEngine` écoute `QuoteTick` via `@ApplicationModuleListener` — transaction séparée, persistance des événements manqués dans `event_publication`. L'UI reçoit la notification par STOMP, topic dédié utilisateur. »

**Fallback** si aucun déclenchement: créer à la volée une règle `BOAC ABOVE <prix_courant - 1>` — déclenche au tick suivant.

## T+6:00 — Admin & Architecture (1:30)

Basculer dans la fenêtre ADMIN déjà loguée. Ouvrir **Administration**:

1. Liste des trois utilisateurs démo, rôles, statut enabled.
2. Désactiver le VIEWER en un clic (toggle) — l'API répond 204.

Ouvrir un nouvel onglet sur `http://<host>/actuator/modulith`:

> « C'est la carte vivante des modules Spring Modulith. On y voit les dépendances autorisées entre `user`, `portfolio`, `alerts`, `marketdata`, et les événements qui circulent. La structure du code est validée à chaque build par `ApplicationModulesTest`. »

**Fallback** si le endpoint ne répond pas: « L'actuator modulith est expose par `spring-modulith-actuator` mais peut être désactivé en prod ; la vue est identique dans le rapport de tests `ModuleStructureReport`. »

## T+7:30 — Clôture (0:30)

Résumer:

> « Ce que vous avez vu tient dans ~200 lignes de code ajoutées pour la release v2 : seed démo idempotent, durcissement prod (CORS strict, cookies `HttpOnly`/`Secure`/`SameSite=Strict`, pool Hikari borné), actuator modulith. Le reste est construit sur sept stories livrées depuis janvier. Le code est prêt à brancher un flux BRVM live via l'interface `MarketDataProvider` — la couche présentation n'en saura rien. »

Questions.

---

## Timings cheat sheet

| Temps   | Section              | Marqueur clé                                |
|---------|----------------------|---------------------------------------------|
| 0:00    | Opening + login      | Terminal chargé                             |
| 0:30    | Terminal             | 4 widgets expliqués                         |
| 2:30    | Portfolio            | Trade BUY sur SGBC soumis                   |
| 4:00    | Alerts               | Attente toast + slide-over                  |
| 6:00    | Admin + modulith     | Actuator modulith ouvert                    |
| 7:30    | Clôture + questions  | Résumé 3 phrases                            |

## Ce qui casse la démo et comment rattraper

| Symptôme                              | Cause probable                  | Phrase de secours                                                                    |
|---------------------------------------|---------------------------------|---------------------------------------------------------------------------------------|
| Page blanche / 502                    | Backend redémarre               | « On est sur Coolify, redéploiement en moins d'une minute, je rebranche. »           |
| WebSocket déconnecté                  | Token expiré / DNS              | « L'access token est de 15 minutes, je recharge, le refresh cookie relance la session. » |
| Aucune alerte en 2 min                | Malchance sur la marche aléatoire| Créer règle `BOAC ABOVE <prix-1>` en direct.                                         |
| Modulith 404                          | Profil différent / dep manquante| « Sur ce build l'actuator n'est pas exposé, je remets la carte tirée du rapport de tests. » |
