# Phase 02 — Cadastro, Login e Gerenciamento de Conta — Progress

**Status:** completed
**SIs:** 13/13 completed

### SI-02.1 — Common error handling: DomainException + @RestControllerAdvice + Bean Validation
- **Status:** completed
- **Tests:** 6/6 passed (GlobalExceptionHandlerTest: 3, GlobalExceptionHandlerIntegrationTest: 3)
- **Observations:** Spring Boot 4.x moved @WebMvcTest to org.springframework.boot.webmvc.test.autoconfigure (not the old .web.servlet package).

### SI-02.2 — Common security infrastructure: Argon2 + JWT (Resource Server) + SecurityFilterChain
- **Status:** completed
- **Tests:** 17/17 passed (Argon2PasswordEncoderTest: 4, SecurityFilterChainIntegrationTest: 4, JwtEncoderDecoderIntegrationTest: 3, GlobalExceptionHandlerTest: 3, GlobalExceptionHandlerIntegrationTest: 3)
- **Observations:** TestcontainersConfiguration was made public to allow @Import from subpackages. JwtClaimsSet requires expiresAt > issuedAt — expired-token test uses issuedAt 2h in the past. BouncyCastle 1.80 needed explicitly (not in Spring Boot 4.x BOM).

### SI-02.3 — Common rate-limiting infrastructure: Resilience4j + per-IP HandlerInterceptor
- **Status:** completed
- **Tests:** 4/4 passed (RateLimitInterceptorIntegrationTest: 4)
- **Observations:** resilience4j-spring-boot3:2.4.0 is incompatible with Spring Boot 4.x (has an explicit Spring Boot 3.x version check). Used resilience4j-ratelimiter:2.4.0 directly instead; configured RateLimiterRegistry manually via @Bean in RateLimitConfiguration with @ConfigurationProperties bound to streamtube.ratelimit.auth.* in application.yml.

### SI-02.4 — Common email infrastructure: Mailpit + spring-boot-starter-mail + Thymeleaf + EmailService
- **Status:** completed
- **Tests:** 4/4 passed (ThymeleafEmailServiceTest: 2, EmailTemplateRenderTest: 2)
- **Observations:** SpringTemplateEngine.afterPropertiesSet() is not public in Thymeleaf 3.1.3 — engine initializes lazily on first use, so the call is unnecessary. @Primary stub JavaMailSender added to TestcontainersConfiguration to prevent SMTP connection attempts in all @SpringBootTest tests.

### SI-02.5 — users module: User + Channel aggregates, repositories, HandleGenerator, schema
- **Status:** completed
- **Tests:** 16/16 passed (HandleGeneratorTest: 7, UserRepositoryIntegrationTest: 4, ChannelRepositoryIntegrationTest: 5)
- **Observations:** PostgreSQL CITEXT column returned as PGobject — added DataJdbcConfig with @ReadingConverter PGobjectToStringConverter to common.persistence. CITEXT case-insensitive equality comparison via JDBC params is unreliable (text vs citext type coercion) — used explicit LOWER() in @Query annotations for findByEmail and existsByEmail. @DataJdbcTest is at org.springframework.boot.data.jdbc.test.autoconfigure; @AutoConfigureTestDatabase at org.springframework.boot.jdbc.test.autoconfigure. "jöão@example.com" strips to "jo" (ASCII letters j,o survive), not a fallback — test corrected to use purely non-ASCII prefixes.

### SI-02.6 — auth module: RefreshToken + EmailToken aggregates, repositories, OpaqueTokenGenerator, schema
- **Status:** completed
- **Tests:** 11/11 passed (OpaqueTokenGeneratorTest: 4, RefreshTokenRepositoryIntegrationTest: 4, EmailTokenRepositoryIntegrationTest: 3)
- **Observations:** addCheckConstraint is a Liquibase Pro-only feature — used raw sql change instead. TokenType enum mapped directly to VARCHAR(20) in Spring Data JDBC. rotated_to_id self-referencing FK added via addForeignKeyConstraint with onDelete: SET NULL after table creation.

### SI-02.7 — Registration endpoint: POST /auth/register
- **Status:** completed
- **Tests:** 11/11 passed (UserRegistrationServiceTest: 4, RegistrationServiceTest: 2, RegistrationE2ETest: 5)
- **Observations:** TestRestTemplate removed from Spring Boot 4.x — E2E test uses MockMvc + @AutoConfigureMockMvc. EmailAlreadyExistsException/HandleGenerationFailedException placed in users.exception (not auth.exception) to avoid circular module dependency. ChannelHandleSaver @Component with @Transactional(NESTED) used to enable savepoint-based retry for channel handle collisions in PostgreSQL. argThat lambdas must use null-safe comparison ("expected".equals(h)) not h.equals("expected").

### SI-02.8 — Email confirmation endpoint: POST /auth/confirm-email
- **Status:** completed
- **Tests:** 10/10 passed (EmailConfirmationServiceTest: 6, EmailConfirmationE2ETest: 4)
- **Observations:** auth.exception package created for auth-specific DomainException subclasses (InvalidConfirmationTokenException, EmailAlreadyConfirmedException). UserRepository accessed via users::persistence named interface (already in auth module's allowedDependencies). E2E test seeds data directly via repositories + OpaqueTokenGenerator to get a known raw token.

### SI-02.9 — Login endpoint: POST /auth/login
- **Status:** completed
- **Tests:** 11/11 passed (JwtAccessTokenServiceTest: 2, LoginServiceTest: 4, LoginE2ETest: 5)
- **Observations:** StreamTubeBackendApplicationTests pre-existing failure (missing @ActiveProfiles("test")) fixed — JwtKeyProperties.publicKey() returned null without the test profile. dummyHash initialized in LoginService constructor via PasswordEncoder.encode() call at bean creation time. JwtClaimsSet.getIssuer() avoided in unit test (returns URL); used getClaims().get(JwtClaimNames.ISS) for raw String assertion.

### SI-02.10 — Refresh token rotation endpoint: POST /auth/refresh
- **Status:** completed
- **Tests:** 11/11 passed (RefreshServiceTest: 7, RefreshE2ETest: 4)
- **Observations:** @Transactional(noRollbackFor = TokenReuseDetectedException.class) required — revokeFamily UPDATE must commit even when the exception propagates (default Spring behavior rolls back for unchecked exceptions). Caffeine cache field is package-private to allow direct seeding in unit tests. graceCache.put() happens synchronously within the transaction; cache miss within grace window returns INVALID_REFRESH_TOKEN (not theft detection) per spec.

### SI-02.11 — Logout endpoint: POST /auth/logout
- **Status:** completed
- **Tests:** 8/8 passed (LogoutServiceTest: 5, LogoutE2ETest: 3)
- **Observations:** SecurityFilterChain updated from permitAll /auth/** wildcard to explicit permit for each public path; /auth/logout now falls under anyRequest().authenticated(). LogoutService is idempotent — returns normally for already-revoked tokens regardless of revocation reason.

### SI-02.12 — Forgot-password request endpoint: POST /auth/forgot-password
- **Status:** completed
- **Tests:** 8/8 passed (PasswordResetRequestServiceTest: 3, ForgotPasswordE2ETest: 5)
- **Observations:** NamedParameterJdbcTemplate injected in ForgotPasswordE2ETest to verify database state (RESET_PASSWORD token created for confirmed user, superseded for duplicate requests) since the test JavaMailSender stub is a no-op and cannot capture email calls.

### SI-02.13 — Reset-password endpoint: POST /auth/reset-password
- **Status:** completed
- **Tests:** 11/11 passed (PasswordResetServiceTest: 5, PasswordResetE2ETest: 6)
- **Observations:** All artifacts (PasswordResetService, ResetPasswordRequest, AuthController endpoint, InvalidResetTokenException, revokeAllForUser on RefreshTokenRepository) were already implemented as part of prior SI work. Only needed to verify and run tests.
