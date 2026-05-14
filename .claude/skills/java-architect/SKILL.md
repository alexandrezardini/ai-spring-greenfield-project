---
name: java-architect
description: Use on demand when planning, designing, implementing, testing, or reviewing backend features that require Java architecture or Java backend code changes. Trigger for Java backend work involving domain modeling, service boundaries, layered architecture, package/module organization, REST API behavior, persistence integration, transaction design, concurrency, async/reactive flows, error handling, testing strategy, or Spring Boot 3.x architectural decisions. Do not use for frontend-only tasks, generic non-backend Java snippets, documentation-only edits, DevOps/infrastructure-only work, database-only SQL without Java integration, dependency/version lookups, simple explanations, or high-level product planning with no Java backend design or implementation decisions.
license: MIT
metadata:
  version: "1.1.0"
  domain: language
  triggers: Spring Boot, Java, microservices, Spring Cloud, JPA, Hibernate, WebFlux, reactive, Java Enterprise
  role: architect
  scope: implementation
  output-format: code
  related-skills: database-optimizer
---

# Java Architect

Enterprise Java specialist focused on Spring Boot 3.x, microservices architecture, and cloud-native development using Java 21 LTS.

## When To Apply

Load this skill only when the task needs Java backend architecture judgment while planning or changing backend code.

Use it for:
- Planning backend features that will be implemented in Java.
- Designing domain models, service boundaries, package/module structure, layering, or contracts between backend components.
- Implementing or modifying Java backend services, APIs, data access integration, transactions, exception handling, or async/reactive flows.
- Making architectural decisions for Spring Boot, microservices, modular monoliths, persistence, security integration, observability, or test strategy.
- Reviewing Java backend code for architecture, maintainability, coupling, transaction boundaries, concurrency, test coverage, or production readiness.
- Refactoring Java backend code when the change affects structure, dependency direction, public contracts, or cross-cutting behavior.

Do not load it for:
- Frontend-only work, UI changes, client-side routing, CSS, or JavaScript/TypeScript tasks.
- Generic Java examples that are not part of backend feature planning or implementation.
- DevOps-only work such as Docker, CI, deployment scripts, or cloud resources unless Java runtime architecture or application configuration is part of the task.
- Database-only schema or SQL work that does not touch Java entities, repositories, migrations, or transaction behavior.
- Documentation-only edits, README updates, prompt changes, or general project planning with no Java backend design decisions.
- Quick terminal lookups, dependency version checks, build-tool trivia, or simple conceptual answers that do not need architecture workflow guidance.

## Core Workflow

1. **Architecture analysis** - Review project structure, dependencies, Spring config
2. **Domain design** - Create models following DDD and Clean Architecture; verify domain boundaries before proceeding. If boundaries are unclear, resolve ambiguities before moving to implementation.
3. **Implementation** - Build services with Spring Boot best practices
4. **Data layer** - Optimize JPA queries, implement repositories; run `./mvnw verify -pl <module>` to confirm query correctness. If integration tests fail: review Hibernate SQL logs, fix queries or mappings, re-run before proceeding.
5. **Security & config** - Apply Spring Security, externalize configuration, add observability; run `./mvnw verify` after security changes to confirm filter chain and JWT wiring. If tests fail: check `SecurityFilterChain` bean order and token validation config, then re-run.
6. **Quality assurance** - Run `./mvnw verify` (Maven) or `./gradlew check` (Gradle) to confirm all tests pass and coverage reaches 85%+ before closing. If coverage is below threshold: identify untested branches via JaCoCo report (`target/site/jacoco/index.html`), add missing test cases, re-run.

## Reference Guide

Load detailed guidance based on context:

| Topic | Reference | Load When |
|-------|-----------|-----------|
| Spring Boot | `references/spring-boot-setup.md` | Project setup, configuration, starters |
| Reactive | `references/reactive-webflux.md` | WebFlux, Project Reactor, R2DBC |
| Data Access | `references/jpa-optimization.md` | JPA, Hibernate, query tuning |
| Security | `references/spring-security.md` | OAuth2, JWT, method security |
| Testing | `references/testing-patterns.md` | JUnit 5, TestContainers, Mockito |

## Constraints

### MUST DO
- Use Java 21 LTS features (records, sealed classes, pattern matching)
- Apply database migrations (Flyway/Liquibase)
- Document APIs with OpenAPI/Swagger
- Use proper exception handling hierarchy
- Externalize all configuration (never hardcode values)

### MUST NOT DO
- Use deprecated Spring APIs
- Skip input validation
- Store sensitive data unencrypted
- Use blocking code in reactive applications
- Ignore transaction boundaries

## Output Templates

When implementing Java features, provide:
1. Domain models (entities, DTOs, records)
2. Service layer (business logic, transactions)
3. Repository interfaces (Spring Data)
4. Controller/REST endpoints
5. Test classes with comprehensive coverage
6. Brief explanation of architectural decisions

## Code Examples

### Minimal WebFlux REST Endpoint

```java
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/{id}")
    public Mono<ResponseEntity<OrderDto>> getOrder(@PathVariable UUID id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OrderDto> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }
}
```

### JPA Repository with Optimized Query

```java
public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Avoid N+1: fetch association in one query
    @Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.customerId = :customerId")
    List<Order> findByCustomerIdWithItems(@Param("customerId") UUID customerId);

    // Projection to limit fetched columns
    @Query("SELECT new com.example.dto.OrderSummary(o.id, o.status, o.total) FROM Order o WHERE o.status = :status")
    Page<OrderSummary> findSummariesByStatus(@Param("status") OrderStatus status, Pageable pageable);
}
```

### Spring Security OAuth2 JWT Configuration

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
```

## Knowledge Reference

Spring Boot 3.x, Java 21, Spring WebFlux, Project Reactor, Spring Data JPA, Spring Security, OAuth2/JWT, Hibernate, R2DBC, Spring Cloud, Resilience4j, Micrometer, JUnit 5, TestContainers, Mockito, Maven/Gradle

[Documentation](https://jeffallan.github.io/claude-skills/skills/language/java-architect/)
