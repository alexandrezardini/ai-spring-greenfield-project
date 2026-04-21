---
paths:
  - 'stream-tube-backend/src/main/java/**/*.java'
description: 'Spring Data JDBC entity and repository conventions'
---

# Entity and Repository Rules

## Naming and Structure

- Use `@Table("table_name")` to explicitly define the table name in the database.
- Use `@Id` for the primary key. In Spring Data JDBC, this field can be `Long` or `UUID`.
- For auditing, use `@CreatedDate` and `@LastModifiedDate` (requires `@EnableJdbcAuditing`).

## Entity Conventions

- Entities should be standard Java classes (not `record`) if they need to be mutable or if you are using a framework that requires a default constructor.
- For Spring Data JDBC, entities are ideally immutable (using `@PersistenceCreator` or Lombok's `@Builder`/`@Value`).
- Sensitive fields (e.g., `password`) should be marked to avoid exposure, though Spring Data JDBC doesn't have a direct equivalent to TypeORM's `{ select: false }`. Use DTOs to exclude them.

## Repository Conventions

- Extend `CrudRepository<Entity, IdType>`, `ListCrudRepository`, or `PagingAndSortingRepository`.
- Use **Query Methods** for simple lookups (e.g., `findByEmail(String email)`).
- Use `@Query` for complex queries that are difficult to express via method names.
- Repositories must return `Optional<T>` for single results and `List<T>` or `Page<T>` for multiple results.

## Relationships

- Spring Data JDBC treats the aggregate root as the boundary.
- Use `AggregateReference<OtherEntity, IdType>` to reference entities in other aggregates (Modulith compliant).
- For one-to-many relationships within the same aggregate, use a `Set<ChildEntity>` or `List<ChildEntity>`.

## Schema Changes

- **Never modify an entity without updating the Liquibase changelog.**
- Entities and database schema must always stay in sync.
- Define column constraints (e.g., `NOT NULL`, `UNIQUE`) in the Liquibase script, not just in the Java code.

## The Rule

- Entities represent the persistent state and should be kept as close to the database schema as possible.
- Business logic that only involves the entity's state can live in the entity class (Rich Domain Model).
- Complex data access logic belongs in the Repository using custom implementations if necessary.
- **Never expose Repositories to Controllers** — always go through a Service.
