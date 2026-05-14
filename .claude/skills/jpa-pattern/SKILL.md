---
name: jpa-pattern
description: 'JPA, Hibernate, and Spring Data persistence guidance for planning or implementing database-backed backend features and queries in Java/Spring Boot codebases. Use when the task involves designing or changing entities, table mappings, repositories, JPQL/native queries, Criteria/Specifications, relationships, transactions, migrations, pagination, sorting, fetch strategies, projections, auditing, soft deletes, indexes, database performance, or connection/caching behavior. Do not use for frontend-only work, documentation-only edits, generic Java code with no database access, backend changes that do not touch persistence or queries, non-Java stacks, non-JPA data access such as MongoDB/Redis-only work, infrastructure-only database provisioning, or simple repository navigation/status tasks.'
---

# JPA/Hibernate Patterns

Use this skill on demand for database-backed Java/Spring Boot backend work where JPA, Hibernate, Spring Data repositories, transactions, or queries are part of the design or implementation.

## When To Apply

Load this skill when planning, implementing, reviewing, or debugging Java/Spring Boot backend features that require database modeling, persistence behavior, or query design.

Apply it for:

- Designing or changing JPA entities, table mappings, identifiers, constraints, enums, embedded values, or column types
- Defining relationships such as `@OneToMany`, `@ManyToOne`, `@OneToOne`, or `@ManyToMany`
- Creating or changing Spring Data repositories, derived queries, JPQL, native SQL, Specifications, Criteria queries, or custom repository implementations
- Planning query behavior for filtering, sorting, pagination, projections, aggregate reads, reporting views, or cursor-like access patterns
- Optimizing database access, including N+1 prevention, fetch joins, entity graphs, lazy/eager loading choices, batch sizes, indexes, and query plans
- Designing transaction boundaries, consistency rules, locking, auditing, soft deletes, optimistic concurrency, or write flows
- Adding or reviewing migrations that must stay aligned with JPA mappings and query patterns
- Tuning Hibernate, HikariCP, second-level cache, or persistence-related application properties

Do not load this skill for:

- Frontend-only changes, UI behavior, styling, or client-side state management
- Documentation-only edits that do not require validating persistence guidance
- Generic Java refactoring, algorithms, DTO mapping, controller routing, validation, security, or logging when no database behavior or query changes are involved
- Backend work in non-Java stacks or Java codebases that do not use JPA/Hibernate/Spring Data
- MongoDB, Redis, Elasticsearch, Kafka, object storage, or other non-JPA persistence work unless it also changes JPA-backed data access
- Infrastructure-only tasks such as creating a database container, Terraform/RDS provisioning, CI setup, or environment variables without JPA/query design impact
- Simple repository inspection, file search, formatting, dependency listing, or status checks

## Entity Design

```java
@Entity
@Table(name = "markets", indexes = {
  @Index(name = "idx_markets_slug", columnList = "slug", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
public class MarketEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, unique = true, length = 120)
  private String slug;

  @Enumerated(EnumType.STRING)
  private MarketStatus status = MarketStatus.ACTIVE;

  @CreatedDate private Instant createdAt;
  @LastModifiedDate private Instant updatedAt;
}
```

Enable auditing:
```java
@Configuration
@EnableJpaAuditing
class JpaConfig {}
```

## Relationships and N+1 Prevention

```java
@OneToMany(mappedBy = "market", cascade = CascadeType.ALL, orphanRemoval = true)
private List<PositionEntity> positions = new ArrayList<>();
```

- Default to lazy loading; use `JOIN FETCH` in queries when needed
- Avoid `EAGER` on collections; use DTO projections for read paths

```java
@Query("select m from MarketEntity m left join fetch m.positions where m.id = :id")
Optional<MarketEntity> findWithPositions(@Param("id") Long id);
```

## Repository Patterns

```java
public interface MarketRepository extends JpaRepository<MarketEntity, Long> {
  Optional<MarketEntity> findBySlug(String slug);

  @Query("select m from MarketEntity m where m.status = :status")
  Page<MarketEntity> findByStatus(@Param("status") MarketStatus status, Pageable pageable);
}
```

- Use projections for lightweight queries:
```java
public interface MarketSummary {
  Long getId();
  String getName();
  MarketStatus getStatus();
}
Page<MarketSummary> findAllBy(Pageable pageable);
```

## Transactions

- Annotate service methods with `@Transactional`
- Use `@Transactional(readOnly = true)` for read paths to optimize
- Choose propagation carefully; avoid long-running transactions

```java
@Transactional
public Market updateStatus(Long id, MarketStatus status) {
  MarketEntity entity = repo.findById(id)
      .orElseThrow(() -> new EntityNotFoundException("Market"));
  entity.setStatus(status);
  return Market.from(entity);
}
```

## Pagination

```java
PageRequest page = PageRequest.of(pageNumber, pageSize, Sort.by("createdAt").descending());
Page<MarketEntity> markets = repo.findByStatus(MarketStatus.ACTIVE, page);
```

For cursor-like pagination, include `id > :lastId` in JPQL with ordering.

## Indexing and Performance

- Add indexes for common filters (`status`, `slug`, foreign keys)
- Use composite indexes matching query patterns (`status, created_at`)
- Avoid `select *`; project only needed columns
- Batch writes with `saveAll` and `hibernate.jdbc.batch_size`

## Connection Pooling (HikariCP)

Recommended properties:
```
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.validation-timeout=5000
```

For PostgreSQL LOB handling, add:
```
spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true
```

## Caching

- 1st-level cache is per EntityManager; avoid keeping entities across transactions
- For read-heavy entities, consider second-level cache cautiously; validate eviction strategy

## Migrations

- Use Flyway or Liquibase; never rely on Hibernate auto DDL in production
- Keep migrations idempotent and additive; avoid dropping columns without plan

## Testing Data Access

- Prefer `@DataJpaTest` with Testcontainers to mirror production
- Assert SQL efficiency using logs: set `logging.level.org.hibernate.SQL=DEBUG` and `logging.level.org.hibernate.orm.jdbc.bind=TRACE` for parameter values

**Remember**: Keep entities lean, queries intentional, and transactions short. Prevent N+1 with fetch strategies and projections, and index for your read/write paths.
