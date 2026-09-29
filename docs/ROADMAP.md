# Delivery roadmap

Status is based on executable evidence. A source file or UI mock alone does not close an acceptance item.

| Stage | Status | Evidence / limitation |
| --- | --- | --- |
| S0 Engineering foundation | Implemented; runtime acceptance blocked | Added README, AGENTS, Maven/Spring Boot 3.5.16 (latest OSS 3.5 patch), Vue/Vite, MySQL 8.4 Compose, Flyway V1, actuator health, ProblemDetail validation format, GitHub Actions (Maven/frontend/Compose smoke), deployment/security docs. Local inspection: original worktree had no files or commits. Runtime commands could not run: only Java 8, no Maven, Docker Engine pipe unavailable; GitHub Actions cannot be triggered because github.com:443 is unreachable. DOCX content extraction succeeded; packaged render could not run because LibreOffice is absent. Frontend lockfile generation is still blocked by unavailable npm registry/cache. S0 commit: `4e3543748607c7f7a89cc94d9cfa19194dfd582e`. |
| S1 Isolation and core flow | Implemented; runtime acceptance blocked | Added anonymous random bearer cookie (SHA-256 hash persisted), HttpOnly/SameSite cookie, strict double-submit CSRF plus same-origin Origin check, session-creation rate limit, server-derived workspace context, workspace-scoped company/job/application/resume/tag/dictionary endpoints, composite workspace foreign keys, favorite/status history, soft tag removal, and a real API-backed UI. Added MySQL Testcontainers and two-context Playwright isolation/CRUD tests. Static checks passed: JSON/XML parse and `git diff --check`; `docker compose config --quiet` passed when supplied placeholders. Runtime tests unavailable: Maven absent, npm dependencies not cached and registry unavailable, Docker Engine unavailable. S1 commit: `2635b981d31f67e1ab6101a1fdc8d570082472ef`. |
| S2 Daily workflow | Implemented; runtime acceptance blocked | Added workspace-scoped tasks with completion timestamps and links, UTC instant storage with IANA time zones, month/week/day calendar views for tasks/job deadlines/events, configurable day offsets and reminder timezone, notification inbox/read markers with a workspace-scoped dedupe key, and load-on-open dashboard notifications. Added Testcontainers coverage for timezone/reminder dedupe and expanded two-browser Playwright coverage to calendar persistence. Runtime tests remain blocked by the S0 environment limits. |
| S3 Profile and interviews | Implemented; runtime acceptance blocked | Added workspace-scoped private file upload/list/download/soft-delete with opaque IDs, signature and size checks, attachment-only downloads, AES-256-GCM profile field encryption (key optional until profile writes), resume-file links, exams, interview rounds, question notes, ratings, experience notes, calendar entries and reminders. Expanded MySQL Testcontainers and Playwright coverage to profile encryption, malicious file rejection, and cross-workspace file denial. Static checks: `git diff --check`, `node --check frontend/eslint.config.js`, `frontend/package.json` JSON parse and `docker compose config --quiet` passed; no Java compiler, Python, or frontend dependencies. npm lint/typecheck/unit/build/e2e commands invoked from `frontend/`, all blocked because dependencies are absent; Docker runtime and Maven are unavailable as recorded above. `README.md` local setup uses `npm install` because no lockfile could be generated offline. |
| S4 Offers and analytics | Not started | DB-backed funnel and configurable weighted comparison. |
| S5 Import/export and recovery | Not started | Preview, atomic restore and full file-integrity validation. |
| S6 Collection and intelligence | Not started | Safe URL fetch, editable drafts, rules; AI optional. |
| S7 Production readiness | Not started | Security regression, backup drill, HTTPS proxy, clean-machine E2E. |

## Stage evidence log

No stage has passed runtime acceptance yet. Append exact commands, summaries, defects fixed, and commit IDs at each stage; distinguish local verification from external deployment.

S3 commands/results: git diff --check passed; 
ode --check frontend/eslint.config.js passed; docker compose config --quiet passed with CI placeholder DB secrets. 
pm run lint, 
pm run typecheck, 
pm test, 
pm run build, and 
pm run e2e were invoked in rontend/; each could not launch its required CLI (slint, ue-tsc, itest, ite, playwright) because dependencies are not installed and registry access is unavailable. mvn -version confirmed Maven absent. Docker Engine remains unavailable. Defects addressed: unsupported 
pm ci README instructions (changed to 
pm install pending lockfile), resume/file association SQL placeholder count, interview insert placeholder count, and file writes use explicit truncate/write options.
