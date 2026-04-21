---
paths:
  - 'stream-tube-backend/src/main/java/**/*.java'
description: 'Layer separation — business logic belongs exclusively in services; controllers and other components must delegate to services'
---

# Layer Separation Rules

## Business Logic Lives in Services

Services are the single home for all business rules and core domain logic. No other artifact type may implement business logic directly.

## Controllers Are Thin

Controllers receive HTTP requests, delegate to services, and return the response. They must not contain conditionals, transformations, or decisions that encode business rules.

Controllers **may** contain simple request-level decisions — such as checking if a service returned an `Optional.empty()` and throwing an appropriate exception, or deciding which service to call based on a query parameter. These are HTTP-layer concerns, not business rules.

## Aspect-Oriented Components and Security

Security checks (`@PreAuthorize`), logging aspects, and validation filters handle cross-cutting infrastructure concerns. When they need to make a decision based on business logic, they should ideally rely on values provided by the service layer or call a specialized service.

### Bad: business logic in a controller

```java
@PostMapping("/{id}/status")
public void updateStatus(@PathVariable String id, @RequestBody String status) {
    User user = userService.findById(id);
    // business rule leaked into controller
    if ("ADMIN".equals(user.getRole()) && !"DELETED".equals(status)) {
        user.setStatus(status);
        userRepository.save(user);
    } else {
        throw new AccessDeniedException("Invalid operation");
    }
}
```

### Good: controller delegates to a service

```java
@PostMapping("/{id}/status")
public void updateStatus(@PathVariable String id, @RequestBody String status) {
    userService.updateUserStatus(id, status);
}
```

## The Rule

- **Services** own business logic — they are the only place where domain rules, validations, and decisions live.
- **Controllers** are thin: receive request, call service(s), return response. 
- **Repositories** are for data access only. Do not put business logic in custom repository implementations.
- If you find yourself writing `if` statements that encode domain rules outside a service, move that logic into the appropriate service.
- **Transactional Boundary:** Always use `@Transactional` at the service layer to ensure atomic operations. Avoid putting `@Transactional` on controllers.
