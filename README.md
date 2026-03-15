# Bloomfield Terminal — Prototype Module 1

Plateforme de suivi des opérations boursières BRVM en temps réel.

## Lancement rapide

### Prérequis
- Docker & Docker Compose

### Avec Docker Compose
```bash
docker-compose up --build
```
- Frontend : http://localhost:3000
- Backend API : http://localhost:8080

### Développement local

**Backend** (Java 25 + Maven) :
```bash
cd backend
mvn clean package -DskipTests
java -jar target/*.jar
```

**Frontend** (Node 24) :
```bash
cd frontend
npm install --legacy-peer-deps
npm run dev
```
- http://localhost:5173 (proxy API vers :8080)

## Stack
- **Backend** : Spring Boot 4.0.3, Java 25, WebSocket STOMP
- **Frontend** : React 19, TypeScript, Tailwind CSS 4, lightweight-charts, react-grid-layout
- **Infra** : Docker, Nginx

## Fonctionnalités
- Cotations BRVM temps réel (WebSocket)
- Graphique chandeliers 30 jours + live
- Carnet d'ordres bid/ask
- Indices BRVM Composite & BRVM 10
- Widgets drag & resize
- Détail émetteur

---
*Groupement IBEMS — ALTARYS LABS | AO_BI_2026_001 | Données simulées*
