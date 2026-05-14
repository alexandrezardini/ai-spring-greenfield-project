# CLAUDE.md

## Environment Startup Verification

**Default behavior:** `docker compose up -d` starts the `db` and `spring-boot-app` containers, but the Spring Boot application must be started manually inside the container (the dev Dockerfile uses `CMD ["tail", "-f", "/dev/null"]` to keep the container alive without auto-launching the app).

**Full startup sequence:**

```bash
# 1. Start containers
docker compose up -d

# 2. Verify containers are healthy
docker compose ps   # both services must show "running" / "healthy"

# 3. Start the Spring Boot app inside the container (long-running — run in background)
docker compose exec -d spring-boot-app sh -c \
  './mvnw spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/app.log 2>&1'

# 4. Wait for startup and tail logs
docker compose exec spring-boot-app sh -c \
  'until grep -qE "Started StreamTubeBackendApplication|APPLICATION FAILED" /tmp/app.log 2>/dev/null; \
   do sleep 3; done && tail -30 /tmp/app.log'
```

Then verify each service is ready:

- **Spring Boot API:** `curl http://localhost:8080/actuator/health` — expect `{"status":"UP"}`
- **PostgreSQL:** `docker compose exec db pg_isready -U streamtube` — expect `accepting connections`

## Development Environment

This project runs inside Docker. Always use the container for development:

```bash
# Start containers
docker compose up -d

# Install dependencies (first time only)
docker compose exec spring-boot-app ./mvnw install

# Run the application
docker compose exec spring-boot-app ./mvnw spring-boot:run
```

Services:
- `spring-boot-app` — Spring Boot API, port `8080`
- `db` — PostgreSQL 17, port `5432`, database `streamtube`, user/password `streamtube`

All verification and teardown commands run on the **host machine**:

```bash
# Verify Spring Boot is running (expect {"status":"UP"})
curl http://localhost:8080/actuator/health

# Verify PostgreSQL is ready (runs inside the db container)
docker compose exec db pg_isready -U streamtube

# Check container logs
docker compose logs spring-boot-app
docker compose logs db

# Tear down containers (preserves the postgres_data volume — data survives)
docker compose down

# Tear down containers AND delete all data (forces fresh PostgreSQL init on next up)
docker compose down -v
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

## Code Formatting

This project uses [Spotless](https://github.com/diffplug/spotless) with `google-java-format` (GOOGLE style).

```bash
./mvnw spotless:apply   # fix formatting violations
./mvnw spotless:check   # verify formatting (also runs automatically in mvn verify)
```

Spotless `check` is bound to the `verify` phase — a formatting violation will fail the build at `mvn verify`.

> **Note:** Requires JDK 26+ (google-java-format 1.27.0). Does not work on JDK 17/21 with this configuration.

## Troubleshooting

### `FATAL: role "streamtube" does not exist`

**Cause:** The `postgres_data` Docker volume was initialized by a previous container with different credentials (or with no credentials at all). PostgreSQL only runs its initialization scripts (`POSTGRES_USER`, `POSTGRES_DB`, `POSTGRES_PASSWORD`) on a **fresh, empty data directory** — if the volume already contains data, these env vars are silently ignored.

**Fix:** Remove the volume and restart so PostgreSQL re-initializes from scratch:

```bash
docker compose down -v   # stops containers AND removes the postgres_data volume
docker compose up -d     # fresh init — streamtube role and database are created correctly
```

> **Warning:** `docker compose down -v` deletes all database data. Only use this when the database is empty or the data is disposable (e.g., local dev reset).

**Verify the fix:**

```bash
docker compose exec db psql -U streamtube -d streamtube -c "\du"
# should list the streamtube role
```

## IDE Setup

**Lombok:** Install the Lombok plugin for your IDE to enable annotation processing support:
- **IntelliJ IDEA:** Preferences → Plugins → search "Lombok" → install and enable annotation processing (Settings → Build → Compiler → Annotation Processors → Enable annotation processing).
- **VS Code:** Install the "Lombok Annotations Support for VS Code" extension.
