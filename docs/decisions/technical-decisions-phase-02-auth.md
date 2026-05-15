# Technical Decisions — Phase 02: Cadastro, Login e Gerenciamento de Conta

> **Phase:** 02 — Cadastro, Login e Gerenciamento de Conta
> **Status:** Finalized
> **Date:** 2026-05-14
> **Supersedes:** the original NestJS-oriented draft of 2026-04-07. Phase 01 standardized the stack on Java 17 + Spring Boot 4.0.5 + Spring Modulith + Spring Data JDBC + Liquibase, so every TD below is rewritten with Spring-native options. Stack-agnostic conclusions (refresh rotation, opaque DB tokens, handle generation algorithm) are preserved.

---

## TD-01: Password Hashing Algorithm

**Context:** User registration requires securely storing passwords. The choice of hashing algorithm impacts security against brute-force/GPU attacks and runtime performance.

**Options:**

### Option A: BCryptPasswordEncoder (Spring Security)
- Spring Security's default password encoder. `org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder` ships with `spring-security-crypto`. Adaptive cost factor (default 10).
- **Pros:** Zero extra dependencies — included transitively by `spring-boot-starter-security`. Battle-tested since 1999. Simple API. Most Spring Boot tutorials and examples use it.
- **Cons:** Fixed 4 KB memory per hash — vulnerable to GPU/ASIC attacks at scale. No memory-hardness tuning.

### Option B: Argon2PasswordEncoder (Spring Security + BouncyCastle)
- `org.springframework.security.crypto.argon2.Argon2PasswordEncoder` from `spring-security-crypto`. Requires `org.bouncycastle:bcprov-jdk18on` on the classpath for the Argon2 implementation. Argon2id variant, OWASP-recommended parameters (memory ≥ 19 MiB, iterations ≥ 2).
- **Pros:** OWASP-recommended algorithm for new projects (2025+). Memory-hard — GPU/ASIC attacks are orders of magnitude more expensive than bcrypt. Three tunable parameters (memory, iterations, parallelism). Built into Spring Security, no third-party wrapper.
- **Cons:** Adds one dependency (BouncyCastle, ~7 MB). Slightly slower default cost than bcrypt (intentional — memory-hardness costs CPU). Tuning the memory parameter requires understanding the deployment hardware.

### Option C: argon2-jvm (third-party)
- `de.mkammerer:argon2-jvm` — pure Java + native fallback Argon2 implementation. Wrap manually in a custom `PasswordEncoder`.
- **Pros:** Avoids BouncyCastle. Self-contained.
- **Cons:** Non-standard integration — must write a custom `PasswordEncoder` bean. Less aligned with Spring Security conventions. Two ways to fail (custom wrapper + library upgrades).

**Recommendation:** **Option B (Argon2PasswordEncoder)** — OWASP guidance is the deciding factor for a greenfield project in 2026. BouncyCastle is a standard dependency in Spring Security 6 projects that opt into Argon2 or modern crypto; the size is acceptable. Using Spring Security's built-in encoder avoids a custom wrapper.

**Decision:** B (Argon2PasswordEncoder via `spring-security-crypto` + `org.bouncycastle:bcprov-jdk18on`)

---

## TD-02: Authentication Architecture (Spring Security + JWT)

**Context:** Phase 02 introduces the first authenticated endpoints. Spring Security 6 offers two main paths for JWT-based auth: use the OAuth 2.0 Resource Server starter (declarative, RFC-aligned, uses Nimbus JOSE+JWT internally) or build custom `OncePerRequestFilter`s combined with a JWT library (JJWT or Nimbus). The decision affects every authenticated endpoint from Phase 02 onward.

**Options:**

### Option A: `spring-boot-starter-oauth2-resource-server` (Nimbus internally)
- Treats the API as an OAuth 2.0 Resource Server. `JwtDecoder` validates incoming access tokens; `JwtEncoder` (configured with a `JWKSource`) issues both access and refresh tokens. Declarative `SecurityFilterChain` uses `.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`. Built on Nimbus JOSE+JWT (transitive dependency, no manual coordinate needed).
- **Pros:** Spring-native, declarative — minimal custom filter code. Standards-aligned (RFC 7519 + RFC 6750 bearer tokens). `JwtAuthenticationConverter` maps custom claims to authorities cleanly. Future-proof for adding OAuth/OIDC providers (Google, GitHub). Maintained by the Spring Security team. Easy to plug an `Authentication` into method-security annotations (`@PreAuthorize`).
- **Cons:** Heavier dependency footprint than JJWT-only. Mental model of "Resource Server" is slightly overkill for a first-party API. Issuing tokens (not just validating them) requires explicit `JwtEncoder` + `NimbusJwtEncoder` wiring; less common in tutorials.

### Option B: `spring-boot-starter-security` + JJWT custom filter
- `spring-boot-starter-security` for the filter chain. `io.jsonwebtoken:jjwt-api` (+ `jjwt-impl`, `jjwt-jackson` at runtime) for token issuing and parsing. Custom `OncePerRequestFilter` reads `Authorization: Bearer <token>`, parses it with JJWT, and populates `SecurityContextHolder`.
- **Pros:** Full control over claim format and validation. JJWT is the most popular Java JWT library (~10M monthly downloads). Smaller dependency footprint.
- **Cons:** More custom code to maintain (filter, AuthenticationManager wiring, exception translation). Easier to introduce subtle security bugs in the custom filter. No built-in `JwtAuthenticationToken` — must build one. Less idiomatic Spring Security 6.

### Option C: `spring-boot-starter-security` + Nimbus JOSE+JWT directly
- Same as Option B but uses Nimbus JOSE+JWT (`com.nimbusds:nimbus-jose-jwt`) as the JWT library, wired through a custom filter.
- **Pros:** Same library as Resource Server underneath, so semantics are consistent if migrating later. Mature, well-documented JOSE library.
- **Cons:** Lower-level API than JJWT. Custom filter still required — combines the downsides of B with a less ergonomic JWT API.

**Recommendation:** **Option A (Resource Server + Nimbus)** — Declarative configuration eliminates a class of custom-filter bugs. Spring Security's `JwtAuthenticationToken` and `JwtAuthenticationConverter` integrate cleanly with method security. Future OAuth/OIDC providers (the project plan does not require them now, but the door stays open) drop in with no rework. The "Resource Server" framing is fine for a first-party API — it just means "this server validates incoming JWTs".

**Decision:** A (`spring-boot-starter-oauth2-resource-server` for validation + `NimbusJwtEncoder` for issuance, both access and refresh tokens)

---

## TD-03: Refresh Token Strategy

**Context:** JWT access tokens should be short-lived (15 minutes). A refresh token strategy is needed to maintain sessions without forcing re-login. The choice affects security, database load, and complexity.

**Options:**

### Option A: Refresh Token Rotation with Family Tracking (stored in DB)
- Each refresh generates a new access token AND a new refresh token. The old refresh token is invalidated. If a reused (old) token is detected, all tokens in the same family are revoked (theft detection). The family is identified by a `family_id` (UUID); rotation updates the row but preserves the family. Tokens stored in a `refresh_tokens` table in PostgreSQL via Spring Data JDBC.
- **Pros:** Theft detection — reuse of an old token signals compromise and triggers family revocation. Each token is single-use (within the grace window), limiting attack surface. RFC 6819 / OAuth 2.0 Security BCP-aligned. PostgreSQL is already in the stack.
- **Cons:** DB write on every refresh. Concurrent refresh from the same client can mis-trigger theft detection without a grace mechanism (addressed below). Family-tracking schema is slightly more complex than a flat blacklist.

### Option B: Long-lived Refresh Token with Blacklist
- Refresh token issued at login, stored in DB. Remains valid until expiry (e.g., 30 days) or explicit revocation. On logout or password change, the token is added to a blacklist.
- **Pros:** Simpler — no rotation logic. Fewer DB writes.
- **Cons:** Stolen refresh token remains valid until manual revocation — no automatic theft detection. Less secure for a public-facing platform.

### Option C: Token Versioning (per-user counter)
- A `token_version` column on the `users` table; the token carries the version. Verification compares the token version to the DB. Bulk revocation = increment version.
- **Pros:** Single column per user. O(1) lookups.
- **Cons:** All-or-nothing revocation — cannot revoke a single device. No per-session telemetry.

### Race-condition handling for Option A (sub-decision)

Concurrent `/auth/refresh` requests from the same client (e.g., a flaky network triggering a retry) would normally trip theft detection. Three ways to handle this:

- **A1: Strict single-use** — first request rotates; second request triggers theft detection and revokes the family. Most secure, but a single network retry can log the user out.
- **A2: Grace period (30s)** — after rotation, the old refresh token remains valid for 30 seconds and returns the same new token pair (idempotent). After 30 seconds, reuse triggers theft detection. Implemented by recording `rotated_at` and returning the most-recently-issued pair when an old token is presented within the window.
- **A3: SELECT FOR UPDATE serialization** — the rotation transaction locks the row; concurrent requests serialize. Heavier DB load, but no special grace logic.

**Recommendation:** **Option A with A2 (30s grace period)** — Strongest security model with theft detection, while tolerating realistic concurrent refresh attempts. The 30-second window is short enough that a stolen token used by an attacker after 30s still triggers revocation, but long enough to absorb legitimate retries.

**Decision:** A + A2 (Refresh Token Rotation with family tracking; 30s grace period on rotated tokens)

---

## TD-04: Email Confirmation & Password Reset Tokens

**Context:** Two flows require tokens sent via email: account confirmation and password reset. The choice is between stateless JWT-based tokens and stateful random tokens stored in the database.

**Options:**

### Option A: Signed JWT (stateless)
- Generate a JWT carrying `userId`, purpose (`CONFIRM`/`RESET`), and `exp`. Verify by signature + expiration — no DB lookup.
- **Pros:** No table or cleanup job.
- **Cons:** Cannot be revoked. New reset request does not invalidate previous tokens. URL contains a base64-readable payload.

### Option B: Random Opaque Tokens in Database
- Generate 32 random bytes via `java.security.SecureRandom`, hex-encode (64 chars), hash with SHA-256 before storing in `email_tokens` table (`user_id`, `type`, `token_hash`, `expires_at`, `used_at`). Verify by hashing the presented token and looking up the hash.
- **Pros:** Revocable — new request invalidates previous tokens of the same type. Per-token usage tracking (`used_at` for single-use enforcement). Token is opaque — no data leakage. Hash-at-rest protects against DB exfiltration. Independent of the JWT signing key.
- **Cons:** Requires a table and a periodic cleanup of expired rows. DB lookup on each verification.

**Recommendation:** **Option B (Random Opaque Tokens in DB)** — Revocability matters for password reset (UX expectation: requesting a new reset email invalidates the previous one). The table doubles as a uniform store for both confirmation and reset tokens. `SecureRandom.getInstanceStrong()` produces cryptographically appropriate token material.

**Decision:** B (Random opaque tokens via `SecureRandom`, SHA-256 hashed at rest, stored in `email_tokens` table)

---

## TD-05: Email Sending Infrastructure

**Context:** Phase 02 requires sending transactional emails (account confirmation, password reset). The architecture diagram lists "Email Service (SMTP)" as a container. The dev environment must run offline; tests must not depend on external SMTP.

**Options:**

### Option A: `spring-boot-starter-mail` + Thymeleaf + Mailpit (dev) / stub or GreenMail (test)
- `spring-boot-starter-mail` autoconfigures `JavaMailSender` from `spring.mail.*` properties. `spring-boot-starter-thymeleaf` renders HTML email bodies from `src/main/resources/templates/email/*.html`. Local development uses **Mailpit** (`axllent/mailpit`) added to `compose.yaml`: SMTP on port 1025, web UI on port 8025. Test profile uses a stubbed/mock `JavaMailSender` or in-JVM GreenMail for integration tests.
- **Pros:** Spring-native — `JavaMailSender` is a DI-friendly interface. Thymeleaf is already the default Spring template engine. Mailpit catches every email and exposes a web UI for inspection — ideal dev UX. Tests can be deterministic without container startup if `JavaMailSender` is stubbed. Migrates to any production SMTP provider (SendGrid, SES, Postfix) by changing properties.
- **Cons:** Two dependencies (`spring-boot-starter-mail`, `spring-boot-starter-thymeleaf`). Adds one container to `compose.yaml`.

### Option B: `spring-boot-starter-mail` + Java text blocks (no template engine)
- Same `JavaMailSender` but email bodies are plain Java text blocks with `%s` substitution.
- **Pros:** No Thymeleaf dependency.
- **Cons:** No HTML preview while editing. Mixing Java code and email HTML is awkward. Refactor required as soon as a third template lands.

### Option C: Resend API (external service)
- HTTP API. No SMTP server needed.
- **Pros:** Excellent developer experience.
- **Cons:** External dependency in dev. Diverges from the architecture diagram's SMTP container. Requires internet during development.

**Recommendation:** **Option A** — Spring-native integration, no vendor lock-in, matches the architecture diagram, Mailpit gives a great local UX, Thymeleaf is already idiomatic. Test layer uses a stub by default (deterministic and fast); integration tests that need to assert SMTP behavior can opt into GreenMail.

**Decision:** A (`spring-boot-starter-mail` + `spring-boot-starter-thymeleaf` + Mailpit container in `compose.yaml`; tests stub `JavaMailSender`)

---

## TD-06: Request Validation Library

**Context:** Phase 02 introduces the first HTTP endpoints with user input (registration, login, password reset). DTOs must be validated before reaching the service layer.

**Options:**

### Option A: Jakarta Bean Validation (Hibernate Validator)
- `spring-boot-starter-validation` brings `jakarta.validation` API + Hibernate Validator implementation. Annotate DTO fields with `@NotBlank`, `@Email`, `@Size`, `@Pattern`, etc. Controller parameters annotated with `@Valid` trigger validation. `MethodArgumentNotValidException` raised on failure is mapped to the project's error shape by the global `@RestControllerAdvice` (TD-07).
- **Pros:** Jakarta EE standard. Integrated with Spring MVC out of the box. Extensive built-in constraints. Custom constraints are straightforward (`@Constraint` + `ConstraintValidator`). DTOs serve as living documentation.
- **Cons:** Annotation-based — validation rules and Java types are co-located but not type-checked together (e.g., `@Size(max=255)` on a `String` is fine; on an `Integer` is silently wrong).

### Option B: Manual validation in services
- No annotations. Each service method validates inputs explicitly with `if`/`throw`.
- **Pros:** Maximum control.
- **Cons:** Validation logic scattered across services. No declarative contract on DTOs. Far more boilerplate.

**Recommendation:** **Option A (Jakarta Bean Validation)** — Standard, idiomatic, declarative. No real competitor in the Spring ecosystem.

**Decision:** A (Jakarta Bean Validation via `spring-boot-starter-validation` + Hibernate Validator)

---

## TD-07: Error Response Standardization

**Context:** Phase 02 is the first phase introducing public HTTP endpoints. The error response format defined here becomes the contract for all subsequent phases. Phase 01 TD-13 deferred this decision to Phase 02 because no real domain errors existed yet.

**Options:**

### Option A: Custom `{ statusCode, error, message }` via `@RestControllerAdvice`
- Define `DomainException` (abstract) with subclasses per error code (`EmailAlreadyExistsException`, `InvalidCredentialsException`, etc.). Each subclass carries a stable `errorCode` and HTTP status. A `@RestControllerAdvice` `GlobalExceptionHandler` catches `DomainException`, `MethodArgumentNotValidException`, and a fallback `Exception`, returning `{ statusCode, error, message }` in every case.
- **Pros:** Machine-readable error codes (e.g., `EMAIL_ALREADY_EXISTS`) that the Next.js frontend can switch on. Catalog is explicit and testable. Simple, lightweight shape. Domain exceptions co-locate the HTTP semantics with the business rule.
- **Cons:** Custom advice to maintain (one class). Diverges from Spring Boot 3+'s default `ProblemDetail`.

### Option B: Spring `ProblemDetail` (RFC 9457)
- Spring Boot 3+ returns RFC 9457 `ProblemDetail` by default for unhandled exceptions. Domain exceptions can extend `ErrorResponseException` and supply `ProblemDetail` instances; domain codes go in `properties.errorCode`.
- **Pros:** IETF standard. Future-proof for API tooling.
- **Cons:** More verbose response (`type`, `title`, `status`, `detail`, `instance`, `properties.errorCode`). Frontend reads `properties.errorCode` instead of a top-level `error` field. URI-based `type` field needs a documentation site to point to — overkill for a first-party API.

### Option C: ProblemDetail + flat `error` extension
- Use `ProblemDetail` but add an extension property as a top-level field. Hybrid.
- **Pros:** Standards-aligned + simple top-level code.
- **Cons:** Deviates from RFC 9457 (extensions are nested under `properties`). Loses the benefit of either approach.

**Recommendation:** **Option A** — Single first-party consumer (Next.js), so RFC 9457's URI-based metadata is unused. Custom shape costs one advice class and produces a cleaner client experience. Phase 01 TD-13 explicitly framed this as Phase 02's choice.

**Decision:** A (Custom `{ statusCode, error, message }` shape, implemented in a `@RestControllerAdvice` global handler in `com.streamtube.backend.common.web`)

**Error response shape (canonical, inherited by all later phases in this subproject):**

```json
{
  "statusCode": 409,
  "error": "EMAIL_ALREADY_EXISTS",
  "message": "Email is already registered"
}
```

- `statusCode` — integer; matches the HTTP status.
- `error` — string; `SCREAMING_SNAKE_CASE` domain code from the Error Catalog. For framework-level errors without a domain code (e.g., 400 validation failure), value is `VALIDATION_ERROR`; for 500-level fallbacks, `INTERNAL_ERROR`.
- `message` — string; human-readable, in English (locale support deferred).

---

## TD-08: Rate Limiting Strategy

**Context:** Auth endpoints (`/auth/login`, `/auth/register`, `/auth/forgot-password`) are prime brute-force targets. A rate-limiting mechanism is needed to cap requests per IP within a time window.

**Options:**

### Option A: Bucket4j with in-memory cache
- `com.bucket4j:bucket4j-core` (or `bucket4j-spring-boot-starter`). Token-bucket algorithm. Apply via a custom `HandlerInterceptor` keyed by client IP. In-memory `ConcurrentHashMap<String, Bucket>` storage.
- **Pros:** Dedicated rate-limiting library. Token-bucket gives smooth throughput with burst capacity. Many examples.
- **Cons:** Manual interceptor wiring. In-memory storage doesn't scale across instances (acceptable for single-instance phase).

### Option B: Resilience4j RateLimiter
- `io.github.resilience4j:resilience4j-spring-boot3`. Method-level `@RateLimiter(name = "auth")` annotation; configuration via `application.yml`. Backed by an in-memory atomic-state algorithm (semaphore-based). Per-IP keying requires a custom `RateLimiterRegistry` lookup combined with a manual aspect or interceptor that derives the bucket key.
- **Pros:** Already part of the Resilience4j family (useful later for circuit breakers and retries in Phase 03+). Declarative annotations. Active maintenance, large ecosystem.
- **Cons:** Per-IP keying is not built-in — must implement a custom `KeyResolver` and either wrap with `RateLimiterRegistry.rateLimiter(key, supplier)` or use a custom interceptor. Documentation is light on per-key rate limiting compared to Bucket4j.

### Option C: Custom `HandlerInterceptor` + `ConcurrentHashMap`
- No external dep. Hand-rolled sliding-window or token-bucket implementation.
- **Pros:** Zero dependencies.
- **Cons:** Reinventing a well-solved problem. Easy to introduce correctness bugs (eviction, race conditions).

**Recommendation:** **Option B (Resilience4j)** — Reuses the Resilience4j family the project will likely adopt anyway in Phase 03 (retries, bulkheads, circuit breakers for the upload pipeline). Per-IP keying is implemented once and reused across endpoints. Annotations keep configuration co-located with the controller methods.

**Decision:** B (Resilience4j RateLimiter via `io.github.resilience4j:resilience4j-spring-boot3` with a custom per-IP key resolver; in-memory storage; applied to `/auth/login`, `/auth/register`, `/auth/forgot-password`)

---

## TD-09: Persistence Approach for Phase 02 Entities

**Context:** Phase 02 introduces the first business tables (`users`, `channels`, `refresh_tokens`, `email_tokens`). Phase 01 chose Liquibase as the schema authority and removed `SPRING_JPA_HIBERNATE_DDL_AUTO` (TD-17). The persistence layer for entities must be confirmed now: Spring Data JDBC, Spring Data JPA, or plain `JdbcTemplate`.

**Options:**

### Option A: Spring Data JDBC
- `spring-boot-starter-data-jdbc`. Aggregate-root-based persistence: each repository maps a root entity and its owned associations. Queries are explicit (`@Query`) or derived (`findByEmail`). No lazy loading, no proxy magic. Schema is owned by Liquibase.
- **Pros:** Lightweight — no Hibernate. Domain-Driven-Design-friendly (aggregate roots). Loading semantics are explicit, removing N+1 surprises. Plays well with Spring Modulith (each module owns its aggregate, repositories don't leak across boundaries). Schema-first via Liquibase is consistent with Phase 01.
- **Cons:** No lazy loading — must load the full aggregate. Less feature-rich than JPA (no `@EntityGraph`, no second-level cache). Smaller ecosystem of tutorials.

### Option B: Spring Data JPA (Hibernate)
- `spring-boot-starter-data-jpa`. Full JPA stack with Hibernate. Lazy loading, dirty checking, JPQL.
- **Pros:** Most popular Spring persistence stack. Massive ecosystem. Powerful when relationships are complex.
- **Cons:** Reintroduces Hibernate after Phase 01 TD-17 deliberately removed JPA references. Schema management drift risk (Hibernate's auto-DDL vs. Liquibase). N+1 and lazy-loading exceptions are common pitfalls. Heavier mental model for a modular monolith with clear aggregates.

### Option C: Plain `JdbcTemplate` / `NamedParameterJdbcTemplate`
- No Spring Data abstraction. Raw SQL + manual mapping.
- **Pros:** Maximum control.
- **Cons:** Heavy boilerplate for CRUD. No repository abstraction.

**Recommendation:** **Option A (Spring Data JDBC)** — Aligns with Phase 01's stack decisions (Liquibase as schema authority, no Hibernate). Aggregate boundaries fit naturally with Spring Modulith's module ownership rules (TD-11). The trade-off (no lazy loading) is acceptable for Phase 02's small aggregates (User+Channel, RefreshToken, EmailToken).

**Decision:** A (`spring-boot-starter-data-jdbc` — aggregate-root repositories, schema owned by Liquibase)

---

## TD-10: Channel Handle Generation and Collision Strategy

**Context:** On registration, a default channel handle must be generated automatically from the email prefix (the part before `@`). The handle is the user's public identifier and must be URL-safe, unique, and reproducible even when the email prefix contains only non-ASCII or special characters. Two questions: (1) how to derive the handle deterministically, and (2) how to resolve collisions atomically under concurrent registration.

**Generation algorithm — options:**

### Option A: Strict allowlist `[a-z0-9_]`, empty fallback to `user_<8-hex>`
- Lowercase the email prefix, strip everything outside `[a-z0-9_]`, truncate to 46 characters (leaving 4 chars for a collision suffix within the 50-char column limit). If the sanitized result is empty, generate `user_` + 8 random hex characters.
- **Pros:** URL-safe, predictable. Handles the degenerate case (prefix all special chars). No transliteration library.
- **Cons:** Strips hyphens common in real emails.

### Option B: Broader allowlist `[a-z0-9_-]` (include hyphen)
- Same as A, plus hyphens.
- **Pros:** Preserves hyphenated email prefixes.
- **Cons:** Leading/trailing hyphens require trimming. Some platforms disallow them in usernames.

### Option C: Transliterate then allowlist
- ICU4J or `any-ascii`-equivalent: convert non-ASCII to ASCII, then apply A's allowlist.
- **Pros:** Preserves more original intent for non-Latin scripts.
- **Cons:** Adds a transliteration dependency. Locale-dependent results.

**Collision strategy — options:**

### Option C1: DB UNIQUE + retry with random suffix
- `channels.handle` has a `UNIQUE` constraint. The first insert uses the generated handle as-is. On `DataIntegrityViolationException`, append `_` + 3 random hex chars and retry (up to 5 attempts). Concurrent registrations both succeed with distinct handles. The UNIQUE constraint is the source of truth — no SELECT-then-INSERT race.
- **Pros:** Atomic at the DB level. Simple. Most registrations succeed on the first attempt; retry only triggers on actual collision.
- **Cons:** Up to 5 INSERT attempts in the absolute worst case (effectively never, given 3-hex = 4096 possibilities per retry).

### Option C2: Pre-check uniqueness, then insert
- Query for existing handle, increment counter (`_2`, `_3`, …), then insert.
- **Pros:** Sequential numbering looks tidier.
- **Cons:** Race window between SELECT and INSERT — must still handle unique constraint violations.

### Option C3: Always append `_<8-hex>`
- Every handle is `<base>_<8-hex>`. No collisions.
- **Pros:** No collision logic at all.
- **Cons:** Ugly handles by default, defeats the "prefix becomes handle" UX.

**Recommendation:** **Option A (strict allowlist) + Option C1 (DB UNIQUE + retry with random suffix)** — Strict allowlist is portable and dependency-free. Random-suffix retry is atomic, simple, and doesn't degrade the common case.

**Decision:**
- Generation: A — strict `[a-z0-9_]` allowlist, truncate at 46 chars, `user_<8-hex>` fallback when sanitization yields empty.
- Collision: C1 — `channels.handle UNIQUE` enforced at the DB; on violation, append `_` + 3 random hex chars and retry (max 5 attempts; if still failing, raise `HANDLE_GENERATION_FAILED`).

---

## TD-11: Spring Modulith Module Ownership for Phase 02 Entities

**Context:** Phase 01 SI-01.2 created the eight Modulith module placeholders (`common`, `users`, `channels`, `auth`, `videos`, `comments`, `interactions`, `categories`). Phase 02 introduces the first real entities (`User`, `Channel`, `RefreshToken`, `EmailToken`). Module ownership must be confirmed so `ApplicationModules.verify()` continues to pass and cross-module dependencies are explicit.

**Options:**

### Option A: `users` owns `User` + `Channel`; `auth` owns `RefreshToken` + `EmailToken`
- `users` module is the source of identity (User aggregate + Channel aggregate; Channel is the public face of a User). `auth` module owns auth-specific persistence: refresh tokens and email tokens. `auth` depends on `users` for user lookups. The `channels` module placeholder remains empty in Phase 02 (it will own public channel pages and channel-side video listings in Phase 04).
- **Pros:** Identity (User + Channel) and authentication (tokens) cleanly separated. Registration orchestrated within `users` (creates User + Channel in one transaction) with `auth` triggering an email-token creation as a side effect. `channels` module reserved for the page/listing concerns added later.
- **Cons:** The `channels` module exists but is empty in Phase 02.

### Option B: `users` owns `User`; `channels` owns `Channel`; `auth` owns tokens
- Three modules participate in registration. `auth` orchestrates by calling `users` and `channels`.
- **Pros:** Stricter separation.
- **Cons:** Cross-module orchestration during a single transaction (auth → users → channels) is more complex. Modulith transactions are easier when an aggregate lives in one module.

### Option C: `auth` owns everything for Phase 02
- All four tables in `auth`. Move to `users`/`channels` in a later phase.
- **Pros:** Single-module simplicity.
- **Cons:** Premature consolidation. Later move is disruptive (Liquibase changesets, repository moves, tests).

**Recommendation:** **Option A** — Aligns the Phase 01 placeholders with how the domain actually splits. Channel is a public projection of User identity, so they belong together. Token persistence is an auth concern.

**Decision:** A — `users` owns `User` + `Channel` aggregates and their repositories; `auth` owns `RefreshToken` + `EmailToken` aggregates and their repositories. `auth` declares `allowedDependencies = "users, common"` via `@ApplicationModule`.

---

## TD-12: Local Email Service Container (Mailpit in `compose.yaml`)

**Context:** TD-05 chose `spring-boot-starter-mail` over a managed API. Local development needs an SMTP server that the Spring app can reach inside the Docker Compose network without depending on internet or paid services.

**Options:**

### Option A: Mailpit (`axllent/mailpit`)
- Single Go binary; fast; SMTP on 1025, HTTP UI on 8025. Catches every outgoing email and exposes it in a web UI for inspection. Recent, actively maintained.
- **Pros:** Best-in-class developer UX. Tiny image (~15 MB). One container, two ports. Inspectable inbox per developer.
- **Cons:** None significant.

### Option B: MailHog (`mailhog/mailhog`)
- Older incumbent. Same idea — SMTP server with a catching inbox + UI.
- **Pros:** Mature.
- **Cons:** No longer actively maintained (last commit years old). Mailpit is the recommended successor.

### Option C: GreenMail container
- JVM-based SMTP test server. Designed for tests; less polished as a dev tool.
- **Pros:** Same engine usable in tests + dev.
- **Cons:** Heavier image. Less ergonomic UI.

**Recommendation:** **Option A (Mailpit)** — Active maintenance and best UI carry the decision.

**Decision:** A — Add a `mailpit` service to `stream-tube-backend/compose.yaml` (image `axllent/mailpit:latest`, ports `1025:1025` SMTP and `8025:8025` HTTP). `application-dev.yml` sets `spring.mail.host=mailpit`, `spring.mail.port=1025`. Tests use a stubbed `JavaMailSender` bean; GreenMail is reserved for explicit SMTP integration tests if needed in later phases.

---

## Decisions Summary

| ID | Decision | Recommendation | Choice |
|----|----------|---------------|--------|
| TD-01 | Password Hashing Algorithm | Argon2PasswordEncoder + BouncyCastle | B (Argon2id) |
| TD-02 | Authentication Architecture | Resource Server + Nimbus | A (`spring-boot-starter-oauth2-resource-server` + `NimbusJwtEncoder`) |
| TD-03 | Refresh Token Strategy | Rotation with 30s grace period | A + A2 (rotation, family tracking, 30s grace) |
| TD-04 | Email Confirmation & Reset Tokens | Opaque tokens in DB (SecureRandom, SHA-256 at rest) | B (Random opaque tokens) |
| TD-05 | Email Sending Infrastructure | `spring-boot-starter-mail` + Thymeleaf + Mailpit | A |
| TD-06 | Request Validation | Jakarta Bean Validation | A (Hibernate Validator via `spring-boot-starter-validation`) |
| TD-07 | Error Response Standardization | Custom `{ statusCode, error, message }` via `@RestControllerAdvice` | A |
| TD-08 | Rate Limiting Strategy | Resilience4j RateLimiter | B (Resilience4j, per-IP, in-memory) |
| TD-09 | Persistence Approach | Spring Data JDBC | A |
| TD-10 | Channel Handle Generation + Collision | Strict allowlist + DB UNIQUE retry | A + C1 |
| TD-11 | Modulith Module Ownership | `users` owns User+Channel; `auth` owns tokens | A |
| TD-12 | Local Email Service Container | Mailpit in `compose.yaml` | A |
