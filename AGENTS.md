# Repository guide

- Product, architecture, security, sequence, and acceptance authority: the Hire Cockpit development and acceptance Word document supplied by the user; `docs/ROADMAP.md` tracks its stage evidence.
- Work through S0–S7 in `docs/ROADMAP.md`; record commands, outcomes, and commit IDs for each stage.
- Product delivery is a local-first PWA: scope business data to the current browser origin/profile using IndexedDB; do not send business records to a server. The legacy server workspace boundary remains required for backend API changes.
- Never commit credentials, real resumes, user data, database volumes, or uploaded files.
- Use `feat:`, `fix:`, `test:`, `docs:`, or `chore:` commit prefixes.
- Before changing existing files, inspect them and preserve user changes. Do not force push or bypass branch protection.
