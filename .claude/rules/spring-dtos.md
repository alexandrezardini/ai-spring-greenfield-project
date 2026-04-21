---
paths:
  - 'stream-tube-backend/src/main/java/**/*.java'
description: 'DTO conventions for input validation and data transfer using Java Records'
---

# DTO Rules

## Validation

- Use **Bean Validation** (Jakarta Validation) annotations on DTO fields to enforce constraints:
    - `@NotNull`, `@NotBlank`, `@Size`, `@Email`, `@Min`, `@Max`, etc.
- For nested objects, use `@Valid` to trigger validation on the nested DTO.

## Separation of Concerns

- Create separate DTOs for different operations: `UserCreateRequest`, `UserUpdateRequest`, `UserResponse`.
- **Never use an Entity class as a DTO.** Entities represent the database schema, while DTOs represent the API contract.
- Use Java **Records** for all DTOs. They provide:
    - Immutability by default.
    - Automatic `equals()`, `hashCode()`, and `toString()`.
    - Concise syntax with no boilerplate.

## Naming

- File naming: `UserCreateRequest.java`, `UserResponse.java` (matching the record name).
- Record naming:
    - Input: `[Entity][Action]Request` (e.g., `UserCreateRequest`, `AuthLoginRequest`).
    - Output: `[Entity]Response` or `[Entity]View` (e.g., `UserResponse`, `VideoSummaryResponse`).

## Implementation Example

```java
public record UserCreateRequest(
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50)
    String username,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8)
    String password
) {}
```

## The Rule

- All DTOs must be implemented as `record`.
- Every field in an input DTO must have at least one validation annotation if it's not purely optional.
- DTOs should not contain any business logic; they are simple data carriers.
- Use a library like **MapStruct** or manual mapping methods in the Record/Service to convert between DTOs and Entities. Avoid exposing internal entity structure directly.
