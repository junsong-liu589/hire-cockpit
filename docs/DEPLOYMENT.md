# Deployment

Use Docker Compose with MySQL 8.4, the application, and Nginx. Set all secrets through the environment or a secret manager; `.env.example` values are local placeholders. Mount `./data` on a persistent private volume and include both MySQL and uploaded files in encrypted backups. Do not expose MySQL or the file volume publicly.

For production, set `COOKIE_SECURE=true`, configure a real TLS certificate and DNS name in `infra/nginx`, restrict database network access and credentials, and run the restore rehearsal before opening public access. This repository does not claim a public deployment; a server, domain, TLS credentials, and deployment authorization are required.
