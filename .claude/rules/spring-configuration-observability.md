---
paths:
  - 'stream-tube-backend/src/main/resources/application.properties'
  - 'stream-tube-backend/src/main/resources/application.yml'
description: 'Spring Boot configuration and observability conventions using Actuator and externalized settings'
---

# Configuration and Observability Rules

## Externalized Configuration

- Use `@ConfigurationProperties` for grouping related settings instead of multiple `@Value` annotations.
- Define a POJO for configuration and use `@EnableConfigurationProperties` or `@ConfigurationPropertiesScan`.
- Prefer `application.yml` over `application.properties` for complex hierarchical structures.
- Use **Profiles** (`application-{profile}.yml`) for environment-specific settings (e.g., `dev`, `test`, `prod`).

## Observability with Actuator

- Include `spring-boot-starter-actuator` in all production projects.
- **Endpoints:** Expose only necessary endpoints. Always keep `/health` and `/info` public (if safe), and secure others like `/metrics`, `/env`, or `/beans`.
- **Health Indicators:** Implement custom `HealthIndicator` for external dependencies (third-party APIs, specialized hardware).
- **Metrics:** Use Micrometer for custom metrics (`Counter`, `Timer`, `Gauge`) to track business KPIs.

## Logging

- Use **SLF4J** via Lombok's `@Slf4j` annotation.
- Never use `System.out.println()`.
- Follow logging levels correctly:
    - `ERROR`: Unexpected issues requiring immediate attention.
    - `WARN`: Potential issues or deprecated usage.
    - `INFO`: Significant lifecycle events.
    - `DEBUG`: Detailed information for troubleshooting.
- Use structured logging (JSON) in production for better log aggregation.

## The Rule

- Secrets (passwords, keys) must NEVER be hardcoded; use environment variables or a Secret Manager.
- All configuration classes must be validated using `@Validated` if they contain required fields.
- Actuator endpoints must be secured when exposed over HTTP.
- Keep logs clean and avoid logging sensitive data (PII, credentials).
