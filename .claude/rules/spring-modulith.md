---
paths:
  - 'stream-tube-backend/src/main/java/**/*.java'
description: 'Spring Modulith conventions for modular monolith architecture'
---

# Spring Modulith Rules

## Module Structure

- Each top-level package under the main application package is considered a **Module**.
- Example: `org.example.app.user`, `org.example.app.video`, `org.example.app.order`.

## Encapsulation

- Only the top-level package of a module is **public** to other modules.
- Sub-packages (e.g., `org.example.app.user.internal`) are considered internal and should not be accessed by other modules.
- Spring Modulith enforces this during tests. **Do not use classes from internal packages of other modules.**

## Inter-module Communication

- Prefer **Asynchronous Event-based communication** using `ApplicationEventPublisher`.
- This decouples modules and prevents circular dependencies.
- When synchronous communication is necessary, call only the public API (classes in the top-level package) of the other module.

## Dependency Rules

- **No Circular Dependencies:** Module A cannot depend on Module B if Module B depends on Module A.
- Modules should depend on more stable modules (e.g., a "common" or "core" module).

## Testing

- Use `ApplicationModules.of(Application.class).verify()` in a test to ensure module boundaries are respected.
- Use `@ApplicationModuleTest` for integration tests that focus on a single module while mocking others.

## The Rule

- Respect package-private visibility for internal module components.
- Keep the public API of a module as small as possible.
- Use events for cross-module side effects to maintain high cohesion and low coupling.
- Run `mvn spring-modulith:documentation` (if configured) to generate up-to-date module diagrams and documentation.
