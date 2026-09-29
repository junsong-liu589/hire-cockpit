# Implementation decisions

## 2026-09-30 — Spring Boot patch release

The project pins Spring Boot 3.5.16, the latest 3.5 patch available on the project date. The Spring team announced 3.5.16 as the final open-source 3.5.x release, so production operators should plan a supported upgrade before relying on long-term public hosting. [Release announcement](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/).

## 2026-09-30 — Workspace identifier storage

The initial migration stores workspace and credential IDs as `BINARY(16)`. V2 uses `UUID_TO_BIN`/`BIN_TO_UUID` at the boundary and composite foreign keys to prevent cross-workspace links. The raw anonymous bearer is never stored; only its SHA-256 hash is persisted.

## 2026-09-30 — Scoped persistence

Workspace-sensitive reads and writes use parameterized JDBC queries with `workspace_id` in every predicate and MySQL composite foreign keys for relationships. MyBatis-Plus is also configured and used by `HiringAnalyticsMapper` for the stage-funnel query; that SQL explicitly resolves the workspace from the authenticated request. This hybrid keeps the critical tenant boundary visible in hand-written SQL while retaining the requested mapper layer. Flyway remains the only schema change mechanism.

## 2026-09-30 — Production proxy image

The optional production Compose profile pins Nginx to stable 1.30.5, the current stable release from the official project download page on this date. Recheck and update this tag during routine security maintenance. [Official Nginx releases](https://nginx.org/en/download.html).

## 2026-09-30 — Local verification constraints

This checkout began empty. The host has Java 8 and no Maven executable, the Docker CLI cannot connect to Docker Engine, npm dependencies are not cached, and registry/GitHub access is blocked. CI and integration tests are configured, but those checks remain unverified until run on a host with Java 21, network access, and Docker Engine.
