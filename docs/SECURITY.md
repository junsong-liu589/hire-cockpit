# Security and privacy boundaries

## Current static PWA

- The PWA stores business records in IndexedDB for the current site origin, device, and browser profile. It does not send those records to GitHub Pages, an app server, or an AI API. Browser profiles on the same device are separate; tabs in one profile intentionally share the same local work area.
- Local business records and resume attachments are **not encrypted by this PWA**. Use a personal device account protected by a password; avoid sensitive records on shared devices. Browser extensions, device malware, or malicious code served from the same origin can potentially read the site's IndexedDB.
- Backups contain all exported records and attachment bytes. They are plain JSON and may contain contact information, resume contents, interview notes, and other personal data. Keep the file in a private location; never commit it or publish it. A restore replaces all local PWA data after explicit confirmation.
- Browser storage is best-effort unless the browser grants persistent storage. Clearing website data, resetting the browser, profile loss, disk failure, or storage exhaustion may erase records. Persistence requests do not replace backups.
- The service worker caches application assets for offline startup. It does not cache or transmit user business data. HTTPS is required for PWA installation and service workers, except browser localhost development.
- Recruitment page content is not fetched by the static PWA. The user opens the supplied URL, copies the job text, reviews the editable draft, and explicitly confirms before the job is stored. This avoids pretending browser code can bypass third-party CORS rules.
- Reminder records update when the app opens; they are not guaranteed background notifications while the app is closed. Core workflow and keyword matching do not require an AI API key.

## Legacy server architecture (retained for development/compatibility)

The old Spring Boot/MySQL API retains its separate workspace security controls: unguessable HttpOnly/SameSite cookies, workspace resolution from the credential hash, CSRF and same-origin checks on writes, and workspace-scoped database queries, relationships, files, and exports. Its API must not trust a caller-submitted workspace ID. Server profile records use AES-256-GCM when an `APP_ENCRYPTION_KEY` is configured. Those controls do not describe the current static PWA's local storage model.

The legacy API validates upload signatures, type, size and workspace ownership; protects downloads as attachments; and restricts server-side URL fetches to public HTTP(S) destinations with redirect and size limits. Do not expose the old stack publicly without reviewing [deployment requirements](DEPLOYMENT.md), TLS, secrets, backups, monitoring, and access boundaries.

## Repository hygiene

- Never commit credentials, real resumes, exported user data, database volumes, or uploaded files.
- Do not log bearer credentials, tokens, file contents, identity data, or API keys.
- Keep GitHub Pages source code public only after ensuring this repository contains code and documentation, not user data. Public code hosting does not publish browser-local IndexedDB records.
