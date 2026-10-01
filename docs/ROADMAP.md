# Delivery roadmap

## PWA product direction (2026-09-30)

The user selected an installable, free-to-host PWA after clarifying that each roommate can keep an independent data set. This supersedes the earlier default Docker-first end-user path. The backend, migrations, Compose stack, and existing S0–S7 implementation remain in the repository as legacy/developer architecture; they are not required by the static PWA. The PWA's business API is implemented by a browser-side IndexedDB adapter and makes no business-data network requests. Browser storage is origin/profile/device-local, not an account or sync service.

| PWA delivery item | Status | Evidence / limitation |
| --- | --- | --- |
| Static offline-capable application | Deployed; offline E2E passed | Published `/hire-cockpit/` PWA with 192/512 icons and scoped service worker. Chromium offline-shell E2E and GitHub Pages deployment passed. Native install prompt remains browser-controlled and was not separately exercised. |
| Local persistence and per-user separation | Implemented and E2E verified | IndexedDB records/settings; no API proxy or business-data uploads. Chromium E2E verifies persistence after reload, separate browser contexts, and restore into another context. StorageManager persistence request/estimate added; browser may deny durable-storage request. |
| Full hiring workflow | Implemented and E2E verified | Local adapter supports enterprises → jobs → applications/status history → exams/interviews/calendar/tasks → offers/comparison → analytics, profiles, resumes/files, keyword matching and reminders. Full Playwright acceptance and frontend build passed in CI. |
| Export, restore, and user instructions | Implemented and E2E verified | Versioned JSON export/preview/restore includes file data, validates bounds and requires explicit confirmation. The [PWA user guide](PWA-USER-GUIDE.md) explains persistence, backups, restore, and privacy. CI E2E transferred and restored the workspace between isolated contexts. |
| Recruitment drafts / no-AI mode | Implemented with browser limits | No AI key needed for business basics or keyword matching. Draft creation explains CORS and asks for manually copied content; explicit review/confirmation required before a job is saved. Background notifications while app is closed are not promised. |
| Free hosted link | Published | Public HTTPS URL: https://junsong-liu589.github.io/hire-cockpit/. Pages source is GitHub Actions; deployment run #3 succeeded and the live app loaded. |

### PWA verification update

- `docker compose build --pull=false`: passed after fixes; the production frontend bundle and Java package build. Vite reports the existing all-in-one Element Plus/ECharts bundle is over 500 kB.
- `npm run typecheck` in the frontend build image: passed.
- `npm run lint`: exit 0 with 1,237 warnings (mostly existing explicit `any` and unused-variable findings); no lint errors remain. The existing codebase contains significant lint debt.
- `npm test`: passed, 2 workflow unit tests. Vitest collection is restricted to `src/**/*.test.ts`, separate from Playwright E2E.
- Playwright acceptance covers manifest/icons, offline navigation, two-context separation, the full hiring flow, reload persistence, JSON backup/restore, and manually confirmed recruitment drafts. All three Chromium E2E cases passed in CI run #18; all three also passed locally on Microsoft Edge.
- Frontend CI run [#18](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36858237622) passed backend, frontend, and Compose smoke jobs. Pages deployment run [#3](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36859067661) succeeded. PR [#2](https://github.com/junsong-liu589/hire-cockpit/pull/2) merged as `a39579b`; the live URL returned the complete app shell.
- Public application: [https://junsong-liu589.github.io/hire-cockpit/](https://junsong-liu589.github.io/hire-cockpit/). Each browser keeps its own IndexedDB; the user guide explains backup export. This free local-first release does not sync roommate workspaces.

Status is based on executable evidence. A source file or UI mock alone does not close an acceptance item.

| Stage | Status | Evidence / limitation |
| --- | --- | --- |
| S0 Engineering foundation | Complete; CI runtime verification passed | Spring Boot/Maven, Vue/Vite, MySQL Compose, Flyway, health endpoint, validation errors, CI, and deployment/security docs. GitHub Actions run #18 passed frontend, backend, and Compose smoke jobs. |
| S1 Isolation and core flow | Complete; CI runtime verification passed | Workspace-scoped endpoints, session/CSRF/origin controls, and Testcontainers isolation checks passed backend CI; separate browser contexts were verified in PWA E2E. |
| S2 Daily workflow | Complete; runtime CI verification passed | Tasks, calendar, time zones, and reminders passed backend verification; PWA E2E verifies create/save/reload persistence. |
| S3 Profile and interviews | Complete; CI runtime verification passed | File validation/permissions, profiles, exams, interviews, and notes passed backend checks; PWA E2E saved a resume and attachment. |
| S4 Offers and analytics | Complete; CI runtime verification passed | Offer lifecycle/comparison and analytics passed backend tests and the full PWA flow. |
| S5 Import/export and recovery | Complete; CI runtime verification passed | Backend backup/restore tests passed; PWA E2E exported and restored the workspace between isolated contexts. |
| S6 Collection and intelligence | Complete; CI runtime verification passed | Recruitment drafts require manual review/confirmation; keyword matching needs no AI key. Backend security tests and PWA draft acceptance passed. |
| S7 Production readiness | Complete for selected PWA release | Production Compose configuration passed CI. Static PWA built for Pages and deployed over HTTPS; the live app was verified at the fixed URL. End users do not need Docker or a server. |

## Stage evidence log

At initial delivery, runtime acceptance was pending. The historical environment notes below are superseded by the final CI and deployment evidence in the PWA release follow-up. Business acceptance tests are recorded separately from build and startup checks.

Historical S0-S7 blocker notes (superseded): Java 21/Maven, Docker engine access, npm packages, and GitHub HTTPS initially appeared unavailable. Once the appropriate Docker/network access was restored, builds, backend tests, browser acceptance, CI, and Pages deployment completed as recorded below.

## Runtime verification update (2026-09-30)

The earlier Docker limitation above was incorrect: Docker Desktop was running, while the restricted terminal could not access its Engine pipe. With approved Docker access, Docker Engine 29.3.1 and Compose 5.1.1 were confirmed. Docker Hub token requests from this network timed out, so the public `mirror.gcr.io` cache was used to fetch the Node 22, Maven/Temurin 21, and Temurin 21 runtime images; no global Docker configuration was changed.


## PWA release follow-up (2026-10-01)

- Local frontend `npm test` passed (2 tests), `npm run typecheck` passed, and `npm run lint` exited 0 (1,231 warnings, 0 errors). CI lint/typecheck/tests/build also passed.
- All 3 Playwright cases passed on installed Microsoft Edge: offline app shell/service worker; complete local hiring flow with reload, attachment, two isolated browser contexts, backup export/restore; and the recruitment draft manual-confirmation gate. This found an outdated calendar button label; the assertion now matches “＋ 新建待办”. Playwright uses a dedicated strict port (4174 by default) so it cannot accidentally reuse another local web app. Offer submission now sends a plain evaluations object for IndexedDB structured cloning.
- GitHub Actions runs 36750010469, 36796441957, and 36857052188 exposed, in sequence, a stale calendar label and test timing gaps around IndexedDB commits and reload. E2E now waits for the saved task to appear before reload and verifies it afterward. Final run [#18](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36858237622) passed all jobs.
- The Windows E2E web server now launches Vite's CLI directly after building, avoiding an `npx` wrapper process left listening on the test port after a local run.
- PR [#2](https://github.com/junsong-liu589/hire-cockpit/pull/2) merged to `main` as `a39579b`. Pages deployment [#3](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36859067661) succeeded, and [the published application](https://junsong-liu589.github.io/hire-cockpit/) was opened and verified. The dedicated [PWA user guide](PWA-USER-GUIDE.md) is ready for users.

## Local record deletion follow-up (2026-10-01)

- Added confirmed deletion controls for user-facing records across companies, jobs, applications, tasks/calendar, resumes and attachments, profile entries, exams, interviews, experience notes, offers, matching rules, and notifications. Added a separately confirmed clear-workspace action in Settings.
- Cascade rules preserve relationship integrity: deleting a company removes its jobs and their application chains; deleting a job removes its applications and related schedule/interview/offer/history records; deleting one application keeps its job/company; deleting a resume unbinds it from applications and removes an attachment only when no other resume uses it. Deleting a file unbinds all resume references. Individual Offer deletion returns its application stage to the latest preceding non-Offer stage.
- The [user guide](PWA-USER-GUIDE.md) documents permanent deletion, cascades, and backup precautions.
- Verification on Microsoft Edge: `npm run typecheck`, `npm test` (2/2), `npm run build`, and Playwright E2E (3/3, including deleting a company after cross-context backup restore, verifying cascaded rows disappear while the unrelated resume/file survives, then deleting the file and resume). `npm run lint` exits 0 with 1,299 warnings and no errors (existing Vue formatting / explicit-any lint debt). Playwright's Windows runner left its Vite preview child listening after printing all three passing tests; that child was stopped after capturing the passing results.
- GitHub Actions run [#36867080229](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36867080229) passed backend, frontend, and Compose smoke for the PR head; the post-merge [main CI run #36867404806](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36867404806) also passed. Pages deployment [#36867404695](https://github.com/junsong-liu589/hire-cockpit/actions/runs/36867404695) succeeded. The live [PWA URL](https://junsong-liu589.github.io/hire-cockpit/) returned HTTP 200, and its deployed JavaScript bundle contains company delete, attachment delete, and clear-workspace controls.
