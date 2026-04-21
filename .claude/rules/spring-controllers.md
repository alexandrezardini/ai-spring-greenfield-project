---
paths:
  - 'stream-tube-backend/src/main/java/**/*Controller.java'
description: 'Controller conventions — REST compliance, global exception handling, and thin controllers'
---

# Controller Rules

## REST Compliance

Controllers are the HTTP layer — they must follow the REST principles. When editing a controller, enforce:

- Use `@RestController` instead of `@Controller` to ensure `@ResponseBody` is applied to all methods.
- Use the correct HTTP method annotations: `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`.
- Return appropriate status codes:
    - `201 Created` for successful POST requests (use `@ResponseStatus(HttpStatus.CREATED)`).
    - `204 No Content` for successful DELETE or PUT/PATCH requests that return no body.
    - `200 OK` for other successful operations.
- Use plural nouns for resource paths: `@RequestMapping("/users")`, not `/user`.
- Follow nesting patterns for sub-resources: `/channels/{channelId}/videos`.

## Error Handling

## Never Swallow Errors

Controllers must never catch an error and return a generic error body or a fallback value manually. Errors must be allowed to propagate to the global exception handler.

## Global Exception Handling with @RestControllerAdvice

Controllers should not contain `try/catch` blocks. Instead, use a `@RestControllerAdvice` class with `@ExceptionHandler` methods to catch domain exceptions and map them to HTTP responses.

This keeps controllers thin and ensures consistent error response formats across the entire API.

## Bad: try/catch in controller

```java
@GetMapping("/{id}")
public ResponseEntity<?> findOne(@PathVariable String id) {
    try {
        return ResponseEntity.ok(userService.findById(id));
    } catch (Exception e) {
        return ResponseEntity.status(500).body("Something went wrong"); // manual, untyped, wrong status
    }
}
```

## Good: let exception advice handle it

```java
@GetMapping("/{id}")
public UserResponse findOne(@PathVariable String id) {
    return userService.findById(id); 
    // If user is not found, service throws UserNotFoundException.
    // The GlobalExceptionHandler maps it to 404 Not Found.
}
```

## The Rule

- Controllers must not contain `try/catch` blocks — delegate error handling to `@RestControllerAdvice`.
- Services throw domain-specific exceptions (e.g., `EntityNotFoundException`) — never Spring Web exceptions (like `ResponseStatusException`) directly in business logic.
- Use `ProblemDetail` (introduced in Spring Boot 3) or a custom standard error response object for all API errors.
- Apply `@Valid` or `@Validated` on request bodies to trigger Bean Validation.
- Controllers should only handle:
    1. Input validation (triggering Bean Validation).
    2. Calling the appropriate Service.
    3. Mapping the result to a DTO/Response (if not done in the service).
    4. Defining the HTTP status code and headers.
