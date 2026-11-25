# OJTTrack

OJTTrack is a full-stack web application designed for students undergoing OJT and school OJT coordinators. It helps students organize company applications, track application statuses and follow-ups, manage accepted placements, and record their training hours. It also enables coordinators to manage student accounts, monitor OJT progress, review daily time logs, verify completed hours, and identify students who need assistance. The goal is to replace scattered records and manual tracking with a centralized, organized, and reliable system for managing the OJT process.

See [docs/mvp.md](docs/mvp.md) for the full MVP scope.

## Tech stack

- **Frontend:** React + Vite (`frontend/`)
- **Backend:** Java 25, Spring Boot, Spring Security, Spring Data JPA (`backend/`)
- **Database:** PostgreSQL, with schema migrations managed by Flyway (`backend/src/main/resources/db/migration/`)

## Running locally

### 1. Create the database

In `psql` as a superuser:

```sql
CREATE USER ojttrack_user WITH PASSWORD 'choose-a-password';
CREATE DATABASE ojttrack_dev OWNER ojttrack_user;
```

Flyway creates the tables automatically the first time the backend starts.

### 2. Start the backend (port 8080)

Set these environment variables, then run the app from `backend/`:

| Variable | Required | Purpose |
|---|---|---|
| `DB_PASSWORD` | Yes | Password for `ojttrack_user` |
| `SEED_COORDINATOR_EMAIL` | First run | Email for the first coordinator account |
| `SEED_COORDINATOR_PASSWORD` | First run | Password for that account (at least 8 characters) |

The coordinator is only created if no coordinator exists yet.

```powershell
cd backend
$env:DB_PASSWORD = 'choose-a-password'
$env:SEED_COORDINATOR_EMAIL = 'coordinator@example.com'
$env:SEED_COORDINATOR_PASSWORD = 'change-me-please'
.\mvnw.cmd spring-boot:run
```

### 3. Start the frontend (port 5173)

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Vite forwards every `/api` request to the backend on port 8080.

## Running tests

```powershell
cd backend
.\mvnw.cmd test -Dtest='*ControllerTest'
```

The controller tests need no database. `BackendApplicationTests` starts the whole app, so it needs PostgreSQL running and `DB_PASSWORD` set.

```powershell
cd frontend
npm run lint
```
