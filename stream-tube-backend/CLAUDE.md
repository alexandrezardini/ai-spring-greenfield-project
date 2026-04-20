# CLAUDE.md

## Environment Startup Verification

**Default behavior:** `docker compose up -d` automatically starts **all services** including the Spring Boot application and PostgreSQL database.

After starting containers, always confirm they are up and healthy:

```bash
docker compose ps   # all services must show status "running"
```

Then verify each service is actually ready to accept connections:

- **Spring Boot API:** `curl http://localhost:8080/` — expect `HTTP/1.1 200`
- **PostgreSQL:** `docker compose exec postgres pg_isready -U streamtube` — expect `accepting connections`

The Spring Boot application starts automatically with `docker compose up -d` and is immediately accessible on port 8080.

## Development Environment

This project runs inside Docker. Always use the container for development:

```bash
# Start containers
docker compose up -d

# Install dependencies (first time only)
docker compose exec spring-boot-app mvn install

# Run the application
docker compose exec spring-boot-app mvn spring-boot:run
```

Services:
- `spring-boot-app` — Spring Boot API, port `8080`
- `db` — PostgreSQL 17, port `5432`, database `streamtube`, user/password `streamtube`

All verification and teardown commands run on the **host machine**:

```bash
# Verify Spring Boot is running (expect 200)
curl http://localhost:8080

# Verify PostgreSQL is ready (runs inside the db container)
docker compose exec db pg_isready -U streamtube

# Check container logs
docker compose logs spring-boot-app
docker compose logs db

# Tear down the entire environment
docker compose down
```

## Commands (run inside the container via `docker compose exec spring-boot-app <cmd>`)

```bash
mvn spring-boot:run                      # Run application
mvn clean package                        # Compile to target/

mvn test                                 # Unit tests
mvn test -Dgroups=integration           # Integration tests

mvn clean                                # Clean build artifacts
```

## Long-running Processes

Commands that never exit (application server) must be run in background in the Bash tool — otherwise the agent blocks indefinitely waiting for the process to return.

This applies to: `spring-boot:run` and any other persistent process.

## Architecture

Spring Boot with standard layered architecture. Source lives in `src/main/java/`, compiled output in `target/`.

- Each domain feature gets its own package structure (e.g., `com.streamtube.users`, `com.streamtube.videos`)
- Controllers handle HTTP routing; Services hold business logic; Repositories handle data access
- Dependency injection via Spring's `@Autowired` and constructor injection

## Code Conventions

- **Java:** Java 17+, strict type safety
- **Spring:** Standard Spring Boot conventions with `@RestController`, `@Service`, `@Repository` annotations
- **Build:** Maven for dependency management and build process
- **Code Style:** Follow standard Java conventions; meaningful class and method names

## REST Conventions

This is a RESTful API. All endpoints must follow standard REST conventions — correct HTTP methods, proper status codes, plural resource nouns, and consistent URL structure. Details are enforced via rules on controller files.
