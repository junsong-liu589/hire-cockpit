# Security boundaries

- An unguessable bearer cookie identifies a workspace; the workspace ID is public data and never authorizes access.
- Resolve workspace from the credential hash on every request and scope every query, mutation, relationship, download, and export.
- Write routes require same-origin CSRF verification. Production cookies are Secure, HttpOnly, SameSite=Lax, and scoped to `/`.
- Never accept a filesystem path from clients. Validate file magic/type, size and workspace ownership; serve as attachment.
- URL extraction permits only public HTTP(S), blocks loopback, private, link-local and metadata IPs on every redirect, and has byte/time limits.
- Do not log credentials, tokens, file contents, identity data, or model API keys.
- Personal profile JSON is encrypted with AES-256-GCM using `APP_ENCRYPTION_KEY`; the workspace ID is authenticated as associated data. Profile writes are disabled when the key is absent. Store the key separately from database backups.
- Uploads are limited to content-checked PDF, PNG, JPEG, and Office files up to 20 MiB. Office ZIP archives are scanned without extraction, capped at 1,000 entries and 64 MiB expanded data. Disk names are random IDs; original names are metadata only. Downloads require a same-workspace file record and are sent as attachments with `nosniff` and `no-store` headers.
