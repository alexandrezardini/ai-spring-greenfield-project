---
paths:
  - 'stream-tube-backend/src/test/java/**/*.java'
description: 'Testing conventions for Spring Boot using JUnit 5, Mockito, and Testcontainers'
---

# Testing Rules

## Unit Tests

- Use **JUnit 5** and **Mockito**.
- Use `@ExtendWith(MockitoExtension.class)` to enable Mockito support.
- Follow the naming pattern: `[ClassName]Test`.
- Place unit tests in the same package structure under `src/test/java`.
- Test business logic in Services by mocking Repositories and other dependencies.

## Slice Tests (Integration Tests)

- Use **Spring Boot Slice Tests** to test specific layers in isolation:
    - `@WebMvcTest` for Controllers (mocks the service layer).
    - `@JdbcTest` or `@DataJdbcTest` for Repositories.
- Use `MockMvc` to perform requests and verify responses in `@WebMvcTest`.

## Integration Tests with Testcontainers

- Use **Testcontainers** for tests that require a real database (PostgreSQL).
- Use `@SpringBootTest` for full integration tests.
- Use `@Import(TestcontainersConfiguration.class)` if a shared configuration is available.
- Do not mock the database in integration tests.

## E2E / Functional Tests

- Use `RestTemplate` or `WebTestClient` to make HTTP calls against the running application in `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)`.
- Test complete request/response cycles, including security (JWT/OAuth2) if applicable.

## General Practices

- Follow the **Arrange-Act-Assert (AAA)** pattern.
- Use **AssertJ** for fluent and readable assertions (e.g., `assertThat(result).isEqualTo(expected)`).
- **Test Data:** Use "Object Mother" or "Data Builders" to create test instances. Avoid hardcoding large objects inside test methods.
- **Cleanup:** Spring handles most cleanup for slice tests. For Testcontainers, use a fresh container or ensure data is wiped between tests if necessary (though `@Transactional` on tests usually handles rollback).

## The Rule

- Every new feature must have corresponding unit tests and at least one integration test.
- Use `@Mock` for dependencies and `@InjectMocks` for the class under test.
- Tests should be fast and deterministic. Avoid `Thread.sleep()`.
- Run tests using Maven: `./mvnw test`.
- All tests must pass before submitting code.
