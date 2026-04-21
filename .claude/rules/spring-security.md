---
paths:
  - 'stream-tube-backend/src/main/java/**/config/SecurityConfig.java'
  - 'stream-tube-backend/src/main/java/**/security/**/*.java'
description: 'Spring Security conventions for authentication, authorization, and protection'
---

# Spring Security Rules

## Configuration

- Use a dedicated `SecurityConfig` class annotated with `@Configuration` and `@EnableWebSecurity`.
- Use the **SecurityFilterChain** bean definition instead of the deprecated `WebSecurityConfigurerAdapter`.
- Prefer **Lambda DSL** for configuring `HttpSecurity`.

## Authentication and Authorization

- **JWT/OAuth2:** For stateless APIs, prefer JWT-based authentication using Spring Security's OAuth2 Resource Server support.
- **Method Security:** Use `@PreAuthorize`, `@PostAuthorize`, or `@Secured` to enforce authorization at the service layer.
- **Role-Based Access Control (RBAC):** Use standard role names (e.g., `ROLE_USER`, `ROLE_ADMIN`) and refer to them using `hasRole('ADMIN')`.

## Protection

- **CSRF:** Disable CSRF only for stateless APIs (JWT/Token based). For session-based APIs, it must be enabled.
- **CORS:** Configure CORS explicitly using `CorsConfigurationSource` beans rather than global `@CrossOrigin` on every controller.
- **Password Encoding:** Always use `BCryptPasswordEncoder` or `Argon2PasswordEncoder`. Never store plain-text passwords.

## Example (SecurityFilterChain)

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/public/**").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        )
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
}
```

## The Rule

- Secure all endpoints by default (`anyRequest().authenticated()`).
- Business-level authorization logic belongs in `@PreAuthorize` on Service methods.
- Never use `@Autowired` on the `AuthenticationManager`; instead, configure it via `AuthenticationConfiguration`.
- Handle security exceptions (401, 403) consistently with the global exception handler using `AuthenticationEntryPoint` and `AccessDeniedHandler`.
