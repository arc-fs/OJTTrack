# OJTTrack

**A centralized On-the-Job Training management and tracking system for students and school coordinators.**

OJTTrack is a full-stack web application designed to simplify the management of student internships and On-the-Job Training (OJT). It provides students with tools to organize company applications, monitor application progress, manage accepted placements, and record training hours.

For school OJT coordinators, the system provides a centralized way to manage student accounts, monitor training progress, review daily time logs, verify completed hours, and identify students who may need assistance.

By bringing these processes into one platform, OJTTrack aims to reduce scattered records, minimize manual tracking, and make OJT management more organized and reliable.

---

## Screenshots

<!-- Add actual application screenshots here as the frontend develops. -->

| Student Dashboard | Coordinator Dashboard |
|---|---|
| Coming soon | Coming soon |

---

## Key Features

### For Students

- **Company Application Tracking** – Organize company applications and monitor their respective statuses.
- **Application Follow-ups** – Keep track of follow-up activities throughout the application process.
- **Placement Management** – Manage accepted company placements.
- **Training Hour Tracking** – Record and monitor completed OJT hours.
- **Progress Monitoring** – Keep track of OJT progress throughout the training period.

### For OJT Coordinators

- **Student Account Management** – Manage student accounts within the system.
- **OJT Progress Monitoring** – Monitor students' overall training progress.
- **Daily Time Log Review** – Review submitted student time logs.
- **Training Hour Verification** – Verify completed training hours.
- **Student Assistance Monitoring** – Identify students who may need additional attention or assistance.

---

## Technology Stack

OJTTrack uses a full-stack architecture with a React frontend, Java-based backend, and PostgreSQL database.

| Layer | Technology |
|---|---|
| Frontend | React, Vite |
| Backend | Java 25, Spring Boot |
| Security | Spring Security |
| Data Access | Spring Data JPA |
| Database | PostgreSQL |
| Database Migrations | Flyway |

### Architecture Overview

The application follows a frontend-backend architecture:

- **Frontend:** React handles the user interface and communicates with the backend through API requests.
- **Backend:** Spring Boot handles business logic, API endpoints, authentication, and application operations.
- **Database:** PostgreSQL stores application data, while Flyway manages database schema migrations.

The frontend communicates with the backend through REST API endpoints under `/api`.

---

## Project Structure

```text
OJTTrack/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   │       └── db/
│   │   │           └── migration/
│   │   └── test/
│   └── mvnw
│
├── frontend/
│   ├── src/
│   ├── public/
│   └── package.json
│
├── docs/
│   └── mvp.md
│
└── README.md
```

---

## Getting Started

Follow these instructions to set up and run OJTTrack locally.

### Prerequisites

Make sure the following are installed:

- Java 25
- PostgreSQL
- Node.js and npm
- Git

### 1. Clone the Repository

```bash
git clone <repository-url>
cd OJTTrack
```

### 2. Create the Database

Connect to PostgreSQL using a superuser account and execute:

```sql
CREATE USER ojttrack_user WITH PASSWORD 'choose-a-password';
CREATE DATABASE ojttrack_dev OWNER ojttrack_user;
```

Flyway automatically creates and manages the database tables when the backend starts.

### 3. Configure the Backend

Navigate to the backend directory:

```powershell
cd backend
```

Configure the required environment variables:

| Variable | Required | Description |
|---|---|---|
| `DB_PASSWORD` | Yes | Password for the PostgreSQL database user. |
| `SEED_COORDINATOR_EMAIL` | First run | Email address for the initial coordinator account. |
| `SEED_COORDINATOR_PASSWORD` | First run | Password for the initial coordinator account (at least 8 characters). |

Example PowerShell configuration:

```powershell
$env:DB_PASSWORD = 'choose-a-password'
$env:SEED_COORDINATOR_EMAIL = 'coordinator@example.com'
$env:SEED_COORDINATOR_PASSWORD = 'change-me-please'
```

The initial coordinator account is created only when no coordinator account exists yet.

### 4. Start the Backend

From the `backend/` directory:

```powershell
.\mvnw.cmd spring-boot:run
```

The backend runs at:

```text
http://localhost:8080
```

### 5. Start the Frontend

Open another terminal and navigate to the frontend directory:

```powershell
cd frontend
```

Install the dependencies:

```bash
npm install
```

Start the development server:

```bash
npm run dev
```

The frontend runs at:

```text
http://localhost:5173
```

Vite automatically forwards `/api` requests to the backend running on port `8080`.

---

## Running Tests

### Backend Tests

From the `backend/` directory:

```powershell
.\mvnw.cmd test -Dtest='*ControllerTest'
```

The controller tests do not require a running database.

**Note:** `BackendApplicationTests` starts the entire Spring Boot application and requires PostgreSQL to be running, along with the `DB_PASSWORD` environment variable.

### Frontend Code Quality

From the `frontend/` directory:

```bash
npm run lint
```

---

## Documentation

For more information about the project's requirements and planned functionality, see:

- [MVP Scope](docs/mvp.md) – Detailed description of the project's minimum viable product, requirements, and planned functionality.

---

## Project Goals

OJTTrack aims to provide a more organized and reliable approach to managing the OJT process by:

- Centralizing student applications and training records.
- Improving visibility into student OJT progress.
- Simplifying the monitoring and verification of training hours.
- Helping coordinators identify students who need assistance.
- Reducing reliance on scattered records and manual tracking.

---

*OJTTrack — Making OJT management more organized, transparent, and manageable.*