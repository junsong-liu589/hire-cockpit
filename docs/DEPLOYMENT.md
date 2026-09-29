# Deployment

Use Docker Compose with MySQL 8.4, the application, and Nginx. Set all secrets through the environment or a secret manager; `.env.example` values are local placeholders. Generate `APP_ENCRYPTION_KEY` with `openssl rand -base64 32`; store it separately from database backups. Mount `./data` on a persistent private volume and include both MySQL and uploaded files in encrypted backups. Do not expose MySQL or the file volume publicly.

For production, set `COOKIE_SECURE=true`, configure a real TLS certificate and DNS name in `infra/nginx`, restrict database network access and credentials, and run the restore rehearsal before opening public access. This repository does not claim a public deployment; a server, domain, TLS credentials, and deployment authorization are required.

The application exposes a workspace backup ZIP at `GET /api/v1/backup/export`. Restore is a two-step operation: upload the ZIP to `POST /api/v1/backup/preview`, inspect row/file counts and SHA-256 verification, then explicitly confirm `POST /api/v1/backup/restore`. Restore replaces that workspace's application data while preserving its workspace access credential. Keep a separate encrypted infrastructure backup too; the application archive does not contain the workspace bearer credential, MySQL system tables, or the profile encryption key.
