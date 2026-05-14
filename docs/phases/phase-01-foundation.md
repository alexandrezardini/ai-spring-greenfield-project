# Phase 01 — Configuração Base do Projeto

## Objective

Entregar a fundação backend executável do StreamTube: projeto Spring Boot renomeado para o pacote definitivo, módulos Spring Modulith escaffoldados, ambiente Docker Compose consistente, Liquibase habilitado com extensões PostgreSQL básicas, observabilidade via Actuator e tooling de qualidade de código — pronto para receber o domínio na Fase 02.

---

## Step Implementations

### SI-01.1 — Rename root Java package to `com.streamtube.backend`

**Description:** Move all production and test classes from the Spring Initializr default package `org.example.streamtubebackend` to `com.streamtube.backend`, and update the Maven `groupId` accordingly. Done first so subsequent SIs work in the definitive package layout.

**Technical actions:**

- Update `pom.xml` `<groupId>` from `org.example` to `com.streamtube` and adjust `<artifactId>` references if any depend on the old `groupId`.
- Move `StreamTubeBackendApplication.java`, `HelloController.java` from `src/main/java/org/example/streamtubebackend/` to `src/main/java/com/streamtube/backend/`, updating the `package` declaration in each file.
- Move `StreamTubeBackendApplicationTests.java`, `TestcontainersConfiguration.java`, `TestStreamTubeBackendApplication.java` from `src/test/java/org/example/streamtubebackend/` to `src/test/java/com/streamtube/backend/`, updating package declarations.
- Delete the now-empty `org/example/streamtubebackend/` directories from both `src/main/java/` and `src/test/java/`.
- Run `./mvnw clean test` to confirm the test context still loads under the new package.

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| StreamTubeBackendApplicationTests.java | Integration | Spring context loads under the renamed package (no new logic — existing `contextLoads()` re-validated). |

**Dependencies:** None

**Acceptance criteria:**

- Running `./mvnw clean test` succeeds with all classes resolved under `com.streamtube.backend.*`.
- `src/main/java/org/example/` and `src/test/java/org/example/` directories no longer exist.
- The Maven coordinates resolve as `com.streamtube:stream-tube-backend:0.0.1-SNAPSHOT`.

---

### SI-01.2 — Scaffold Spring Modulith module placeholders

**Description:** Create the eight top-level module packages under `com.streamtube.backend` (`common`, `users`, `channels`, `auth`, `videos`, `comments`, `interactions`, `categories`), each with a `package-info.java` annotated for Spring Modulith. Add a JUnit test that runs `ApplicationModules.of(...).verify()` so module boundaries are validated from Phase 01 onwards.

**Technical actions:**

- Create eight package directories under `src/main/java/com/streamtube/backend/` (one per module). Each directory receives a `package-info.java`.
- In each `package-info.java` (except `common`), declare `@org.springframework.modulith.ApplicationModule(displayName = "<Module Name>")`. For `common`, use `@ApplicationModule(displayName = "Common", type = ApplicationModule.Type.OPEN)` so other modules may depend on its internals.
- Create `src/test/java/com/streamtube/backend/ModularityTests.java` containing one `@Test` that invokes `ApplicationModules.of(StreamTubeBackendApplication.class).verify()`.
- Move `StreamTubeBackendApplication.java` to remain at the root `com.streamtube.backend` package so Spring Modulith discovers all eight modules as siblings.

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| ModularityTests.java | Unit | `ApplicationModules.of(...).verify()` succeeds — eight modules are detected with no cyclic or illegal dependencies. |

**Dependencies:** SI-01.1

**Acceptance criteria:**

- `./mvnw test -Dtest=ModularityTests` exits 0 and reports eight `ApplicationModule` entries.
- The `common` module is detected with `Type.OPEN`; the other seven are detected as `CLOSED` (default).
- No production code yet imports types from any module other than `common` (modulith violations would fail the verification).

---

### SI-01.3 — Migrate configuration to YAML and create `dev`/`test` profiles

**Description:** Convert `application.properties` to `application.yml` and create per-profile overrides for local Docker Compose (`dev`) and Testcontainers-driven tests (`test`). Establishes the hierarchical configuration foundation that Liquibase, Actuator, and the datasource will plug into.

**Technical actions:**

- Delete `src/main/resources/application.properties` and create `src/main/resources/application.yml` carrying cross-profile defaults: `spring.application.name`, `spring.profiles.active` set via env var (`SPRING_PROFILES_ACTIVE`), and a `logging.level.root: INFO` baseline.
- Create `src/main/resources/application-dev.yml` with the datasource pointing at the `db` service (URL `jdbc:postgresql://db:5432/streamtube`, username/password injected via env vars with safe defaults), and `spring.docker.compose.enabled: false` (the app runs inside Compose, so Spring Boot must not try to start a sibling stack).
- Create `src/main/resources/application-test.yml` with `spring.docker.compose.enabled: false` and any test-specific overrides (e.g., `logging.level.org.springframework: WARN`).
- Confirm `./mvnw clean test` still launches the application context (Testcontainers config supplies the datasource at test time via `@ServiceConnection`).

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| StreamTubeBackendApplicationTests.java | Integration | Existing context-load test validates that `application.yml` + `application-test.yml` produce a startable Spring context. |

**Dependencies:** None

**Acceptance criteria:**

- `src/main/resources/application.properties` is removed; `application.yml`, `application-dev.yml`, `application-test.yml` exist.
- Starting the app with `SPRING_PROFILES_ACTIVE=dev` loads the dev datasource URL `jdbc:postgresql://db:5432/streamtube`.
- `./mvnw test` runs the context-load test successfully under the `test` profile (or the unprofiled default merged with `test`).

---

### SI-01.4 — Add Lombok dependency and annotation processor

**Description:** Add Lombok as a `provided`-scope dependency and configure the Maven compiler plugin annotation processor path so subsequent SIs (and future phases) can use `@Slf4j`, `@RequiredArgsConstructor`, `@Value`, etc. — annotations already referenced by `.claude/rules/`.

**Technical actions:**

- Add `org.projectlombok:lombok` (version managed by the Spring Boot parent BOM) as `<scope>provided</scope>` in `pom.xml`.
- Update the `maven-compiler-plugin` `<annotationProcessorPaths>` block to include the Lombok processor alongside the existing `spring-boot-configuration-processor`.
- Append a short "IDE setup: install the Lombok plugin" note to `stream-tube-backend/CLAUDE.md`.

**Tests:** _(no testable artifact introduced by this SI alone — Lombok is exercised by any class that adopts it)_

**Dependencies:** None

**Acceptance criteria:**

- `./mvnw clean compile` succeeds with Lombok on the classpath.
- A minimal smoke check (e.g., a `@Slf4j`-annotated class in `com.streamtube.backend.common`) compiles and the generated `log` field is accessible.

---

### SI-01.5 — Add Spring Boot Actuator and remove `HelloController`

**Description:** Add `spring-boot-starter-actuator`, expose `/actuator/health` and `/actuator/info` publicly, and delete the placeholder `HelloController` so `/actuator/health` is the canonical smoke-test endpoint.

**Technical actions:**

- Add `org.springframework.boot:spring-boot-starter-actuator` (version managed by the Spring Boot parent BOM) to `pom.xml`.
- In `application.yml`, configure `management.endpoints.web.exposure.include: "health,info"` and `management.endpoint.health.show-details: when-authorized` (defaults safe for dev; tightening deferred to Phase 07).
- Delete `src/main/java/com/streamtube/backend/HelloController.java`.
- Update `stream-tube-backend/CLAUDE.md` so the smoke-test command becomes `curl http://localhost:8080/actuator/health` returning `{"status":"UP"}`.
- Create an integration test that hits `/actuator/health` and asserts a 200 status with `status: UP` and Liquibase + datasource components reported as UP.

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| ActuatorHealthIntegrationTest.java | Integration | `GET /actuator/health` returns 200 with `status: UP` and includes `db` + `liquibase` health components. |

**Dependencies:** SI-01.1, SI-01.3

**Acceptance criteria:**

- `GET /actuator/health` returns 200 with body `{"status":"UP", ...}` including `db` and `liquibase` indicators showing UP.
- `GET /actuator/info` returns 200 (body may be empty until contributors are added).
- `GET /` returns 404 (the placeholder controller is gone — confirms cleanup).
- `GET /actuator/env` returns 404 (only `health` + `info` are exposed).

---

### SI-01.6 — Fix Docker Compose, rename Postgres service to `db`, introduce `.env`

**Description:** Repair the `compose.yaml` inconsistencies, rename the Postgres service from `postgres` to `db` to match documentation, and introduce a `.env` / `.env.example` pattern with inline defaults so the stack runs out of the box but supports per-developer overrides.

**Technical actions:**

- In `stream-tube-backend/compose.yaml`, rename the `postgres` service to `db`; update `depends_on` and `SPRING_DATASOURCE_URL` to reference `db` (`jdbc:postgresql://db:5432/streamtube`).
- Replace the hardcoded `SPRING_DATASOURCE_USERNAME=myuser` / `_PASSWORD=secret` / `mydatabase` with `${POSTGRES_USER:-streamtube}` / `${POSTGRES_PASSWORD:-streamtube}` / `${POSTGRES_DB:-streamtube}` substitutions on both the `db` and `spring-boot-app` services.
- Remove the inert `SPRING_JPA_HIBERNATE_DDL_AUTO=update` env var from `spring-boot-app`.
- Create `stream-tube-backend/.env.example` (committed) listing every variable referenced by `compose.yaml` with empty values and inline comments; add `.env` to `stream-tube-backend/.gitignore`.
- Update `stream-tube-backend/CLAUDE.md` verification commands (`docker compose exec db pg_isready -U streamtube`) and any other references that still use `postgres` as the service name.

**Tests:** _(no automated test — verification is manual via `docker compose up` and `docker compose exec db pg_isready`)_

**Dependencies:** None

**Acceptance criteria:**

- `docker compose up -d` from `stream-tube-backend/` starts both `db` and `spring-boot-app` without errors using only defaults (no `.env` required).
- `docker compose exec db pg_isready -U streamtube` outputs `accepting connections`.
- `docker compose exec spring-boot-app env | grep DATASOURCE` resolves the URL/user/password to the `streamtube` defaults.
- `.env` is present in `.gitignore` and absent from `git status` after creation.
- No `SPRING_JPA_HIBERNATE_DDL_AUTO` variable appears in `docker compose config`.

---

### SI-01.7 — Enable Liquibase with initial PostgreSQL extensions

**Description:** Enable Liquibase, point it at a working master changelog, and apply an initial `001-extensions.yaml` changeset that installs `pgcrypto` and `citext`. Validates the migration pipeline end-to-end and prepares the schema for Phase 02 (UUID PKs, case-insensitive emails).

**Technical actions:**

- In `application.yml`, set `spring.liquibase.enabled: true` and `spring.liquibase.change-log: classpath:db/changelog/db.changelog-master.yaml`.
- Rewrite `src/main/resources/db/changelog/db.changelog-master.yaml` to use `databaseChangeLog: [ - includeAll: { path: db/changelog/changes/ } ]` so future changesets are picked up by convention.
- Create `src/main/resources/db/changelog/changes/001-extensions.yaml` with two changesets: id `001-pgcrypto-extension` running `CREATE EXTENSION IF NOT EXISTS "pgcrypto"`, and id `001-citext-extension` running `CREATE EXTENSION IF NOT EXISTS "citext"`. Each has a `<rollback>` section (`DROP EXTENSION IF EXISTS ...`) and `author: IA`.
- Create an integration test using `@SpringBootTest` + `TestcontainersConfiguration` that queries `pg_extension` after startup and asserts both extensions are present.

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| LiquibaseExtensionsIntegrationTest.java | Integration | After application startup against a Testcontainers Postgres, querying `SELECT extname FROM pg_extension` returns rows containing `pgcrypto` and `citext`. |

**Dependencies:** SI-01.3, SI-01.6

**Acceptance criteria:**

- Starting the application with the `dev` profile applies the two changesets exactly once; the Liquibase `DATABASECHANGELOG` table records both with author `IA`.
- After startup, `SELECT 1 FROM pg_extension WHERE extname = 'pgcrypto'` and `... = 'citext'` each return one row.
- A restart applies zero new changesets (idempotency).
- `GET /actuator/health` reports the `liquibase` component as UP.

---

### SI-01.8 — Add Spotless Maven plugin and EditorConfig

**Description:** Wire up automated code formatting with the Spotless Maven plugin (`google-java-format`, GOOGLE style) and a root `.editorconfig` for cross-IDE indentation consistency. Apply the formatter once across the current codebase so subsequent SIs ship with the canonical style.

**Technical actions:**

- Add `com.diffplug.spotless:spotless-maven-plugin` to the `<build><plugins>` section of `pom.xml`, with a `<java><googleJavaFormat><style>GOOGLE</style></googleJavaFormat><removeUnusedImports/><formatAnnotations/></java>` configuration. Bind the `check` goal to the `verify` phase.
- Create `.editorconfig` at the monorepo root declaring `indent_style = space`, `indent_size = 2` for `*.java`, `*.yml`, `*.yaml`, and `*.xml`; `end_of_line = lf`; `insert_final_newline = true`; `charset = utf-8`.
- Run `./mvnw spotless:apply` once and commit the resulting reformat as part of this SI.
- Add a short "Code formatting" section to `stream-tube-backend/CLAUDE.md` documenting `./mvnw spotless:apply` (to fix) and `./mvnw spotless:check` (to verify, also run automatically in `mvn verify`).

**Tests:** _(no automated test — Spotless `check` goal runs in `mvn verify` and fails the build on style violations)_

**Dependencies:** SI-01.1

**Acceptance criteria:**

- `./mvnw spotless:check` exits 0 on the post-formatted codebase.
- Introducing a deliberate formatting violation (e.g., extra blank lines, missing import order) makes `./mvnw verify` fail with a Spotless error referencing the offending file.
- `./mvnw spotless:apply` restores formatting and the subsequent `check` succeeds.
- `.editorconfig` is present at the monorepo root and lints any IDE that respects EditorConfig.

---

### SI-01.9 — Audit and consolidate the AI coding foundation

**Description:** Inventory the already-existing AI coding artifacts (`.claude/skills/`, `.claude/rules/`, `.claude/commands/`, `.mcp.json`, root and backend `CLAUDE.md`) and reconcile any drift introduced by the other Phase 01 SIs (e.g., Lombok mentions, `db` service name, removal of `HelloController`). No new automation is added in this SI per TD-15.

**Technical actions:**

- Read every file under `.claude/skills/`, `.claude/rules/`, `.claude/commands/`, plus the root `CLAUDE.md` and `stream-tube-backend/CLAUDE.md`. Produce a short internal checklist (kept in the PR description, not committed) of references that touch package names, service names, controllers, and Lombok.
- Update rule files where they reference `org.example.streamtubebackend` to use `com.streamtube.backend`, and where they assume `db` vs `postgres` to standardize on `db`.
- Confirm `.claude/rules/spring-configuration-observability.md` (Lombok-based `@Slf4j`), `.claude/rules/spring-common-conventions.md` (`@RequiredArgsConstructor`), and any other Lombok-mentioning rule are consistent with the dependency added in SI-01.4 (no orphan references).
- Confirm `.mcp.json` context7 entry is well-formed and the project's CLAUDE.md "Library Documentation Lookup" section is still accurate.

**Tests:** _(no testable artifact — this SI updates documentation/configuration only)_

**Dependencies:** SI-01.4, SI-01.5, SI-01.6

**Acceptance criteria:**

- No file under `.claude/` or any `CLAUDE.md` mentions `org.example.streamtubebackend` or the old `postgres` Compose service name.
- All references to Lombok annotations in `.claude/rules/` correspond to a dependency present in `pom.xml`.
- The root `CLAUDE.md` "Docker Networking" example service names match the post-SI-01.6 `compose.yaml`.
- A second pass reading every CLAUDE.md and rule file finds no commands that fail when executed against the current repo state.

---

## Technical Specifications

### Data Model

No business tables are introduced in this phase. The Liquibase pipeline installs two PostgreSQL extensions in the `public` schema:

| Object | Type | Purpose |
|--------|------|---------|
| `pgcrypto` | Extension | Provides `gen_random_uuid()` for UUID primary keys (used from Phase 02 onward). |
| `citext` | Extension | Provides the `CITEXT` case-insensitive text type (used for unique email columns from Phase 02 onward). |

Liquibase also creates its own bookkeeping tables (`DATABASECHANGELOG`, `DATABASECHANGELOGLOCK`) on first run.

---

### API Contracts

Only Spring Boot Actuator endpoints are exposed in this phase. Domain endpoints arrive in Phase 02.

#### GET /actuator/health (SI-01.5)

**Request headers:** none required.

**Response 200:**
- status: string — overall status (`UP`, `DOWN`, `OUT_OF_SERVICE`, `UNKNOWN`).
- components: object — per-indicator health, including at least `db`, `livenessState`, `readinessState`, and `liquibase`.
- groups: object — included when health groups are configured (none in this phase).

**Error responses:** Spring Boot defaults. A custom error response shape is deferred to Phase 02 per TD-13.

#### GET /actuator/info (SI-01.5)

**Request headers:** none required.

**Response 200:**
- Arbitrary object aggregated from registered `InfoContributor` beans. Empty `{}` in this phase (no contributors configured).

**Error responses:** Spring Boot defaults.

---

### Authorization Matrix

| Endpoint | Public | Authenticated | Role |
|----------|--------|---------------|------|
| GET /actuator/health | ✓ | | |
| GET /actuator/info | ✓ | | |

All other Actuator endpoints (`/actuator/env`, `/actuator/beans`, etc.) are disabled via `management.endpoints.web.exposure.include: "health,info"` and return 404.

---

## Dependency Map

```
SI-01.1 (rename root package)
├── SI-01.2 (Modulith placeholders)
├── SI-01.5 (Actuator + remove HelloController)  ─┐
├── SI-01.8 (Spotless + EditorConfig)             │
SI-01.3 (YAML + profiles)                         │
├── SI-01.5 (Actuator + remove HelloController) ──┤
└── SI-01.7 (Liquibase + extensions) ─┐           │
SI-01.6 (Compose fixes, .env)         │           │
└── SI-01.7 (Liquibase + extensions) ─┘           │
SI-01.4 (Lombok)                                  │
└── SI-01.9 (AI foundation audit) ────────────────┤
SI-01.5 ──────────────────────────────────────────┤
SI-01.6 ──────────────────────────────────────────┘
                                                  └─→ SI-01.9
```

Linear summary of an implementation-friendly order:

1. **SI-01.1** (root package rename) — must come first; everything else lives under the new package.
2. **SI-01.3** (YAML + profiles) and **SI-01.4** (Lombok) and **SI-01.6** (Compose fixes) — independent, can run in parallel.
3. **SI-01.2** (Modulith placeholders) — after SI-01.1.
4. **SI-01.5** (Actuator + drop HelloController) — after SI-01.1 and SI-01.3.
5. **SI-01.7** (Liquibase + extensions) — after SI-01.3 and SI-01.6.
6. **SI-01.8** (Spotless) — after SI-01.1 (the formatter pass is applied once at the end so it covers all renamed files).
7. **SI-01.9** (AI foundation audit) — last; consolidates documentation after the other SIs have settled.

---

## Deliverables

- [ ] Backend code lives under `com.streamtube.backend.*` with eight module placeholders (`common`, `users`, `channels`, `auth`, `videos`, `comments`, `interactions`, `categories`).
- [ ] `ApplicationModules.of(StreamTubeBackendApplication.class).verify()` passes in the `ModularityTests` test.
- [ ] `application.yml` + `application-dev.yml` + `application-test.yml` replace `application.properties`; profiles `dev` and `test` load cleanly.
- [ ] `pom.xml` includes `lombok` (provided), `spring-boot-starter-actuator`, and `spotless-maven-plugin` (build); annotation processor path is configured.
- [ ] `/actuator/health` and `/actuator/info` respond 200 and are the only Actuator endpoints exposed; `HelloController` is deleted.
- [ ] `docker compose up -d` from `stream-tube-backend/` starts `db` + `spring-boot-app` without errors; `.env.example` is committed, `.env` is gitignored, no `SPRING_JPA_HIBERNATE_DDL_AUTO` env var remains.
- [ ] Liquibase is enabled with `db.changelog-master.yaml` using `includeAll`; `changes/001-extensions.yaml` installs `pgcrypto` and `citext` extensions verifiable via `SELECT extname FROM pg_extension`.
- [ ] `.editorconfig` exists at the monorepo root; `./mvnw verify` runs Spotless `check` successfully on the post-formatted codebase.
- [ ] All `.claude/rules/`, `.claude/skills/`, `.claude/commands/`, root `CLAUDE.md`, and `stream-tube-backend/CLAUDE.md` are consistent with the Phase 01 outcome (no references to `org.example.streamtubebackend`, no stale `postgres` service name, no orphan commands).
- [ ] All SI tests pass in `stream-tube-backend` (`./mvnw test`).
- [ ] Project compiles successfully in `stream-tube-backend` (`./mvnw clean compile`).
- [ ] Project builds successfully in `stream-tube-backend` (`./mvnw clean package`).
- [ ] Code formatting check passes in `stream-tube-backend` (`./mvnw spotless:check`).
