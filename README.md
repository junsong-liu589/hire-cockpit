# Hire Cockpit

Hire Cockpit is a Chinese-language job search workspace for tracking employers, jobs, applications, interviews, offers, and decisions. Each browser receives an anonymous private workspace. Clearing that browser's cookies loses access unless the user first exports a backup. There is no account recovery in this version.

## Start with Docker Desktop

1. Install Docker Desktop with Compose v2 and Java 21 for local development.
2. Copy `.env.example` to `.env` and replace the local database passwords.
3. Run `docker compose up --build` from this folder.
4. Open <http://localhost:8080>. The API health endpoint is `/actuator/health`.

The first visit creates a workspace. Use Settings → Data to export a backup before moving browsers or clearing cookies. Restore previews the archive before replacing workspace data. Never upload real identity documents to a public demo.

## Local development

- Backend: Java 21 and Maven 3.9+, then `cd backend && mvn spring-boot:run`.
- Frontend: Node.js 20+, then `cd frontend && npm ci && npm run dev`.
- Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` to a MySQL 8.4 database. Flyway applies versioned migrations; startup does not recreate tables.
- Set `VITE_API_BASE_URL=http://localhost:8080/api/v1` when running Vite separately.

## Privacy and operations

The workspace credential is an HttpOnly cookie; it is not the workspace ID. Backups contain personal data and uploaded files. Store them offline and protect them. Cookies are same-site and writes require a CSRF token. Production requires HTTPS (`COOKIE_SECURE=true`), a restricted DB user, persistent private volumes, encrypted backups, log redaction, and a retention policy. The included AI integration is optional; basic features work without an AI key.

See [docs/ROADMAP.md](docs/ROADMAP.md), [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md), and [docs/SECURITY.md](docs/SECURITY.md).
