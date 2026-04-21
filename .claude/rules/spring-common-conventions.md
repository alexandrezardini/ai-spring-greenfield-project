---
paths:
  - 'stream-tube-backend/src/main/java/**/*.java'
description: 'Spring Boot common conventions'
---

# Spring Boot Standard Conventions

## Naming

| Artifact    | Class Suffix | Package Example             | Class Name Example     |
|-------------|--------------|-----------------------------|------------------------|
| Application | `Application`| `org.example.project`      | `ProjectApplication`   |
| Controller  | `Controller` | `*.controller`              | `UserController`       |
| Service     | `Service`    | `*.service`                 | `UserService`          |
| Repository  | `Repository` | `*.repository`              | `UserRepository`       |
| Entity      | (none)       | `*.entity` ou `*.model`    | `User`                 |
| DTO         | `Record` / `DTO` | `*.dto`                 | `UserCreateRequest`    |
| Config      | `Config`     | `*.config`                  | `SecurityConfig`       |

- **Files:** PascalCase (Java Standard)
- **Classes:** PascalCase
- **Packages:** lowercase, dot-separated (`org.example.project.user`)
- **Methods/Variables:** camelCase
- **Constants:** UPPER_SNAKE_CASE

## Dependency Injection

- **Constructor Injection:** Always prefer constructor injection over `@Autowired` on fields.
- **Final Fields:** Mark injected dependencies as `private final` to ensure immutability.
- **Lombok (if available):** Use `@RequiredArgsConstructor` to generate the constructor for `final` fields.
- **Interfaces:** Inject the interface, not the implementation, to promote loose coupling (especially for Services and Repositories).

## Optional and Null Safety

- Use `Optional<T>` for return types that might be empty (e.g., repository lookups).
- Never return `null` for collections; return `Collections.emptyList()` or similar.
- Use Spring's `@NonNullApi` or JSR-305 `@Nullable` / `@Nonnull` annotations to document API expectations.

## Records

- Use Java `record` for DTOs and data carriers to reduce boilerplate and ensure immutability.
- Avoid using `record` for JPA entities (they require a default constructor and non-final fields). For Spring Data JDBC, records are supported but should be used carefully with relationships.

## Constants

- Group related constants in a dedicated class or interface within the appropriate package.
- Use `public static final` for constants.
- For configuration properties, use `@ConfigurationProperties` instead of hardcoded constants.
