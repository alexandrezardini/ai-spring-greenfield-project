# Technical Decisions — Phase 01: Configuração Base do Projeto

> **Phase:** 01 — Configuração Base do Projeto
> **Status:** Finalized
> **Date:** 2026-05-14

---

## TD-01: Root Java Package

**Context:** The Spring Initializr scaffolded the backend with the default package `org.example.streamtubebackend`. This is a placeholder. Phase 01 introduces business module placeholders (users, channels, videos, etc.) under the root package, so changing the root later becomes increasingly disruptive.

**Options:**

### Option A: `com.streamtube.backend`
- Reverse-domain naming aligned with the product (StreamTube). The `.backend` suffix scopes the package to this subproject in the monorepo.
- **Pros:** Conventional reverse-domain naming. Explicit subproject scoping leaves room for `com.streamtube.frontend` or `com.streamtube.shared` packages in the future. Clear separation from frameworks (`org.springframework.*`).
- **Cons:** Requires moving every source file under `src/main/java/org/example/streamtubebackend/` to `src/main/java/com/streamtube/backend/`. Updates to `application.yml` (if any class references) and tests.

### Option B: `org.example.streamtubebackend` (default Spring Initializr)
- Keep the placeholder package.
- **Pros:** Zero refactor cost right now. Existing tests and main class continue to work without change.
- **Cons:** Generic name not aligned with the product. `org.example.*` is a placeholder convention in Maven archetypes; it looks unprofessional in production code and version-control history.

### Option C: `com.streamtube` (no `.backend` suffix)
- Shorter root package.
- **Pros:** Less typing in imports.
- **Cons:** Semantically less clear in a monorepo. If shared Java code is ever added (e.g., shared DTOs generated from API contracts), there is no room to distinguish backend-only from cross-cutting packages.

**Recommendation:** **Option A (`com.streamtube.backend`)** — Phase 01 is the cheapest moment to rename, before business modules and Liquibase changesets reference any package or class name.

**Decision:** A (`com.streamtube.backend`)

---

## TD-02: Configuration File Format

**Context:** Spring Boot supports both `application.properties` and `application.yml`. The choice affects readability of hierarchical configuration (Liquibase, Actuator, datasource, logging) introduced in this and later phases.

**Options:**

### Option A: `application.yml`
- YAML hierarchical format. Native Spring Boot support; profiles use `application-{profile}.yml`.
- **Pros:** Hierarchical configs (datasource, liquibase, management, logging) are far more readable in YAML. Recommended by `.claude/rules/spring-configuration-observability.md`. Easier to compose multi-profile setups (`application-dev.yml`, `application-test.yml`).
- **Cons:** Whitespace-sensitive — indentation errors break the file. Slightly steeper learning curve than properties for newcomers.

### Option B: `application.properties`
- Flat key/value format.
- **Pros:** Simpler for very flat configs. Default of Spring Initializr.
- **Cons:** Becomes verbose and hard to read once nested properties accumulate. Profile composition is less ergonomic (long prefixes repeated).

**Recommendation:** **Option A (application.yml)** — Configuration complexity grows fast (Actuator, Liquibase, Datasource, Logging). YAML pays for itself by Phase 02.

**Decision:** A (`application.yml`)

---

## TD-03: Spring Profiles in Phase 01

**Context:** Different environments (local dev, test runs, future production) need distinct configurations. The choice is which profiles to create now versus later.

**Options:**

### Option A: `default` + `dev` + `test`
- `application.yml` carries cross-environment defaults. `application-dev.yml` carries Docker Compose datasource for local development. `application-test.yml` carries Testcontainers-friendly overrides. `prod` is deferred to Phase 07.
- **Pros:** Three profiles cover all current usage (local dev + automated tests). `prod` deferred avoids speculative configs that drift before deployment.
- **Cons:** Adds a `prod` step in Phase 07 (acceptable).

### Option B: `default` + `dev` + `test` + `prod`
- Same as Option A plus an empty `prod` placeholder.
- **Pros:** Forces awareness of prod from day one.
- **Cons:** Empty profile invites premature decisions (which secrets manager, which observability stack) before infrastructure is chosen.

### Option C: Only `default`
- Single profile.
- **Pros:** Minimal files.
- **Cons:** Test runs (Testcontainers) and dev runs (Docker Compose) compete for the same defaults — coupling that bites quickly.

**Recommendation:** **Option A** — Aligned with actual needs of Phase 01–06.

**Decision:** A (`default` + `dev` + `test`)

---

## TD-04: Lombok

**Context:** `.claude/rules/spring-configuration-observability.md` orients to `@Slf4j` (Lombok), and `.claude/rules/spring-common-conventions.md` cites `@RequiredArgsConstructor`. Lombok is not yet in `pom.xml`. The decision must be made now — adding later forces refactor of every class that should have used it.

**Options:**

### Option A: Add Lombok + `spring-boot-configuration-processor`
- Add `org.projectlombok:lombok` as `provided` scope and configure the Maven compiler plugin's annotation processor path. Allow the annotations already cited by project rules: `@Slf4j`, `@RequiredArgsConstructor`, `@Value`, `@Builder`, `@Getter`, `@Setter`.
- **Pros:** Aligned with existing project rules. Removes boilerplate (constructor injection, loggers). Cuts noise in domain classes.
- **Cons:** Adds annotation-processing complexity. Some teams find Lombok controversial. Requires IDE plugin.

### Option B: No Lombok — explicit boilerplate
- Don't use Lombok at all. Constructor injection is written manually; loggers via `LoggerFactory.getLogger(...)`.
- **Pros:** No annotation-processor magic. Every class is fully explicit.
- **Cons:** Conflicts with existing `.claude/rules/` that already orient to `@Slf4j` and `@RequiredArgsConstructor` — would require rewriting those rules. More verbose throughout the codebase.

### Option C: Lombok with restricted annotation allowlist
- Same as A, but project rules explicitly limit allowed annotations to `@Slf4j`, `@RequiredArgsConstructor`, `@Value` (records-like immutability), and a few others. Forbid `@SneakyThrows`, `@Data` (mutable state leak).
- **Pros:** Captures the productivity wins without the controversial annotations.
- **Cons:** Slightly more documentation overhead to keep allowlist clear.

**Recommendation:** **Option A** — Existing rules already assume Lombok. Simplest alignment.

**Decision:** A (Lombok added, all conventional annotations allowed)

---

## TD-05: Spring Boot Actuator

**Context:** The Phase 01 deliverable mentions a working development environment with health verification. `.claude/rules/spring-configuration-observability.md` says Actuator must be included in all production projects. Today the project has a placeholder `GET /` controller that returns a greeting string.

**Options:**

### Option A: Actuator with `/health` + `/info` exposed
- Add `spring-boot-starter-actuator`. Expose `/actuator/health` and `/actuator/info` over HTTP; other endpoints (`/metrics`, `/env`, `/beans`) are kept disabled until Phase 07.
- **Pros:** Standard Spring Boot mechanism. `/actuator/health` automatically aggregates database health (Liquibase + datasource indicators). Easy to verify the environment is healthy.
- **Cons:** Adds one starter dependency.

### Option B: Actuator + Micrometer + Prometheus endpoint
- Above plus `micrometer-registry-prometheus` and an exposed `/actuator/prometheus` endpoint.
- **Pros:** Production-ready observability from day one.
- **Cons:** Premature — no metrics scraper exists yet and no business metrics to expose. Adds dependencies that won't be exercised until Phase 07.

### Option C: No Actuator — custom health endpoint
- Skip Actuator. Build a small custom `/health` endpoint manually.
- **Pros:** Minimum dependencies.
- **Cons:** Reinvents what Actuator already does for free, including database health checks. Conflicts with the project's own rules.

**Recommendation:** **Option A** — Minimum cost, maximum standard.

**Decision:** A (Actuator with `/health` and `/info` exposed; other endpoints disabled until Phase 07)

---

## TD-06: Placeholder HelloController

**Context:** A placeholder `HelloController` returning `"Olá! Stream Tube Backend está rodando!"` was created during initial scaffolding. With Actuator coming (TD-05), the smoke-test endpoint becomes redundant.

**Options:**

### Option A: Remove `HelloController`, use `/actuator/health` as the smoke test
- Delete the file. Update `stream-tube-backend/CLAUDE.md` to point smoke-test instructions at `/actuator/health` instead of `GET /`.
- **Pros:** No dead code. `/actuator/health` is the canonical health endpoint and returns structured JSON.
- **Cons:** `GET /` returns 404 (Spring's default whitelabel error page) — acceptable, since the API root is not meant to be browsable.

### Option B: Keep `HelloController` and add Actuator
- Both endpoints coexist.
- **Pros:** Two layers of smoke test.
- **Cons:** Dead code. Confusion about which endpoint is canonical.

### Option C: Replace with `/api/v1/ping` custom controller
- Remove `HelloController` and create a versioned `/api/v1/ping` endpoint.
- **Pros:** Establishes the API versioning convention early.
- **Cons:** Premature — versioning conventions are best chosen when the first real endpoints land in Phase 02 (the auth API). `/actuator/health` already covers smoke testing.

**Recommendation:** **Option A** — Remove the placeholder; let `/actuator/health` be canonical.

**Decision:** A (Remove `HelloController`; use `/actuator/health`)

---

## TD-07: Docker Compose Postgres Service Name

**Context:** The current `compose.yaml` names the Postgres service `postgres`, but every other reference (root `CLAUDE.md` Docker Networking section, `stream-tube-backend/CLAUDE.md` verification commands) uses `db`. This inconsistency breaks documented commands.

**Options:**

### Option A: `db`
- Rename the Compose service from `postgres` to `db`. Update the `SPRING_DATASOURCE_URL` accordingly.
- **Pros:** Aligned with both CLAUDE.md files. Short, conventional name for a database service. `db` is the example used throughout the project's documentation.
- **Cons:** Slightly less self-describing — a newcomer cannot tell from the service name that this is PostgreSQL (visible in the image, but not the name).

### Option B: `postgres`
- Keep the current name. Update both CLAUDE.md files to reference `postgres`.
- **Pros:** Self-describing service name.
- **Cons:** Forces edits to two CLAUDE.md files and any future code/scripts that already assume `db`.

### Option C: `streamtube-db`
- Project-prefixed name.
- **Pros:** Unambiguous in logs and `docker ps` output.
- **Cons:** Verbose. The Compose network already namespaces services by project name.

**Recommendation:** **Option A (`db`)** — Lowest churn given existing documentation.

**Decision:** A (`db`)

---

## TD-08: Local Development Credentials Management

**Context:** The Postgres credentials live in `compose.yaml`. The current file hardcodes `streamtube/streamtube/streamtube`, but the Spring datasource block references different (and inconsistent) values. The team needs a coherent strategy that works offline, in CI, and supports overrides.

**Options:**

### Option A: `.env` file (gitignored) + inline defaults in `compose.yaml`
- `compose.yaml` references variables with defaults: `${POSTGRES_USER:-streamtube}`. A `.env.example` is committed with empty/example values; the real `.env` is gitignored. Developers can run `docker compose up` without creating `.env` — defaults take effect.
- **Pros:** Works offline without any setup. Secrets can be overridden per-developer without editing tracked files. CI doesn't need `.env`. `.env.example` documents the available variables.
- **Cons:** Two sources of truth (defaults inline + `.env`).

### Option B: Hardcoded inline (`streamtube/streamtube/streamtube`)
- Credentials fixed in `compose.yaml`. No `.env`.
- **Pros:** One source of truth. Simplest.
- **Cons:** No override path without editing a tracked file. Production secret patterns (env vars) are not exercised in dev.

### Option C: Mandatory `.env`
- `compose.yaml` references `${POSTGRES_USER}` without defaults. Without a `.env`, Compose fails.
- **Pros:** Forces explicit configuration.
- **Cons:** First-run friction. New devs cannot `docker compose up` without an extra step.

**Recommendation:** **Option A** — Best dev ergonomics + override path.

**Decision:** A (`.env` gitignored + `.env.example` committed + inline defaults in `compose.yaml`)

---

## TD-09: Initial Liquibase Setup

**Context:** Liquibase is in `pom.xml`, but disabled in `application.properties` and the master changelog `db.changelog-master.yaml` is empty (0 lines). Phase 02 will introduce real tables (users, channels, refresh_tokens, etc.). The choice is whether Phase 01 leaves Liquibase enabled with a working-but-empty setup, or whether it includes a meaningful first changeset.

**Options:**

### Option A: Master changelog with `includeAll` + initial extensions changeset
- Enable Liquibase. `db.changelog-master.yaml` uses `includeAll: path: db/changelog/changes/`. Create `db/changelog/changes/001-extensions.yaml` that runs `CREATE EXTENSION IF NOT EXISTS "pgcrypto"` and `CREATE EXTENSION IF NOT EXISTS "citext"`. The first verifies UUID generation infrastructure (`gen_random_uuid()`), the second supports case-insensitive emails in Phase 02.
- **Pros:** Liquibase is validated end-to-end at the end of Phase 01 (it runs and applies a real changeset). Phase 02 starts with extensions already in place — `email CITEXT` and UUID PKs work out of the box. Rollback is trivial (`DROP EXTENSION`).
- **Cons:** Anticipates extensions before tables exist. If extensions are needed for other purposes, this changeset stays minimal anyway.

### Option B: Master changelog with empty `databaseChangeLog: []`
- Liquibase enabled but no changesets.
- **Pros:** Purist: zero schema content in Phase 01.
- **Cons:** Liquibase startup runs a no-op and may not exercise the configuration end-to-end. Phase 02 has to add extensions as its first changeset, mixing infrastructure with business schema.

### Option C: Keep Liquibase disabled
- Leave `spring.liquibase.enabled=false` and an empty master changelog. Defer all Liquibase setup to Phase 02.
- **Pros:** Smallest Phase 01 scope.
- **Cons:** Contradicts the project plan's promise that Phase 01 delivers "initial database structure (schema, migrations)". Phase 02 has to take on Liquibase setup in addition to its real work.

**Recommendation:** **Option A** — Validates the pipeline; pays a small forward installment.

**Decision:** A (Master changelog with `includeAll` + `001-extensions.yaml` enabling `pgcrypto` and `citext`)

---

## TD-10: Code Quality Tooling

**Context:** Phase 01 establishes the conventions other phases inherit. Formatting and basic style enforcement should be in place before multiple SIs and contributors start touching code.

**Options:**

### Option A: Spotless + EditorConfig
- Spotless Maven plugin enforces formatting; `.editorconfig` ensures consistent indentation across IDEs.
- **Pros:** Two complementary tools, minimal config. Spotless `apply` auto-fixes; `check` runs in CI. EditorConfig is universally respected by IDEs.
- **Cons:** Two tools to maintain (though both are tiny).

### Option B: Spotless + Checkstyle + EditorConfig
- Adds Checkstyle for stricter style rules.
- **Pros:** More enforcement.
- **Cons:** Checkstyle rules can conflict with Spotless formatting decisions. Doubles maintenance for marginal gain in a small team.

### Option C: Only EditorConfig
- Just `.editorconfig`. Format discipline relies on humans.
- **Pros:** Lightest setup.
- **Cons:** No automated enforcement. Style drift across contributors.

### Option D: Nothing
- Defer all tooling.
- **Pros:** Lightest setup.
- **Cons:** Drift accumulates quickly.

**Recommendation:** **Option A** — Best ratio of enforcement to maintenance.

**Decision:** A (Spotless Maven plugin + EditorConfig)

---

## TD-11: Spotless Formatter Style

**Context:** Spotless supports several Java formatters: `google-java-format`, `palantir-java-format`, the Eclipse `formatter.xml` used by Spring projects, and others. Style choice affects every line of Java going forward.

**Options:**

### Option A: `google-java-format` (GOOGLE style)
- 2-space indent, 100-column line limit, no star imports, sorted imports. Single-source formatter widely adopted in modern Java.
- **Pros:** Most popular formatter outside of Spring's own codebases. Excellent IntelliJ plugin (`google-java-format-IntelliJ-plugin`). Stable and well-documented.
- **Cons:** 2-space indent diverges from Spring's own (4-space).

### Option B: `palantir-java-format`
- Fork of `google-java-format` with subjective improvements (better method-chain breaks).
- **Pros:** Some aesthetic wins.
- **Cons:** Less popular ecosystem; fewer IDE plugins.

### Option C: Eclipse formatter (Spring style)
- 4-space indent, the Spring project's own `formatter.xml`.
- **Pros:** Aligned with Spring upstream code.
- **Cons:** Verbose. Fewer Spotless-native conveniences.

**Recommendation:** **Option A (`google-java-format`, GOOGLE style)** — Most popular; lowest friction onboarding.

**Decision:** A (`google-java-format` with `GOOGLE` style)

---

## TD-12: Java and Spring Boot Versions

**Context:** The `pom.xml` already targets Java 17 and Spring Boot 4.0.5. Phase 01 is the right moment to confirm or change these baseline versions.

**Options:**

### Option A: Java 17 + Spring Boot 4.0.5 (keep current)
- Confirm the existing baseline.
- **Pros:** Spring Boot 4.0.x is current. Java 17 is LTS, broadly available, and fully supported. No changes.
- **Cons:** Java 17 misses Java 21's virtual threads, pattern matching for switch, and record patterns.

### Option B: Java 21 + Spring Boot 4.0.5
- Upgrade Java to 21 LTS.
- **Pros:** Virtual threads simplify the upload/streaming work in Phase 03. Better pattern matching.
- **Cons:** Requires updating `Dockerfile.dev` (currently `eclipse-temurin:17-jdk-alpine`) and `pom.xml` `<java.version>`. Phase 01 has no use cases that require Java 21 yet; can be revisited at Phase 03.

### Option C: Spring Boot 3.5.x + Java 17
- Downgrade to 3.5.x for a more mature ecosystem.
- **Pros:** More third-party libraries with known-good compatibility.
- **Cons:** Spring Boot 3.x is approaching EOL. New project should target the active major.

**Recommendation:** **Option A** — Stable baseline. Re-evaluate Java 21 at Phase 03 when async upload work begins.

**Decision:** A (Java 17 + Spring Boot 4.0.5)

---

## TD-13: Global Exception Handler Placement

**Context:** A `@RestControllerAdvice`-based global exception handler is required for consistent error responses. The question is whether to create it in Phase 01 (foundation, but no real domain errors to handle) or in Phase 02 (which introduces the first endpoints with real error branches).

**Options:**

### Option A: Defer to Phase 02
- Phase 01 ships with no global handler. Phase 02 implements it alongside the first real endpoints.
- **Pros:** Phase 02 has concrete error cases (duplicate email, invalid credentials, expired tokens) to anchor the design. Avoids writing handler code that exercises no real paths in Phase 01. Lets the Phase 02 decisions doc be updated (it currently references NestJS-style filters) before the handler is built.
- **Cons:** Phase 01 has no canonical error response — but it also has no endpoints that produce domain errors.

### Option B: Build in Phase 01 with `ProblemDetail` (RFC 9457)
- Implement a minimal `@RestControllerAdvice` returning Spring Boot 3+ `ProblemDetail`. Generic 404/500 only; domain errors come later.
- **Pros:** Establishes the contract early.
- **Cons:** Spring already returns `ProblemDetail` for the unhandled errors by default — there's nothing to implement until domain exceptions appear.

### Option C: Build in Phase 01 with custom `{ statusCode, error, message }` shape
- Implement the custom shape borrowed from the (NestJS-era) Phase 02 decisions doc.
- **Pros:** Aligned with the existing Phase 02 doc.
- **Cons:** That doc must be rewritten for Spring Boot regardless. Premature.

**Recommendation:** **Option A** — Defer until the design has concrete cases to validate.

**Decision:** A (Defer to Phase 02)

---

## TD-14: Spring Modulith Module Placeholders

**Context:** Spring Modulith is already in `pom.xml`. The convention is one top-level package per module; modules are validated by `ApplicationModules.of(Application.class).verify()`. The choice is whether Phase 01 creates module placeholders or lets each business phase create its own.

**Options:**

### Option A: All future modules created as placeholders
- Phase 01 creates `common`, `users`, `channels`, `auth`, `videos`, `comments`, `interactions`, `categories` as empty packages under `com.streamtube.backend.*`. Each carries a `package-info.java` with `@ApplicationModule` and an explicit `displayName` (allowedDependencies default to `common`). `common` has no `@ApplicationModule` (or is marked OPEN) so other modules can depend on it.
- **Pros:** Module boundaries validated early — `ApplicationModules.verify()` runs in Phase 01 against the real package layout. Future phases just add code inside existing modules.
- **Cons:** Many empty packages until each is populated. Module boundaries may need adjustment as features evolve.

### Option B: Only `common` placeholder
- Phase 01 creates only `com.streamtube.backend.common`. Other modules are created in their owning phases.
- **Pros:** Less code-dead packages.
- **Cons:** `ApplicationModules.verify()` test must be added with each new module — minor friction.

### Option C: Subset matching Phase 02–03
- Phase 01 creates `common`, `users`, `channels`, `auth`, `videos`. Later phases add `comments`, `interactions`, `categories`.
- **Pros:** Middle ground.
- **Cons:** Half-arbitrary line.

**Recommendation:** **Option A** — All placeholders. Up-front module map clarifies the architecture and gives `ApplicationModules.verify()` a real assertion target.

**Decision:** A (`common`, `users`, `channels`, `auth`, `videos`, `comments`, `interactions`, `categories` — all placeholder packages with `package-info.java`)

---

## TD-15: "AI Foundation for Coding" Scope

**Context:** The Phase 01 project-plan deliverable mentions "Fundação de IA para coding". Components like `.claude/skills/`, `.claude/rules/`, `.claude/commands/`, `.mcp.json`, and CLAUDE.md files already exist. The decision is whether Phase 01 only audits and consolidates what is in place, or also expands the foundation (hooks, settings.json with permissions, more skills).

**Options:**

### Option A: Audit + consolidation of what exists
- Inventory the existing files. Confirm consistency: rules that mention `@Slf4j` align with Lombok decision; rules that mention `db` align with the Compose service name; CLAUDE.md verification commands match `compose.yaml`. Update any drift. No new files added.
- **Pros:** Captures the moment when foundations align. Low risk.
- **Cons:** No new automation.

### Option B: Audit + `.claude/settings.json` with permission allowlist
- Above plus a `.claude/settings.json` listing read-only commands (`ls`, `cat`, `git status`, `./mvnw test`, `docker compose ps`) that are auto-approved to reduce permission prompts.
- **Pros:** Smoother day-to-day Claude Code usage.
- **Cons:** Settings file may diverge from user preferences.

### Option C: Audit + settings.json + automation hooks
- Above plus hooks (e.g., run Spotless apply on file save).
- **Pros:** Fully automated.
- **Cons:** Hook misbehavior can surprise contributors. Premature optimization.

**Recommendation:** **Option A** — Foundation is mostly in place; the value is in consolidation, not expansion.

**Decision:** A (Audit and consolidate existing `.claude/`, `.mcp.json`, and CLAUDE.md files — no new automation)

---

## TD-16: `nextjs-project/` Placeholder

**Context:** The root `CLAUDE.md` references `nextjs-project/` as "not yet initialized". The choice is whether Phase 01 creates a placeholder folder (for monorepo visibility) or waits until the frontend phase actually begins.

**Options:**

### Option A: Don't create — wait for the frontend phase
- `stream-tube-backend/` is the only subproject in the monorepo for now.
- **Pros:** No empty/orphaned folders. Frontend will scaffold its own structure when initialized.
- **Cons:** Monorepo intent is invisible in the file tree until the frontend lands.

### Option B: Create `nextjs-project/` with README placeholder
- Empty folder with a README explaining it will be initialized in a future phase.
- **Pros:** Monorepo structure visible from day one.
- **Cons:** Folder serves only as a marker — no real content.

**Recommendation:** **Option A** — Don't manufacture placeholder content; the root CLAUDE.md already documents the monorepo intent.

**Decision:** A (Don't create `nextjs-project/` in Phase 01)

---

## TD-17: Remove `SPRING_JPA_HIBERNATE_DDL_AUTO`

**Context:** `compose.yaml` sets `SPRING_JPA_HIBERNATE_DDL_AUTO=update`. The project does not use JPA/Hibernate — it uses Spring Data JDBC + Liquibase. This variable is inert and misleading.

**Options:**

### Option A: Remove it
- Delete the env var from `compose.yaml`.
- **Pros:** Eliminates a misleading config. Liquibase is the schema authority; nothing else should imply schema mutation.
- **Cons:** None.

### Option B: Keep for hypothetical future JPA migration
- Leave it in case the project ever migrates to JPA.
- **Pros:** None — the var has no effect today.
- **Cons:** Confuses new developers who assume JPA is involved.

**Recommendation:** **Option A** — Remove it.

**Decision:** A (Remove `SPRING_JPA_HIBERNATE_DDL_AUTO` from `compose.yaml`)

---

## TD-18: Compose File Location

**Context:** `compose.yaml` lives inside `stream-tube-backend/`. Spring Boot's `spring-boot-docker-compose` module auto-detects a `compose.yaml` in the working directory. As more services arrive (object storage, queue, mail), location matters.

**Options:**

### Option A: Keep inside `stream-tube-backend/`
- Each subproject owns its own Compose file. Future frontend will have its own `compose.yaml`.
- **Pros:** Subproject autonomy. `spring-boot-docker-compose` auto-detects without configuration. Backend devs run `docker compose up` from the backend folder.
- **Cons:** Cross-cutting services (object storage shared with frontend) must be duplicated or referenced from a root file later.

### Option B: Move to monorepo root
- Single Compose orchestrating db, backend, future frontend, future queue, storage.
- **Pros:** Single command starts the whole stack.
- **Cons:** Breaks `spring-boot-docker-compose` auto-detect (needs `spring.docker.compose.file` property). Backend subproject can't `docker compose up` standalone.

**Recommendation:** **Option A** — Subproject ownership; auto-detect works.

**Decision:** A (Keep `compose.yaml` inside `stream-tube-backend/`)

---

## Decisions Summary

| ID | Decision | Recommendation | Choice |
|----|----------|---------------|--------|
| TD-01 | Root Java Package | `com.streamtube.backend` | A (`com.streamtube.backend`) |
| TD-02 | Configuration File Format | `application.yml` | A (`application.yml`) |
| TD-03 | Spring Profiles | default + dev + test | A (default + dev + test) |
| TD-04 | Lombok | Add Lombok | A (Lombok added) |
| TD-05 | Spring Boot Actuator | Actuator with `/health` + `/info` | A (Actuator, `/health` + `/info` only) |
| TD-06 | Placeholder HelloController | Remove, use `/actuator/health` | A (Remove `HelloController`) |
| TD-07 | Compose Postgres Service Name | `db` | A (`db`) |
| TD-08 | Local Dev Credentials | `.env` + inline defaults | A (`.env` gitignored + inline defaults) |
| TD-09 | Initial Liquibase Setup | Master + `001-extensions.yaml` | A (extensions changeset) |
| TD-10 | Code Quality Tooling | Spotless + EditorConfig | A (Spotless + EditorConfig) |
| TD-11 | Spotless Formatter Style | `google-java-format` (GOOGLE) | A (`google-java-format`, GOOGLE) |
| TD-12 | Java + Spring Boot Versions | Java 17 + Spring Boot 4.0.5 | A (keep current) |
| TD-13 | Global Exception Handler | Defer to Phase 02 | A (Defer) |
| TD-14 | Modulith Module Placeholders | All future modules | A (8 modules placeholder) |
| TD-15 | AI Foundation Scope | Audit + consolidation | A (Audit only) |
| TD-16 | `nextjs-project/` Placeholder | Don't create | A (Don't create) |
| TD-17 | `SPRING_JPA_HIBERNATE_DDL_AUTO` | Remove | A (Remove) |
| TD-18 | Compose File Location | Keep in `stream-tube-backend/` | A (Keep in subproject) |
