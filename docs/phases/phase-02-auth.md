# Phase 02 — Cadastro, Login e Gerenciamento de Conta

## Objective

Deliver the complete backend authentication flow on Spring Boot: user registration with auto-generated channel handle, email confirmation, login with JWT access + refresh tokens (rotation with theft detection), logout, and password recovery — backed by `spring-boot-starter-oauth2-resource-server`, Spring Data JDBC, Mailpit for local email, Argon2id password hashing, and Resilience4j rate limiting on sensitive endpoints. Establishes the canonical error response shape, the validation pipeline, the email subsystem, and the module ownership (`users` owns User+Channel; `auth` owns RefreshToken+EmailToken) that later phases inherit.

---

## Step Implementations

### SI-02.1 — Common error handling: `DomainException` + `@RestControllerAdvice` + Bean Validation

**Description:** Establish the canonical error response shape (`{ statusCode, error, message }`, TD-07) that every subsequent phase in `stream-tube-backend` inherits. Wire Jakarta Bean Validation into Spring MVC and normalize validation errors into the same shape. Lives in `com.streamtube.backend.common.web`.

**Technical actions:**

- Add `org.springframework.boot:spring-boot-starter-validation` (managed by the Spring Boot parent BOM) to `pom.xml`.
- In `com.streamtube.backend.common.web`, create the `ApiErrorResponse` record `{ int statusCode, String error, String message }` and an abstract `DomainException` carrying `errorCode` (String, SCREAMING_SNAKE_CASE) and `status` (`org.springframework.http.HttpStatus`).
- Create `GlobalExceptionHandler` annotated with `@RestControllerAdvice` handling: (a) `DomainException` → `{ status, errorCode, message }`; (b) `MethodArgumentNotValidException` and `HandlerMethodValidationException` → 400 + `error: "VALIDATION_ERROR"` with the first field violation as `message`; (c) fallback `Exception` → 500 + `error: "INTERNAL_ERROR"` + generic message (do not leak stack traces).
- Configure `application.yml` `server.error.include-message: never` and `server.error.include-stacktrace: never` so the global handler is the only error formatter.

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| GlobalExceptionHandlerTest.java | Unit | Each handler branch (DomainException, validation exception, fallback) builds the expected `ApiErrorResponse`. |
| GlobalExceptionHandlerIntegrationTest.java | Integration | Hitting a test-only controller that throws `DomainException`, posts invalid body, or throws unchecked returns the expected JSON shape and HTTP status. |

**Dependencies:** None

**Acceptance criteria:**

- A controller throwing a `DomainException` subclass returns a JSON body matching `{ statusCode, error, message }` with the exception's `errorCode` in `error` and its HTTP status reflected in `statusCode`.
- A controller receiving a `@Valid`-annotated body with violated constraints returns 400 with `error: "VALIDATION_ERROR"` and a `message` describing the first violation.
- An uncaught `RuntimeException` from a controller returns 500 with `error: "INTERNAL_ERROR"` and a generic message — no stack trace or Spring `ProblemDetail` fields are present in the body.
- `GET /actuator/health` (a Spring-managed endpoint) is unaffected by the advice and still returns the standard Actuator JSON.

---

### SI-02.2 — Common security infrastructure: Argon2 + JWT (Resource Server) + `SecurityFilterChain`

**Description:** Bring up Spring Security with `spring-boot-starter-oauth2-resource-server`. Provide the `PasswordEncoder` (Argon2id via `Argon2PasswordEncoder` + BouncyCastle, TD-01), the `JwtEncoder` + `JwtDecoder` beans backed by an RSA keypair (TD-02), and the global `SecurityFilterChain` that classifies endpoints. No JWT issuance flows are implemented yet — those live in SI-02.9 (login). Lives in `com.streamtube.backend.common.security`.

**Technical actions:**

- Add `org.springframework.boot:spring-boot-starter-security`, `org.springframework.boot:spring-boot-starter-oauth2-resource-server`, and `org.bouncycastle:bcprov-jdk18on` (version managed by Spring Boot parent BOM) to `pom.xml`. Nimbus JOSE+JWT is pulled in transitively by the OAuth2 resource server starter.
- Create `JwtKeyProperties` (`@ConfigurationProperties("streamtube.security.jwt")`) exposing PEM-encoded RSA `privateKey` and `publicKey`. Populate `application-dev.yml` and `application-test.yml` with a development RSA keypair (generated once and checked in for dev/test only — never reused in prod).
- Create the security configuration class (`@Configuration @EnableWebSecurity`) defining: (a) `PasswordEncoder` → `new Argon2PasswordEncoder(16, 32, 1, 19_456, 2)` (OWASP minimum: salt 16 B, hash 32 B, parallelism 1, memory 19 MiB, iterations 2); (b) `JwtDecoder` → `NimbusJwtDecoder.withPublicKey(rsaPublicKey).build()`; (c) `JwtEncoder` → `new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(new RSAKey.Builder(rsaPublicKey).privateKey(rsaPrivateKey).keyID("streamtube-dev").build())))`.
- Create `SecurityFilterChain` permitting `/actuator/health`, `/actuator/info`, and all `/auth/**` endpoints anonymously; requiring authentication for any other route; setting `sessionManagement(STATELESS)`, disabling CSRF (stateless JWT API), and wiring `oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))`.
- Provide `JwtAuthenticationConverter` mapping the `sub` claim to the principal (a `UserId` value object wrapping `UUID`) and an empty authorities list (roles introduced in later phases).

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| Argon2PasswordEncoderTest.java | Unit | `encode` then `matches` round-trips successfully; verifying an altered hash returns false. |
| SecurityFilterChainIntegrationTest.java | Integration | `/actuator/health` and `/auth/login` (placeholder) return 200 anonymously; a protected stub endpoint returns 401 anonymously and 200 with a valid JWT. |
| JwtEncoderDecoderIntegrationTest.java | Integration | A JWT encoded by `JwtEncoder` with a known `sub` claim decodes via `JwtDecoder` to the same `sub`; an expired token raises `JwtValidationException`. |

**Dependencies:** None

**Acceptance criteria:**

- A protected endpoint (e.g., a test-only `/secured/ping`) returns 401 without an `Authorization` header and 200 with a valid `Bearer <jwt>` header.
- `PasswordEncoder.encode("secret")` produces an Argon2id hash recognizable by its `$argon2id$` prefix; `PasswordEncoder.matches("secret", hash)` returns true and `matches("wrong", hash)` returns false.
- `JwtEncoder` produces tokens whose `sub` claim round-trips through `JwtDecoder`; tampering with the payload makes decoding fail with `JwtValidationException`.
- `/actuator/health` and all `/auth/**` placeholder routes are reachable without authentication.

---

### SI-02.3 — Common rate-limiting infrastructure: Resilience4j + per-IP `HandlerInterceptor`

**Description:** Add Resilience4j and wire a per-IP rate limiter to be applied (by SI-02.7, SI-02.9, SI-02.12) to brute-force-sensitive endpoints. Because Resilience4j's `@RateLimiter` annotation is per-name (not per-key), per-IP keying is implemented via a `RateLimiterRegistry` lookup inside a `HandlerInterceptor`. Lives in `com.streamtube.backend.common.ratelimit`.

**Technical actions:**

- Add `io.github.resilience4j:resilience4j-spring-boot3` (compatible with Spring Boot 4.0.x) to `pom.xml`.
- In `application.yml`, define a single `resilience4j.ratelimiter.instances.auth` instance with `limitForPeriod: 10`, `limitRefreshPeriod: 1m`, `timeoutDuration: 0` (refuse immediately when exceeded).
- Create `RateLimitInterceptor` (implements `HandlerInterceptor`): resolves the client IP from `X-Forwarded-For` (first IP) or falls back to `request.getRemoteAddr()`; calls `rateLimiterRegistry.rateLimiter("auth-" + ip, "auth")` (the second arg is the configuration name from `application.yml`); on `RequestNotPermitted` from `rateLimiter.acquirePermission()`, throws `RateLimitExceededException` (a new `DomainException` subclass — 429, `error: "RATE_LIMIT_EXCEEDED"`).
- Provide a `@Configuration` class implementing `WebMvcConfigurer.addInterceptors` that registers `RateLimitInterceptor` against path patterns supplied by a `RateLimitedPaths` bean (initially populated by SI-02.7, SI-02.9, SI-02.12).
- Add `RATE_LIMIT_EXCEEDED` to the Error Catalog (handled here so the catalog stays in one place per phase).

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| RateLimitInterceptorIntegrationTest.java | Integration | Issuing 11 requests in under 1 minute from the same IP against a test-only rate-limited endpoint returns 200 for the first 10 and 429 with `RATE_LIMIT_EXCEEDED` on the 11th; a different IP gets its own quota. |

**Dependencies:** SI-02.1 (for `DomainException` base)

**Acceptance criteria:**

- A path registered as rate-limited returns 429 with `{ statusCode: 429, error: "RATE_LIMIT_EXCEEDED", message: ... }` after exceeding 10 requests/minute from the same IP.
- The same path remains 200 from a different IP within the same window.
- `X-Forwarded-For: 1.2.3.4, 5.6.7.8` causes the limiter to key on `1.2.3.4` (first hop), not the request peer address.
- Non-rate-limited paths (e.g., `/actuator/health`) are not affected by the interceptor.

---

### SI-02.4 — Common email infrastructure: Mailpit + `spring-boot-starter-mail` + Thymeleaf + `EmailService`

**Description:** Stand up the transactional email pipeline. Add Mailpit to `compose.yaml` (TD-12). Add `spring-boot-starter-mail` + `spring-boot-starter-thymeleaf`. Expose an `EmailService` interface (in `com.streamtube.backend.common.email`) with `sendEmailConfirmation` and `sendPasswordReset` methods; implementation renders Thymeleaf HTML templates and dispatches via `JavaMailSender`. Test profile uses a stubbed `JavaMailSender` so unit/integration tests don't require SMTP.

**Technical actions:**

- Add `org.springframework.boot:spring-boot-starter-mail` and `org.springframework.boot:spring-boot-starter-thymeleaf` to `pom.xml`.
- Add a `mailpit` service to `stream-tube-backend/compose.yaml`: `image: axllent/mailpit:latest`, `ports: ["1025:1025", "8025:8025"]`, `restart: unless-stopped`. Configure `spring.mail.host=mailpit`, `spring.mail.port=1025`, `spring.mail.properties.mail.smtp.auth=false`, `spring.mail.properties.mail.smtp.starttls.enable=false` in `application-dev.yml`. In `application-test.yml`, override `spring.mail.host=localhost` and provide a `@Primary` test `JavaMailSender` stub bean (in `TestcontainersConfiguration` or a dedicated test config).
- Create `EmailService` interface with `sendEmailConfirmation(String to, String name, String confirmationLink)` and `sendPasswordReset(String to, String name, String resetLink)`. Implement `ThymeleafEmailService` (annotated `@Service`) using `JavaMailSender` + `SpringTemplateEngine`. Use `MimeMessageHelper` with `multipart=false`, `isHtml=true`.
- Create the two Thymeleaf templates under `src/main/resources/templates/email/`: `email-confirmation.html` (variables: `name`, `confirmationLink`) and `password-reset.html` (variables: `name`, `resetLink`). Keep templates minimal but valid HTML with inline styles.
- Add `streamtube.app.public-url` config property (default `http://localhost:8080` in dev) used by services to construct the email links (`{baseUrl}/auth/confirm-email?token={token}` and `{baseUrl}/auth/reset-password?token={token}` — the URL paths used by the future Next.js frontend; the backend endpoints accept the token in the request body, see SI-02.8 and SI-02.12).

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| ThymeleafEmailServiceTest.java | Unit | `sendEmailConfirmation` renders the template with the supplied variables and invokes `JavaMailSender.send(MimeMessage)` exactly once with the expected `to`, `subject`, and body containing the confirmation link. |
| EmailTemplateRenderTest.java | Unit | Rendering `email-confirmation.html` and `password-reset.html` with sample variables produces non-empty HTML containing the recipient name and the link URL. |

**Dependencies:** None

**Acceptance criteria:**

- `docker compose up -d` starts the `mailpit` service alongside `db` and `spring-boot-app`; the Mailpit UI is reachable at `http://localhost:8025`.
- Calling `emailService.sendEmailConfirmation("user@example.com", "Alice", "http://localhost:3000/auth/confirm-email?token=ABC")` in dev causes the email to appear in the Mailpit inbox with the rendered Thymeleaf template body containing `Alice` and `ABC`.
- In tests, the same call interacts only with the stubbed `JavaMailSender` (no real SMTP connection attempt).
- Both Thymeleaf templates render with valid HTML containing the supplied variables; missing variables render as empty strings (not as `null` literals).

---

### SI-02.5 — `users` module: User + Channel aggregates, repositories, HandleGenerator, schema

**Description:** Materialize the `users` module per TD-11 — owns the `User` and `Channel` aggregate roots. Includes the Liquibase changeset for both tables, the Spring Data JDBC entities, their repositories, and the `HandleGenerator` service implementing TD-10. The `users` module declares `common` as its only dependency.

**Technical actions:**

- Update `src/main/java/com/streamtube/backend/users/package-info.java` to declare `@ApplicationModule(displayName = "Users", allowedDependencies = {"common"})`.
- Create `src/main/resources/db/changelog/changes/002-users-and-channels.yaml` with two changesets: (a) `002-users-table` creating `users` (`id UUID PK DEFAULT gen_random_uuid()`, `email CITEXT NOT NULL UNIQUE`, `password_hash VARCHAR(255) NOT NULL`, `name VARCHAR(100)`, `email_confirmed_at TIMESTAMP WITH TIME ZONE`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()`, `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()`); (b) `002-channels-table` creating `channels` (`id UUID PK DEFAULT gen_random_uuid()`, `user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE`, `handle VARCHAR(50) NOT NULL UNIQUE`, `name VARCHAR(100) NOT NULL`, `description TEXT`, `created_at`, `updated_at`). Each changeset has a matching `<rollback>` dropping the table. `author: IA`.
- In `com.streamtube.backend.users.domain`, implement `User` and `Channel` as Spring Data JDBC aggregate roots: `@Table("users")` / `@Table("channels")`, `@Id` on `UUID id`, `Channel` uses `AggregateReference<User, UUID> userId` per Spring Data Relational conventions. Provide static factory methods `User.create(...)` and `Channel.create(...)` for the registration flow.
- In `com.streamtube.backend.users.persistence`, implement `UserRepository extends CrudRepository<User, UUID>` with `Optional<User> findByEmail(String email)` (CITEXT-aware) and `boolean existsByEmail(String email)`; implement `ChannelRepository extends CrudRepository<Channel, UUID>` with `Optional<Channel> findByUserId(UUID userId)` and `Optional<Channel> findByHandle(String handle)`.
- In `com.streamtube.backend.users.service`, implement `HandleGenerator` with `String fromEmail(String email)` applying TD-10's algorithm: lowercase the prefix (`email.substring(0, email.indexOf('@'))`), strip characters not in `[a-z0-9_]`, truncate to 46 chars; if the result is empty, return `"user_" + 8 random hex chars from SecureRandom`. The collision retry suffix is appended by the registration service (SI-02.7), not here.

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| HandleGeneratorTest.java | Unit | Allowlist applied, lowercased; non-ASCII prefixes (`"jöão"`, `"日本"`) and special-char-only prefixes fall back to `user_<8-hex>`; 46-char truncation enforced; deterministic given same input (except fallback). |
| UserRepositoryIntegrationTest.java | Integration | `@DataJdbcTest`: save + `findByEmail` round-trip is case-insensitive (CITEXT); `existsByEmail` returns true for an inserted row; saving two users with the same email (different case) violates the unique constraint. |
| ChannelRepositoryIntegrationTest.java | Integration | `@DataJdbcTest`: `findByUserId` and `findByHandle` work; the `(user_id)` and `(handle)` unique constraints prevent duplicates; deleting the user cascades to the channel. |

**Dependencies:** SI-02.1

**Acceptance criteria:**

- `ApplicationModules.of(...).verify()` continues to pass with the new `allowedDependencies = {"common"}` on the `users` module.
- After application startup, `SELECT * FROM users` and `SELECT * FROM channels` succeed against the Liquibase-managed schema; the FK on `channels.user_id` cascades on user deletion.
- `HandleGenerator.fromEmail("Alice.Smith@example.com")` returns `"alicesmith"`; `fromEmail("!!@example.com")` returns a value matching `user_[0-9a-f]{8}`; `fromEmail("a-really-long-prefix-with-many-characters-to-trigger-truncation@x.com")` returns exactly 46 characters.
- `userRepository.findByEmail("Alice@Example.COM")` matches a row inserted with `"alice@example.com"` (CITEXT semantics).

---

### SI-02.6 — `auth` module: RefreshToken + EmailToken aggregates, repositories, OpaqueTokenGenerator, schema

**Description:** Materialize the `auth` module per TD-11 — owns the `RefreshToken` and `EmailToken` aggregate roots. Includes the Liquibase changeset, Spring Data JDBC entities, repositories, and the `OpaqueTokenGenerator` producing `(rawHex, sha256Hash)` pairs per TD-04. The `auth` module declares `users` and `common` as its dependencies.

**Technical actions:**

- Update `src/main/java/com/streamtube/backend/auth/package-info.java` to declare `@ApplicationModule(displayName = "Auth", allowedDependencies = {"users", "common"})`.
- Create `src/main/resources/db/changelog/changes/003-auth-tokens.yaml` with two changesets: (a) `003-refresh-tokens-table` creating `refresh_tokens` (`id UUID PK DEFAULT gen_random_uuid()`, `user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE`, `family_id UUID NOT NULL`, `token_hash CHAR(64) NOT NULL UNIQUE`, `expires_at TIMESTAMP WITH TIME ZONE NOT NULL`, `rotated_at TIMESTAMP WITH TIME ZONE`, `rotated_to_id UUID REFERENCES refresh_tokens(id) ON DELETE SET NULL`, `revoked_at TIMESTAMP WITH TIME ZONE`, `revoked_reason VARCHAR(40)`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()`) with indexes on `(user_id)` and `(family_id)`; (b) `003-email-tokens-table` creating `email_tokens` (`id UUID PK`, `user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE`, `type VARCHAR(20) NOT NULL CHECK (type IN ('CONFIRM_EMAIL','RESET_PASSWORD'))`, `token_hash CHAR(64) NOT NULL UNIQUE`, `expires_at TIMESTAMP WITH TIME ZONE NOT NULL`, `used_at TIMESTAMP WITH TIME ZONE`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()`) with index `(user_id, type)`. Both with `<rollback>` and `author: IA`.
- In `com.streamtube.backend.auth.domain`, implement `RefreshToken` and `EmailToken` aggregate roots (`@Table`, `@Id` UUID, `AggregateReference<User, UUID> userId`). `EmailToken` carries a `TokenType` enum (`CONFIRM_EMAIL`, `RESET_PASSWORD`) mapped to a `VARCHAR(20)` column. Provide static factory methods `RefreshToken.issue(userId, familyId, tokenHash, expiresAt)` and `EmailToken.issue(userId, type, tokenHash, expiresAt)`.
- In `com.streamtube.backend.auth.persistence`, implement `RefreshTokenRepository extends CrudRepository<RefreshToken, UUID>` with `Optional<RefreshToken> findByTokenHash(String)`, `List<RefreshToken> findByFamilyId(UUID)`, and a `@Modifying @Query` `int revokeFamily(UUID familyId, String reason, Instant when)`. Implement `EmailTokenRepository` with `Optional<EmailToken> findByTokenHash(String)` and a `@Modifying @Query` `int invalidatePreviousFor(UUID userId, TokenType type, Instant when)` (sets `used_at = when` on still-valid rows so a new request supersedes previous ones).
- In `com.streamtube.backend.auth.service`, implement `OpaqueTokenGenerator` exposing `Tokens generate()` (returns the record `Tokens(String rawHex, String sha256HexHash)` — 64 hex chars each, via `SecureRandom.getInstanceStrong()` for 32 bytes raw + `MessageDigest.getInstance("SHA-256")` for the hash) and `String hash(String rawHex)` for verification lookups.

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| OpaqueTokenGeneratorTest.java | Unit | `generate()` produces 64-hex-char raw + 64-hex-char hash; `hash(raw)` is deterministic and equals the generator's `sha256HexHash`; two generations produce different raw values. |
| RefreshTokenRepositoryIntegrationTest.java | Integration | `@DataJdbcTest`: save + `findByTokenHash`; `revokeFamily` sets `revoked_at` + `revoked_reason` on every row in the family; cascading delete from `users` removes refresh tokens. |
| EmailTokenRepositoryIntegrationTest.java | Integration | `@DataJdbcTest`: save + `findByTokenHash`; `invalidatePreviousFor` marks rows of the same `(user_id, type)` as used; the `CHECK` constraint rejects invalid token types. |

**Dependencies:** SI-02.1, SI-02.5

**Acceptance criteria:**

- `ApplicationModules.of(...).verify()` passes with the auth module declaring `allowedDependencies = {"users", "common"}`; an attempt to import a type from `videos` (not declared) fails the verification.
- Tables `refresh_tokens` and `email_tokens` exist after migration with the documented columns, FKs, and indexes; the `email_tokens.type` CHECK constraint rejects `INSERT ... type='OTHER'`.
- `OpaqueTokenGenerator.generate()` returns distinct raw values across two calls; `hash(raw)` recomputed matches the originally returned hash byte-for-byte.
- `refreshTokenRepository.revokeFamily(familyId, "TOKEN_REUSE", now)` updates every non-revoked row in that family and returns the row count.

---

### SI-02.7 — Registration endpoint: `POST /auth/register`

**Description:** Implement the full registration flow. Endpoint accepts email + password; the service hashes the password (Argon2), atomically creates the User and Channel rows (with handle generation + collision retry per TD-10), creates a CONFIRM_EMAIL token, and sends the confirmation email. Returns 201 with the created user representation (channel included).

**Technical actions:**

- In `com.streamtube.backend.auth.web`, create `RegisterRequest` (`@Email @NotBlank @Size(max=254) email`, `@NotBlank @Size(min=8, max=128) password`) and `RegisterResponse` records (`UUID id, String email, ChannelView channel { UUID id, String handle, String name }`).
- In `com.streamtube.backend.users.service`, implement `UserRegistrationService.register(String email, String passwordHash, String name): User` annotated `@Transactional`. Steps: `existsByEmail` short-circuit returns `Optional.empty()` or throws `EmailAlreadyExistsException`; persist `User`; loop up to 5 times: derive base handle via `HandleGenerator`, on attempts ≥ 2 append `_<3 random hex>` (via `SecureRandom`), try `channelRepository.save(...)`, catch `DbActionExecutionException`/`DataIntegrityViolationException` and retry; after 5 failures, raise `HandleGenerationFailedException` (500, `HANDLE_GENERATION_FAILED`). Returns the persisted `User`.
- In `com.streamtube.backend.auth.service`, implement `RegistrationService.register(RegisterRequest)` orchestrating: derive name from email prefix (same algorithm as handle, capitalized first letter); `passwordEncoder.encode(password)`; call `UserRegistrationService.register(...)`; generate confirm token via `OpaqueTokenGenerator` and persist an `EmailToken(type=CONFIRM_EMAIL, expires_at=now+24h)`; call `emailService.sendEmailConfirmation(user.email, user.name, publicUrl + "/auth/confirm-email?token=" + rawToken)`. Returns a `RegisterResponse`. Wraps in `@Transactional` so a failure during email-token persistence rolls back the user creation — email sending happens outside the transaction (after commit) via `TransactionTemplate.afterCommit` or a `@TransactionalEventListener`.
- Create `AuthController` in `com.streamtube.backend.auth.web` with `@PostMapping("/auth/register")` consuming `application/json`; return `ResponseEntity.created(URI.create("/users/" + response.id())).body(response)`. (The `Location` header points at a future `GET /users/{id}` endpoint; in Phase 02 that URI 404s, which is acceptable.)
- Register `/auth/register` in the `RateLimitedPaths` bean (SI-02.3) so the interceptor rate-limits this path. Add `EmailAlreadyExistsException` (409, `EMAIL_ALREADY_EXISTS`) and `HandleGenerationFailedException` (500, `HANDLE_GENERATION_FAILED`) `DomainException` subclasses in `com.streamtube.backend.auth.exception`.

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| UserRegistrationServiceTest.java | Unit | Happy path persists User + Channel atomically; `existsByEmail=true` raises `EmailAlreadyExistsException` before any write; simulated unique-violation on first save retries with `_<3hex>` suffix; five consecutive collisions raise `HandleGenerationFailedException`. |
| RegistrationServiceTest.java | Unit | Orchestration calls password encoder, user registration, email-token generation, and email service in order; if `EmailToken` persistence fails, the transaction rolls back and no email is sent. |
| RegistrationE2ETest.java | E2E | `POST /auth/register` happy path, duplicate email (409 `EMAIL_ALREADY_EXISTS`), invalid email format (400 `VALIDATION_ERROR`), password too short (400 `VALIDATION_ERROR`), 11th request from the same IP within a minute (429 `RATE_LIMIT_EXCEEDED`). |

**Dependencies:** SI-02.1, SI-02.2, SI-02.3, SI-02.4, SI-02.5, SI-02.6

**Acceptance criteria:**

- `POST /auth/register` with valid `{ email, password }` returns 201 with body `{ id, email, channel: { id, handle, name } }` and `Location: /users/{id}` header; `users` table has a new row with `email_confirmed_at=NULL`; `channels` table has a row linked to that user with a handle derived from the email prefix.
- `POST /auth/register` with an email that already exists returns 409 with `{ statusCode: 409, error: "EMAIL_ALREADY_EXISTS", message: ... }`; no new user or channel is persisted.
- Registering a new user causes a confirmation email to be delivered to the registered address (Mailpit inbox in dev; stubbed `JavaMailSender.send` call captured in tests) containing the user's name and a confirmation link with the raw token.
- Concurrent registrations whose email prefixes both sanitize to the same handle both succeed with distinct handles (the second receives a `_<3hex>` suffix). After 5 retry exhaustions the request returns 500 with `HANDLE_GENERATION_FAILED`.
- The 11th `POST /auth/register` from the same IP within 60 seconds returns 429 with `RATE_LIMIT_EXCEEDED` and does not invoke `RegistrationService`.

---

### SI-02.8 — Email confirmation endpoint: `POST /auth/confirm-email`

**Description:** Endpoint accepts the raw confirmation token (delivered by email in SI-02.7), validates it, and marks the user as confirmed. Returns 204 on success. Single-use: a confirmed token cannot be reused.

**Technical actions:**

- In `com.streamtube.backend.auth.web`, define `ConfirmEmailRequest` record (`@NotBlank String token`).
- In `com.streamtube.backend.auth.service`, implement `EmailConfirmationService.confirm(String rawToken)` annotated `@Transactional`. Steps: `hash = opaqueTokenGenerator.hash(rawToken)`; `emailTokenRepository.findByTokenHash(hash)` → if absent or `type != CONFIRM_EMAIL` → `InvalidConfirmationTokenException` (404, `INVALID_CONFIRMATION_TOKEN`); if `used_at != null` or `expires_at < now` → `InvalidConfirmationTokenException`; load user; if `user.emailConfirmedAt != null` → `EmailAlreadyConfirmedException` (409, `EMAIL_ALREADY_CONFIRMED`); set `user.emailConfirmedAt = now()`, set `emailToken.usedAt = now()`, save both.
- Add `AuthController.confirmEmail` endpoint `@PostMapping("/auth/confirm-email")` returning `ResponseEntity.noContent().build()` (204, no body — TD-07 REST conventions for action endpoints).
- Add `InvalidConfirmationTokenException` and `EmailAlreadyConfirmedException` `DomainException` subclasses to the Error Catalog.

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| EmailConfirmationServiceTest.java | Unit | Valid token confirms the user and marks the token used; unknown/used/expired tokens raise `InvalidConfirmationTokenException`; an already-confirmed user raises `EmailAlreadyConfirmedException`. |
| EmailConfirmationE2ETest.java | E2E | `POST /auth/confirm-email` happy path returns 204 with no body; same token replayed returns 404 `INVALID_CONFIRMATION_TOKEN`; expired token returns 404 `INVALID_CONFIRMATION_TOKEN`; user that is already confirmed returns 409 `EMAIL_ALREADY_CONFIRMED`. |

**Dependencies:** SI-02.6, SI-02.7

**Acceptance criteria:**

- `POST /auth/confirm-email` with a valid unused token returns 204 with no response body; `users.email_confirmed_at` is set to the current timestamp; `email_tokens.used_at` is set on the consumed token.
- `POST /auth/confirm-email` with an unknown token returns 404 `INVALID_CONFIRMATION_TOKEN` — same response as a token that exists but has been used or expired (so an attacker cannot distinguish).
- A valid token replayed after consumption returns 404 `INVALID_CONFIRMATION_TOKEN`.
- A token whose user is already confirmed (e.g., a second token issued before the first was used) returns 409 `EMAIL_ALREADY_CONFIRMED`.

---

### SI-02.9 — Login endpoint: `POST /auth/login`

**Description:** Authenticate a user with email + password and issue an access token (15-minute JWT) plus a refresh token (30-day opaque token, first member of a new family). Returns 200 with the token pair. Rejects unconfirmed users (TD-07's `EMAIL_NOT_CONFIRMED`) and exposes a uniform `INVALID_CREDENTIALS` response for unknown email and wrong password.

**Technical actions:**

- In `com.streamtube.backend.auth.web`, create `LoginRequest` (`@Email @NotBlank email`, `@NotBlank password`) and `TokenPairResponse` records (`String accessToken, String refreshToken, String tokenType, long expiresIn`). `tokenType` is the literal `"Bearer"`; `expiresIn` is the access token lifetime in seconds (900).
- In `com.streamtube.backend.auth.service`, implement `JwtAccessTokenService.issue(UUID userId): String` using the `JwtEncoder` bean from SI-02.2. Build claims with `JwtClaimsSet.builder().issuer("streamtube").subject(userId.toString()).issuedAt(now).expiresAt(now.plusSeconds(900)).id(UUID.randomUUID().toString()).build()` and `jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue()`.
- In `com.streamtube.backend.auth.service`, implement `RefreshTokenService.issue(UUID userId, UUID familyId): IssuedRefreshToken`. Generates a token via `OpaqueTokenGenerator`, persists a `RefreshToken` row with `expires_at = now + 30 days`, returns the `IssuedRefreshToken(rawHex, expiresAt, familyId)` record. `familyId == null` triggers a new `UUID.randomUUID()` family; used by login (new family) and refresh (preserve family).
- In `com.streamtube.backend.auth.service`, implement `LoginService.authenticate(LoginRequest): TokenPairResponse` annotated `@Transactional`. Steps: `userRepository.findByEmail(request.email())`; if absent → run `passwordEncoder.matches(request.password(), DUMMY_HASH)` to keep timing uniform, then throw `InvalidCredentialsException` (401, `INVALID_CREDENTIALS`); if present and `passwordEncoder.matches(...)` fails → throw `InvalidCredentialsException`; if `user.emailConfirmedAt == null` → throw `EmailNotConfirmedException` (403, `EMAIL_NOT_CONFIRMED`); else issue access token + refresh token (new family), assemble `TokenPairResponse`. The `DUMMY_HASH` is a precomputed Argon2id hash of a static placeholder, held in a private static field — used solely so the unknown-email branch performs roughly the same Argon2 work as the wrong-password branch.
- Add `@PostMapping("/auth/login")` in `AuthController` returning `ResponseEntity.ok(tokenPair)` (200 + body — login produces data, per REST conventions for `/auth/login`).
- Register `/auth/login` with the `RateLimitedPaths` bean (SI-02.3). Add `InvalidCredentialsException` (401) and `EmailNotConfirmedException` (403) as `DomainException` subclasses.

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| JwtAccessTokenServiceTest.java | Unit | `issue(userId)` produces a JWT whose `sub` claim equals `userId.toString()`, `iss` is `"streamtube"`, `exp - iat = 900`, and a fresh `jti`; tokens issued back-to-back have distinct `jti`. |
| LoginServiceTest.java | Unit | Happy path returns a token pair, persists exactly one `RefreshToken` row with a new `family_id`; unknown email raises `InvalidCredentialsException` (and `passwordEncoder.matches` is still invoked for timing parity); wrong password raises `InvalidCredentialsException`; unconfirmed user raises `EmailNotConfirmedException`. |
| LoginE2ETest.java | E2E | `POST /auth/login` happy path returns 200 with `{ accessToken, refreshToken, tokenType: "Bearer", expiresIn: 900 }`; wrong password and unknown email both return 401 `INVALID_CREDENTIALS`; unconfirmed user returns 403 `EMAIL_NOT_CONFIRMED`; 11th request from same IP within a minute returns 429 `RATE_LIMIT_EXCEEDED`; `accessToken` is verifiable against the configured `JwtDecoder`. |

**Dependencies:** SI-02.1, SI-02.2, SI-02.3, SI-02.5, SI-02.6

**Acceptance criteria:**

- `POST /auth/login` with valid email + password for a confirmed user returns 200 with `{ accessToken, refreshToken, tokenType: "Bearer", expiresIn: 900 }`; a new row is persisted in `refresh_tokens` with a fresh `family_id`, `rotated_at = NULL`, `revoked_at = NULL`, `expires_at ≈ now + 30 days`.
- `POST /auth/login` with a non-existent email returns 401 `INVALID_CREDENTIALS` — identical status, error code, and message to the wrong-password branch, so email existence is not revealed. Response time variance between the two branches stays within ~10 ms.
- `POST /auth/login` for a user whose `email_confirmed_at` is NULL returns 403 `EMAIL_NOT_CONFIRMED`; no refresh token is persisted.
- The returned `accessToken` decodes via `JwtDecoder` to a `Jwt` whose `sub` claim is the user's UUID and whose `exp` is ~15 minutes in the future.
- The 11th `POST /auth/login` from the same IP within 60 seconds returns 429 `RATE_LIMIT_EXCEEDED` and never reaches `LoginService` (no Argon2 work is performed).

---

### SI-02.10 — Refresh token rotation endpoint: `POST /auth/refresh`

**Description:** Rotate a refresh token: validate the presented token, issue a new access token + new refresh token (same family), and mark the previous token as rotated. Implements TD-03's A2 30-second grace period for concurrent refresh attempts and the theft-detection branch that revokes the entire family when an already-rotated token is presented after the grace window. Returns 200 with the new token pair.

**Technical actions:**

- In `com.streamtube.backend.auth.web`, create `RefreshRequest` record (`@NotBlank String refreshToken`). Response is the existing `TokenPairResponse` from SI-02.9.
- In `com.streamtube.backend.auth.service`, implement `RefreshService.refresh(String rawToken): TokenPairResponse` annotated `@Transactional`. Steps: `hash = opaqueTokenGenerator.hash(rawToken)`; `refreshTokenRepository.findByTokenHash(hash)` — if absent → `InvalidRefreshTokenException` (401, `INVALID_REFRESH_TOKEN`); if `revoked_at != null` → already revoked: revoke any still-active rows in the same family (defense in depth) and throw `TokenReuseDetectedException` (401, `TOKEN_REUSE_DETECTED`); if `expires_at < now` → `InvalidRefreshTokenException`; if `rotated_at != null` (token already rotated): if `now <= rotated_at + 30s` → resolve the head of the rotation chain via `rotated_to_id`, return the access+refresh pair that was previously issued in place of this one (grace replay — see below); else → theft detected: call `refreshTokenRepository.revokeFamily(familyId, "TOKEN_REUSE", now)` and throw `TokenReuseDetectedException`; happy path (token unused, not expired, not revoked): mark old row `rotated_at = now` and `rotated_to_id = newId`, issue new refresh token via `RefreshTokenService.issue(userId, familyId)` (preserves family), issue new access token, return pair.
- Grace replay detail: the row stores `rotated_to_id` pointing at the new token, but the raw new refresh-token value is not stored (only its hash). To support replay, cache the most recent issued pair per old-token-hash in an in-memory `Caffeine` cache (`com.github.ben-manes.caffeine:caffeine`, version managed by the Spring Boot parent BOM; eviction after 60 s) keyed by the old token's hash. Inside the grace window, look up the cache and return the same response; cache miss in-window → still treat as grace (cache evicted) but cannot return the same refresh token: in that case, return `InvalidRefreshTokenException` rather than triggering theft detection. Document that the 60 s cache is intentionally longer than the 30 s grace so the cache never expires *inside* the grace window under normal clock skew.
- Add `@PostMapping("/auth/refresh")` to `AuthController` returning `ResponseEntity.ok(tokenPair)`. `/auth/refresh` is NOT rate-limited (legitimate clients refresh on a fixed cadence — rate-limiting would harm normal use; theft detection is the protective layer).
- Add `InvalidRefreshTokenException` and `TokenReuseDetectedException` as `DomainException` subclasses (401 each).

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| RefreshServiceTest.java | Unit | Happy path issues a new pair, marks old row rotated, preserves `family_id`; expired/revoked/unknown token raises `InvalidRefreshTokenException`; rotated token presented within 30 s grace returns the cached pair; rotated token presented after 30 s revokes the family and raises `TokenReuseDetectedException`; revoked token presented at any time raises `TokenReuseDetectedException` and revokes still-active family members. |
| RefreshE2ETest.java | E2E | `POST /auth/refresh` happy path returns 200 with a new pair whose `accessToken` decodes successfully and whose `refreshToken` is distinct from the one sent; presenting the same refresh token twice rapidly returns the same response (grace replay); replaying an old refresh token after the grace window returns 401 `TOKEN_REUSE_DETECTED` and a subsequent valid refresh on a sibling family member also returns 401 `TOKEN_REUSE_DETECTED`. |

**Dependencies:** SI-02.2, SI-02.6, SI-02.9

**Acceptance criteria:**

- `POST /auth/refresh` with a valid unused refresh token returns 200 with a new `{ accessToken, refreshToken, tokenType, expiresIn }`; the new refresh token's `family_id` matches the old one's; the old row has `rotated_at = now` and `rotated_to_id` pointing at the new row.
- `POST /auth/refresh` with the same refresh token twice within 30 seconds returns the same response on both calls — no theft detection, no extra token rows created beyond the first rotation.
- `POST /auth/refresh` with an already-rotated refresh token after 30+ seconds returns 401 `TOKEN_REUSE_DETECTED`; every refresh-token row in that family has `revoked_at` set with `revoked_reason = "TOKEN_REUSE"`. A follow-up `/auth/refresh` using any other family member returns 401 `TOKEN_REUSE_DETECTED`.
- `POST /auth/refresh` with an unknown, expired, or already-revoked token returns 401 (`INVALID_REFRESH_TOKEN` for unknown/expired; `TOKEN_REUSE_DETECTED` for already-revoked) without leaking which class of failure occurred in the response body's `message`.

---

### SI-02.11 — Logout endpoint: `POST /auth/logout`

**Description:** End the current session by revoking the refresh-token family the client is using. The endpoint is authenticated (Bearer access token required), and the request body carries the refresh token to scope the revocation to that specific family — per the Phase 02 decision, logout revokes the current session only, not every device.

**Technical actions:**

- In `com.streamtube.backend.auth.web`, create `LogoutRequest` record (`@NotBlank String refreshToken`).
- In `com.streamtube.backend.auth.service`, implement `LogoutService.logout(UUID authenticatedUserId, String rawRefreshToken)` annotated `@Transactional`. Steps: `hash = opaqueTokenGenerator.hash(rawToken)`; `refreshTokenRepository.findByTokenHash(hash)` — if absent → throw `InvalidRefreshTokenException` (401); if the token's `userId` does not match `authenticatedUserId` → throw `InvalidRefreshTokenException` (same code — do not reveal ownership mismatch); else → `revokeFamily(familyId, "USER_LOGOUT", now)`. Idempotent: if `revoked_at` is already set and reason is `USER_LOGOUT`, no-op success; if revoked for any other reason (e.g., `TOKEN_REUSE`), still return 204 without re-revoking.
- Add `@PostMapping("/auth/logout")` to `AuthController` with security configured to require authentication. The `SecurityFilterChain` from SI-02.2 currently permits `/auth/**`; update its DSL to permit-all `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/confirm-email`, `/auth/forgot-password`, `/auth/reset-password` explicitly, and `anyRequest().authenticated()` covers `/auth/logout` (and any future authenticated `/auth/*` endpoint). Extract the authenticated user's `UUID` from `@AuthenticationPrincipal Jwt jwt` and call `UUID.fromString(jwt.getSubject())`. Return `ResponseEntity.noContent().build()` (204).
- Logout is NOT rate-limited.

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| LogoutServiceTest.java | Unit | Valid refresh token owned by the authenticated user revokes the family with reason `USER_LOGOUT`; an unknown token or a token whose `userId` differs from the authenticated user raises `InvalidRefreshTokenException`; calling logout twice with the same token is a successful no-op the second time. |
| LogoutE2ETest.java | E2E | `POST /auth/logout` without `Authorization` header returns 401; with a valid access token + matching refresh token returns 204 with no body and all rows of the family have `revoked_at` set; subsequent `POST /auth/refresh` with any token of that family returns 401 `TOKEN_REUSE_DETECTED`; logout with a refresh token belonging to a different user returns 401 `INVALID_REFRESH_TOKEN`. |

**Dependencies:** SI-02.2, SI-02.6, SI-02.9

**Acceptance criteria:**

- `POST /auth/logout` without an `Authorization: Bearer <token>` header returns 401 with the standard Spring Security challenge.
- `POST /auth/logout` with a valid access token and a matching refresh token returns 204 with no response body — every refresh-token row in that family has `revoked_at` set with `revoked_reason = "USER_LOGOUT"`.
- After successful logout, `POST /auth/refresh` with any refresh token from the same family returns 401 `TOKEN_REUSE_DETECTED` (because the row is now revoked).
- `POST /auth/logout` with a refresh token belonging to a different user (same family, different `userId`) returns 401 `INVALID_REFRESH_TOKEN` — same error code as an unknown token, so cross-user ownership is not revealed.
- Other refresh-token families belonging to the same authenticated user remain active — logout scopes revocation to one session only.

---

### SI-02.12 — Forgot-password request endpoint: `POST /auth/forgot-password`

**Description:** Accept an email address, and if it matches a confirmed user, issue a single-use opaque reset token (1-hour TTL) and send a password-reset email. Always returns 204 with no response body — the response is identical whether the email exists, is unconfirmed, or is unknown, so the endpoint never leaks account-existence information.

**Technical actions:**

- In `com.streamtube.backend.auth.web`, create `ForgotPasswordRequest` record (`@Email @NotBlank String email`).
- In `com.streamtube.backend.auth.service`, implement `PasswordResetRequestService.requestReset(String email)` annotated `@Transactional`. Steps: `userRepository.findByEmail(email)` — if absent OR `user.emailConfirmedAt == null` → return silently (no token, no email); else → `emailTokenRepository.invalidatePreviousFor(user.id, TokenType.RESET_PASSWORD, now)` to supersede prior outstanding reset tokens for the user; generate a fresh opaque token via `OpaqueTokenGenerator`; persist `EmailToken(type=RESET_PASSWORD, expires_at=now + 1h)`; schedule the email send via `TransactionTemplate.afterCommit` (same pattern as SI-02.7) → `emailService.sendPasswordReset(user.email, user.name, publicUrl + "/auth/reset-password?token=" + rawToken)`.
- Add `@PostMapping("/auth/forgot-password")` to `AuthController` returning `ResponseEntity.noContent().build()` (204 — security-neutral; the empty body is the natural consequence of returning the same response for every input).
- Register `/auth/forgot-password` with the `RateLimitedPaths` bean (SI-02.3).

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| PasswordResetRequestServiceTest.java | Unit | Confirmed user: previous unused reset tokens are marked used, a new `EmailToken(type=RESET_PASSWORD, expires_at=now+1h)` is persisted, and `emailService.sendPasswordReset` is invoked once with the correct recipient and the raw token in the link. Unknown email: no token persisted, no email sent. Unconfirmed user: no token persisted, no email sent. |
| ForgotPasswordE2ETest.java | E2E | `POST /auth/forgot-password` always returns 204 with no body for unknown, confirmed, and unconfirmed emails; only the confirmed-email scenario causes an email to land in the test mailbox; the 11th request from the same IP within a minute returns 429 `RATE_LIMIT_EXCEEDED`. |

**Dependencies:** SI-02.3, SI-02.4, SI-02.5, SI-02.6

**Acceptance criteria:**

- `POST /auth/forgot-password` with any email payload (known confirmed, known unconfirmed, or unknown) returns 204 with no response body — the response shape is identical across the three scenarios, so the endpoint does not reveal account-existence information.
- When the email belongs to a confirmed user, a `password-reset` email is delivered to that address (Mailpit inbox in dev; stubbed `JavaMailSender.send` capture in tests) containing the user's name and a reset link with the raw token; an `email_tokens` row exists with `type='RESET_PASSWORD'`, `expires_at ≈ now + 1h`, `used_at = NULL`.
- A second `POST /auth/forgot-password` for the same confirmed user marks the previous unused `RESET_PASSWORD` token row as `used_at = now` (superseded) and persists a fresh row — only the most recent token is valid.
- The 11th `POST /auth/forgot-password` from the same IP within 60 seconds returns 429 `RATE_LIMIT_EXCEEDED` and does not invoke `PasswordResetRequestService`.

---

### SI-02.13 — Reset-password endpoint: `POST /auth/reset-password`

**Description:** Consume a valid reset token, replace the user's password hash, mark the token used, and revoke every refresh-token family for the user (per the Phase 02 decision that password reset invalidates all sessions). Returns 204.

**Technical actions:**

- In `com.streamtube.backend.auth.web`, create `ResetPasswordRequest` record (`@NotBlank String token`, `@NotBlank @Size(min=8, max=128) String newPassword`).
- In `com.streamtube.backend.auth.service`, implement `PasswordResetService.reset(ResetPasswordRequest)` annotated `@Transactional`. Steps: `hash = opaqueTokenGenerator.hash(request.token())`; `emailTokenRepository.findByTokenHash(hash)` — if absent OR `type != RESET_PASSWORD` OR `used_at != null` OR `expires_at < now` → throw `InvalidResetTokenException` (404, `INVALID_RESET_TOKEN`); load user; `user.passwordHash = passwordEncoder.encode(request.newPassword())`; save user; mark `emailToken.usedAt = now`; revoke every active row in `refresh_tokens` for `user.id` with `revoked_reason = "PASSWORD_RESET"` via a `@Modifying @Query` `int revokeAllForUser(UUID userId, String reason, Instant when)` on `RefreshTokenRepository` (add this method to the repository in this SI).
- Add `@PostMapping("/auth/reset-password")` to `AuthController` returning `ResponseEntity.noContent().build()` (204).
- Add `InvalidResetTokenException` `DomainException` subclass (404).

**Tests:**

| File | Layer | Verifies |
|------|-------|----------|
| PasswordResetServiceTest.java | Unit | Valid token: user's password hash is replaced, the encoded value matches the new password and not the old; token is marked used; `revokeAllForUser` is invoked once with reason `PASSWORD_RESET`. Unknown/expired/used token raises `InvalidResetTokenException`. Token of type `CONFIRM_EMAIL` (wrong type) raises `InvalidResetTokenException`. |
| PasswordResetE2ETest.java | E2E | `POST /auth/reset-password` happy path returns 204 with no body, login with the new password succeeds, login with the old password returns 401 `INVALID_CREDENTIALS`; replaying the same token returns 404 `INVALID_RESET_TOKEN`; expired or wrong-type token returns 404 `INVALID_RESET_TOKEN`; after reset, any refresh token previously issued to that user returns 401 `TOKEN_REUSE_DETECTED` on `/auth/refresh`; password shorter than 8 chars returns 400 `VALIDATION_ERROR`. |

**Dependencies:** SI-02.2, SI-02.6, SI-02.12

**Acceptance criteria:**

- `POST /auth/reset-password` with a valid unused token and a compliant new password returns 204 with no response body; the user's `password_hash` is replaced (Argon2 verification against the new password succeeds, against the old fails); the `email_tokens` row is marked `used_at = now`.
- After a successful reset, every `refresh_tokens` row belonging to the user has `revoked_at` set with `revoked_reason = "PASSWORD_RESET"` — every prior session is invalidated, and `POST /auth/refresh` with any previously issued refresh token for that user returns 401 `TOKEN_REUSE_DETECTED`.
- `POST /auth/reset-password` with an unknown, expired, used, or wrong-type token returns 404 `INVALID_RESET_TOKEN` — uniform response so attackers cannot distinguish failure modes.
- `POST /auth/reset-password` with a `newPassword` shorter than 8 or longer than 128 characters returns 400 `VALIDATION_ERROR` and does not touch the user row or the token row.

---

## Technical Specifications

### Data Model

Four new tables are introduced. All UUIDs default to `gen_random_uuid()` (pgcrypto, installed in Phase 01 SI-01.7). Email columns use the `CITEXT` type (citext, installed in Phase 01 SI-01.7).

#### users (SI-02.5, owned by the `users` module)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | uuid | PK, default `gen_random_uuid()` | |
| email | citext | NOT NULL, UNIQUE | Case-insensitive uniqueness via `CITEXT`. |
| password_hash | varchar(255) | NOT NULL | Argon2id-encoded; `$argon2id$...` format. |
| name | varchar(100) | | Display name; derived from email prefix on registration. |
| email_confirmed_at | timestamptz | | NULL until `POST /auth/confirm-email` succeeds. |
| created_at | timestamptz | NOT NULL, default `now()` | |
| updated_at | timestamptz | NOT NULL, default `now()` | Updated by application code on password reset. |

**Relations:** `channels.user_id` → `users.id` (one-to-one, FK with `ON DELETE CASCADE`); `refresh_tokens.user_id` and `email_tokens.user_id` → `users.id` (many-to-one, `ON DELETE CASCADE`).

#### channels (SI-02.5, owned by the `users` module)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | uuid | PK, default `gen_random_uuid()` | |
| user_id | uuid | NOT NULL, UNIQUE, FK → `users(id)` ON DELETE CASCADE | One channel per user. |
| handle | varchar(50) | NOT NULL, UNIQUE | URL-safe `[a-z0-9_]` per TD-10; max 46 base chars + optional `_<3hex>` suffix on collision retry. |
| name | varchar(100) | NOT NULL | Display name; defaults to the user's `name` at registration. |
| description | text | | Optional. Empty until edited in a later phase. |
| created_at | timestamptz | NOT NULL, default `now()` | |
| updated_at | timestamptz | NOT NULL, default `now()` | |

**Indexes:** UNIQUE on `(handle)`, UNIQUE on `(user_id)` (both backed by the unique constraints declared on the columns).

#### refresh_tokens (SI-02.6, owned by the `auth` module)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | uuid | PK, default `gen_random_uuid()` | |
| user_id | uuid | NOT NULL, FK → `users(id)` ON DELETE CASCADE | |
| family_id | uuid | NOT NULL | Constant across one rotation chain — all tokens in a rotation share the same `family_id`. |
| token_hash | char(64) | NOT NULL, UNIQUE | SHA-256 hex of the opaque raw token (TD-04). |
| expires_at | timestamptz | NOT NULL | `now() + 30 days` at issuance. |
| rotated_at | timestamptz | | NULL until the token is exchanged for a new pair via `/auth/refresh`. |
| rotated_to_id | uuid | FK → `refresh_tokens(id)` ON DELETE SET NULL | Points at the row that superseded this one. NULL while unrotated. |
| revoked_at | timestamptz | | Set on logout, theft detection, or password reset. |
| revoked_reason | varchar(40) | | One of `USER_LOGOUT`, `TOKEN_REUSE`, `PASSWORD_RESET` (extensible). |
| created_at | timestamptz | NOT NULL, default `now()` | |

**Indexes:** UNIQUE on `(token_hash)`; non-unique on `(user_id)` (logout-all and reset-all lookups); non-unique on `(family_id)` (family revocation).

#### email_tokens (SI-02.6, owned by the `auth` module)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | uuid | PK, default `gen_random_uuid()` | |
| user_id | uuid | NOT NULL, FK → `users(id)` ON DELETE CASCADE | |
| type | varchar(20) | NOT NULL, CHECK `type IN ('CONFIRM_EMAIL','RESET_PASSWORD')` | |
| token_hash | char(64) | NOT NULL, UNIQUE | SHA-256 hex of the raw token (TD-04). |
| expires_at | timestamptz | NOT NULL | `now() + 24h` for confirmation; `now() + 1h` for reset. |
| used_at | timestamptz | | Set when the token is consumed or superseded by a newer request of the same type. |
| created_at | timestamptz | NOT NULL, default `now()` | |

**Indexes:** UNIQUE on `(token_hash)`; non-unique on `(user_id, type)` for the "invalidate previous" sweep in SI-02.12.

---

### API Contracts

All requests use `Content-Type: application/json`. All responses follow the canonical error shape `{ statusCode, error, message }` defined in TD-07 / SI-02.1.

#### POST /auth/register (SI-02.7)

**Request body:**
- email: string, required — RFC 5322 email format, max 254 chars.
- password: string, required — 8 to 128 chars.

**Response 201:**
- id: uuid — the new user's id.
- email: string — echo of the registered email (canonicalized to the stored form).
- channel: object — `{ id: uuid, handle: string, name: string }`.

**Response headers:**
- Location: `/users/{id}` — points at the future user-resource GET (404 in Phase 02).

**Error responses:**
- 400 VALIDATION_ERROR: malformed email, missing/short/long password.
- 409 EMAIL_ALREADY_EXISTS: email already registered.
- 429 RATE_LIMIT_EXCEEDED: more than 10 requests in 60 s from the same client IP.
- 500 HANDLE_GENERATION_FAILED: five consecutive `channels.handle` unique-constraint collisions during retry (effectively unreachable).

#### POST /auth/confirm-email (SI-02.8)

**Request body:**
- token: string, required — raw confirmation token from the email link.

**Response 204:** no body.

**Error responses:**
- 400 VALIDATION_ERROR: missing token.
- 404 INVALID_CONFIRMATION_TOKEN: token unknown, expired, used, or of wrong type.
- 409 EMAIL_ALREADY_CONFIRMED: user already confirmed.

#### POST /auth/login (SI-02.9)

**Request body:**
- email: string, required — email format, non-blank.
- password: string, required — non-blank.

**Response 200:**
- accessToken: string — RS256-signed JWT, 15-minute lifetime; claims: `iss="streamtube"`, `sub=<userId>`, `iat`, `exp`, `jti`.
- refreshToken: string — opaque hex token (64 chars).
- tokenType: string — literal `"Bearer"`.
- expiresIn: integer — access token lifetime in seconds (`900`).

**Error responses:**
- 400 VALIDATION_ERROR: malformed email or missing fields.
- 401 INVALID_CREDENTIALS: unknown email or wrong password (uniform response — no distinction).
- 403 EMAIL_NOT_CONFIRMED: user exists, password correct, but `email_confirmed_at` is NULL.
- 429 RATE_LIMIT_EXCEEDED: more than 10 requests in 60 s from the same client IP.

#### POST /auth/refresh (SI-02.10)

**Request body:**
- refreshToken: string, required — raw refresh token previously issued.

**Response 200:** same shape as `POST /auth/login` (new access token + new refresh token; same `family_id` continues in the new refresh-token row).

**Error responses:**
- 400 VALIDATION_ERROR: missing token.
- 401 INVALID_REFRESH_TOKEN: token unknown, expired, or grace-replay cache miss inside the grace window.
- 401 TOKEN_REUSE_DETECTED: token already rotated past the 30 s grace window OR token already revoked — the entire family is revoked as a side effect.

#### POST /auth/logout (SI-02.11)

**Request headers:**
- Authorization: Bearer `<accessToken>` — required.

**Request body:**
- refreshToken: string, required — refresh token of the session to end.

**Response 204:** no body — the current session's family is revoked with `revoked_reason = "USER_LOGOUT"`.

**Error responses:**
- 400 VALIDATION_ERROR: missing refresh token.
- 401 (Spring Security challenge): missing or invalid `Authorization` header.
- 401 INVALID_REFRESH_TOKEN: refresh token unknown OR belongs to a different user.

#### POST /auth/forgot-password (SI-02.12)

**Request body:**
- email: string, required — email format.

**Response 204:** no body — security-neutral; identical response for known-confirmed, known-unconfirmed, and unknown emails.

**Error responses:**
- 400 VALIDATION_ERROR: malformed email.
- 429 RATE_LIMIT_EXCEEDED: more than 10 requests in 60 s from the same client IP.

#### POST /auth/reset-password (SI-02.13)

**Request body:**
- token: string, required — raw reset token from the email link.
- newPassword: string, required — 8 to 128 chars.

**Response 204:** no body — password replaced, token consumed, all sessions revoked.

**Error responses:**
- 400 VALIDATION_ERROR: missing token, missing or too-short/long password.
- 404 INVALID_RESET_TOKEN: token unknown, expired, used, or of wrong type.

---

### Authorization Matrix

| Endpoint | Public | Authenticated | Role |
|----------|--------|---------------|------|
| POST /auth/register | ✓ | | |
| POST /auth/confirm-email | ✓ | | |
| POST /auth/login | ✓ | | |
| POST /auth/refresh | ✓ | | |
| POST /auth/logout | | ✓ | |
| POST /auth/forgot-password | ✓ | | |
| POST /auth/reset-password | ✓ | | |
| GET /actuator/health (inherited from Phase 01) | ✓ | | |
| GET /actuator/info (inherited from Phase 01) | ✓ | | |

Any route not explicitly listed is denied to anonymous callers by `anyRequest().authenticated()` in the `SecurityFilterChain` from SI-02.2. Role-based authorization is not introduced in this phase.

---

### Error Catalog

**Error response format** (defined in TD-07 / SI-02.1, inherited by every later phase in `stream-tube-backend`):

```json
{ "statusCode": 409, "error": "EMAIL_ALREADY_EXISTS", "message": "Email is already registered" }
```

- `statusCode` (int) — matches the HTTP status.
- `error` (string) — domain code from the catalog below; `VALIDATION_ERROR` for framework-level body validation; `INTERNAL_ERROR` for 500 fallbacks.
- `message` (string) — human-readable, English.

| Code | HTTP | Message | Trigger |
|------|------|---------|---------|
| VALIDATION_ERROR | 400 | (first violation message) | Any endpoint receives a body that fails Jakarta Bean Validation (`MethodArgumentNotValidException` / `HandlerMethodValidationException`). |
| INTERNAL_ERROR | 500 | Unexpected error | Any uncaught exception not derived from `DomainException`. |
| RATE_LIMIT_EXCEEDED | 429 | Too many requests | More than 10 requests in 60 s from the same client IP against a rate-limited path (`/auth/register`, `/auth/login`, `/auth/forgot-password`). |
| EMAIL_ALREADY_EXISTS | 409 | Email is already registered | `POST /auth/register` with an email that already exists in `users`. |
| HANDLE_GENERATION_FAILED | 500 | Could not assign a unique channel handle | `POST /auth/register` exhausts 5 collision-retry attempts on the `channels.handle` unique constraint. |
| INVALID_CONFIRMATION_TOKEN | 404 | Invalid or expired confirmation token | `POST /auth/confirm-email` with a token that is unknown, expired, used, or whose type is not `CONFIRM_EMAIL`. |
| EMAIL_ALREADY_CONFIRMED | 409 | Email already confirmed | `POST /auth/confirm-email` with a valid token whose user already has `email_confirmed_at` set. |
| INVALID_CREDENTIALS | 401 | Invalid email or password | `POST /auth/login` with unknown email OR wrong password (same code for both — does not reveal email existence). |
| EMAIL_NOT_CONFIRMED | 403 | Email not confirmed | `POST /auth/login` with valid credentials for a user whose `email_confirmed_at` is NULL. |
| INVALID_REFRESH_TOKEN | 401 | Invalid refresh token | `POST /auth/refresh` with unknown or expired token; `POST /auth/logout` with unknown token or token belonging to a different user; grace-replay cache miss inside the 30 s grace window. |
| TOKEN_REUSE_DETECTED | 401 | Refresh token reuse detected | `POST /auth/refresh` with a token whose `rotated_at` is older than 30 s (theft), OR with a token whose `revoked_at` is already set — triggers full-family revocation as a side effect. |
| INVALID_RESET_TOKEN | 404 | Invalid or expired reset token | `POST /auth/reset-password` with a token that is unknown, expired, used, or whose type is not `RESET_PASSWORD`. |

---

## Dependency Map

```
SI-02.1 (common error handling)               [no deps]
├── SI-02.3 (rate limit interceptor)
│   ├── SI-02.7 (register)
│   ├── SI-02.9 (login)
│   └── SI-02.12 (forgot-password)
└── SI-02.5 (users module: User + Channel)
    └── SI-02.6 (auth module: RefreshToken + EmailToken)
        ├── SI-02.7 (register)
        │   └── SI-02.8 (confirm-email)
        ├── SI-02.9 (login)
        │   ├── SI-02.10 (refresh)
        │   └── SI-02.11 (logout)
        └── SI-02.12 (forgot-password)
            └── SI-02.13 (reset-password)

SI-02.2 (security infra: Argon2 + JWT + filter chain)      [no deps]
└── SI-02.7, SI-02.9, SI-02.10, SI-02.11, SI-02.13

SI-02.4 (email infra: Mailpit + EmailService)              [no deps]
└── SI-02.7, SI-02.12
```

Linear summary of an implementation-friendly order:

1. **SI-02.1** (common error handling), **SI-02.2** (security infra), **SI-02.4** (email infra) — independent foundational SIs, can run in parallel.
2. **SI-02.3** (rate limit interceptor) — after SI-02.1.
3. **SI-02.5** (`users` module: User + Channel + HandleGenerator) — after SI-02.1.
4. **SI-02.6** (`auth` module: RefreshToken + EmailToken + OpaqueTokenGenerator) — after SI-02.5.
5. **SI-02.7** (register), **SI-02.9** (login), **SI-02.12** (forgot-password) — independent of each other, can run in parallel once SI-02.2, SI-02.3, SI-02.4, SI-02.6 are in place.
6. **SI-02.8** (confirm-email) — after SI-02.7.
7. **SI-02.10** (refresh) and **SI-02.11** (logout) — after SI-02.9; independent of each other.
8. **SI-02.13** (reset-password) — after SI-02.12.

---

## Deliverables

- [ ] Global error handling delivers the canonical `{ statusCode, error, message }` shape for every `DomainException`, every Bean Validation failure, and every uncaught exception — established once in `com.streamtube.backend.common.web` and inherited by all later phases.
- [ ] Spring Security is configured with `spring-boot-starter-oauth2-resource-server`: an Argon2id `PasswordEncoder`, an RSA-keyed `JwtEncoder` + `JwtDecoder` pair, and a stateless `SecurityFilterChain` that permits `/actuator/health`, `/actuator/info`, and the public `/auth/*` endpoints while requiring authentication for `/auth/logout` and any future route.
- [ ] A per-IP Resilience4j rate limiter (10 req/min, no waiting) applied via a `HandlerInterceptor` enforces `/auth/register`, `/auth/login`, and `/auth/forgot-password`; excess returns 429 `RATE_LIMIT_EXCEEDED`.
- [ ] Transactional email is wired through `spring-boot-starter-mail` + `spring-boot-starter-thymeleaf`; the `mailpit` service is part of `compose.yaml` and reachable at `http://localhost:8025`; `EmailService` exposes `sendEmailConfirmation` and `sendPasswordReset`.
- [ ] The `users` Modulith module owns `User` and `Channel` aggregates with `allowedDependencies = {"common"}`; Liquibase changeset `002-users-and-channels.yaml` is applied; `HandleGenerator` produces `[a-z0-9_]` handles with `user_<8-hex>` fallback and 46-char truncation (TD-10).
- [ ] The `auth` Modulith module owns `RefreshToken` and `EmailToken` aggregates with `allowedDependencies = {"users", "common"}`; Liquibase changeset `003-auth-tokens.yaml` is applied; `OpaqueTokenGenerator` produces 64-hex raw + SHA-256 hash pairs (TD-04).
- [ ] `POST /auth/register` creates a User + Channel atomically with handle collision retry (5 attempts, `_<3hex>` suffix); 201 with `{ id, email, channel }` and `Location: /users/{id}`; a confirmation email is delivered (Mailpit in dev, stub in tests); 409 on duplicate email.
- [ ] `POST /auth/confirm-email` consumes the token, sets `users.email_confirmed_at`, marks the token used; 204 on success; uniform 404 `INVALID_CONFIRMATION_TOKEN` for unknown/used/expired tokens; 409 `EMAIL_ALREADY_CONFIRMED` for an already-confirmed user.
- [ ] `POST /auth/login` returns 200 with `{ accessToken (15 min JWT, RS256), refreshToken (30 day opaque), tokenType: "Bearer", expiresIn: 900 }`; uniform 401 `INVALID_CREDENTIALS` for unknown email OR wrong password; 403 `EMAIL_NOT_CONFIRMED` for unconfirmed users.
- [ ] `POST /auth/refresh` rotates the refresh token (new pair, same `family_id`, old row's `rotated_at`/`rotated_to_id` set); 30 s in-memory grace cache returns the same pair on duplicate refresh; reuse after grace → 401 `TOKEN_REUSE_DETECTED` and full-family revocation.
- [ ] `POST /auth/logout` (authenticated) revokes the current session's family with `revoked_reason = "USER_LOGOUT"`; 204; other families of the same user remain valid.
- [ ] `POST /auth/forgot-password` always returns 204 (security-neutral); confirmed users receive a 1-hour reset-token email; previous unused reset tokens for the same user are superseded.
- [ ] `POST /auth/reset-password` replaces the user's password hash with Argon2id, marks the reset token used, and revokes every refresh-token row for the user with `revoked_reason = "PASSWORD_RESET"`; 204; uniform 404 `INVALID_RESET_TOKEN` for unknown/used/expired/wrong-type tokens.
- [ ] `ApplicationModules.of(StreamTubeBackendApplication.class).verify()` still passes after both new modules declare their `allowedDependencies`.
- [ ] All SI tests pass in `stream-tube-backend` (`./mvnw test`).
- [ ] Project compiles successfully in `stream-tube-backend` (`./mvnw clean compile`).
- [ ] Project builds successfully in `stream-tube-backend` (`./mvnw clean package`).
- [ ] Code formatting check passes in `stream-tube-backend` (`./mvnw spotless:check`).
