# Delivery roadmap

Status is based on executable evidence. A source file or UI mock alone does not close an acceptance item.

| Stage | Status | Evidence / limitation |
| --- | --- | --- |
| S0 Engineering foundation | Implemented; runtime acceptance blocked | Added README, AGENTS, Maven/Spring Boot 3.5.5, Vue/Vite, MySQL 8.4 Compose, Flyway V1, actuator health, ProblemDetail validation format, GitHub Actions (Maven/frontend/Compose smoke), deployment/security docs. Local inspection: original worktree had no files or commits. Runtime commands could not run: only Java 8, no Maven, Docker Engine pipe unavailable; GitHub Actions cannot be triggered because github.com:443 is unreachable. DOCX content extraction succeeded; packaged render could not run because LibreOffice is absent. Stage implementation commit records the repository state. |
| S1 Isolation and core flow | Not started | Must verify independent browser storage and server-side authorization against MySQL. |
| S2 Daily workflow | Not started | Dashboard, tasks, calendar, notifications, timezone and deduplication. |
| S3 Profile and interviews | Not started | Private file service and linked resume/interview records. |
| S4 Offers and analytics | Not started | DB-backed funnel and configurable weighted comparison. |
| S5 Import/export and recovery | Not started | Preview, atomic restore and full file-integrity validation. |
| S6 Collection and intelligence | Not started | Safe URL fetch, editable drafts, rules; AI optional. |
| S7 Production readiness | Not started | Security regression, backup drill, HTTPS proxy, clean-machine E2E. |

## Stage evidence log

No stage has passed runtime acceptance yet. Append exact commands, summaries, defects fixed, and commit IDs at each stage; distinguish local verification from external deployment.
