# Security boundaries

- An unguessable bearer cookie identifies a workspace; the workspace ID is public data and never authorizes access.
- Resolve workspace from the credential hash on every request and scope every query, mutation, relationship, download, and export.
- Write routes require same-origin CSRF verification. Production cookies are Secure, HttpOnly, SameSite=Lax, and scoped to `/`.
- Never accept a filesystem path from clients. Validate file magic/type, size and workspace ownership; serve as attachment.
- URL extraction permits only public HTTP(S), blocks loopback, private, link-local and metadata IPs on every redirect, and has byte/time limits.
- Do not log credentials, tokens, file contents, identity data, or model API keys.
