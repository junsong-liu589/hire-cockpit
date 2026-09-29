# Delivery roadmap

Status is based on executable evidence. A source file or UI mock alone does not close an acceptance item.

| Stage | Status | Evidence / limitation |
| --- | --- | --- |
| S0 Engineering foundation | Implemented; runtime acceptance blocked | Added README, AGENTS, Maven/Spring Boot 3.5.16 (latest OSS 3.5 patch), Vue/Vite, MySQL 8.4 Compose, Flyway V1, actuator health, ProblemDetail validation format, GitHub Actions (Maven/frontend/Compose smoke), deployment/security docs. Local inspection: original worktree had no files or commits. Runtime commands could not run: only Java 8, no Maven, Docker Engine pipe unavailable; GitHub Actions cannot be triggered because github.com:443 is unreachable. DOCX content extraction succeeded; packaged render could not run because LibreOffice is absent. Frontend lockfile generation is still blocked by unavailable npm registry/cache. S0 commit: `4e3543748607c7f7a89cc94d9cfa19194dfd582e`. |
| S1 Isolation and core flow | Implemented; runtime acceptance blocked | Added anonymous random bearer cookie (SHA-256 hash persisted), HttpOnly/SameSite cookie, strict double-submit CSRF plus same-origin Origin check, session-creation rate limit, server-derived workspace context, workspace-scoped company/job/application/resume/tag/dictionary endpoints, composite workspace foreign keys, favorite/status history, soft tag removal, and a real API-backed UI. Added MySQL Testcontainers and two-context Playwright isolation/CRUD tests. Static checks passed: JSON/XML parse and `git diff --check`; `docker compose config --quiet` passed when supplied placeholders. Runtime tests unavailable: Maven absent, npm dependencies not cached and registry unavailable, Docker Engine unavailable. S1 commit: pending in this entry. |
| S2 Daily workflow | Not started | Dashboard, tasks, calendar, notifications, timezone and deduplication. |
| S3 Profile and interviews | Not started | Private file service and linked resume/interview records. |
| S4 Offers and analytics | Not started | DB-backed funnel and configurable weighted comparison. |
| S5 Import/export and recovery | Not started | Preview, atomic restore and full file-integrity validation. |
| S6 Collection and intelligence | Not started | Safe URL fetch, editable drafts, rules; AI optional. |
| S7 Production readiness | Not started | Security regression, backup drill, HTTPS proxy, clean-machine E2E. |

## Stage evidence log

No stage has passed runtime acceptance yet. Append exact commands, summaries, defects fixed, and commit IDs at each stage; distinguish local verification from external deployment.
