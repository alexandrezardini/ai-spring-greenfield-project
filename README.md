# StreamTube

*[Read this in Portuguese / Leia em português (Brasil)](README-pt.br.md)*

StreamTube is a video-sharing platform (YouTube-like). Registered users can upload, manage, and publish videos; anonymous visitors can watch freely; social features (comments, subscriptions, likes) require authentication.

This repository is, above all, a **case study**: the project is built **100% through AI workflows** (skills, MCP, and rules), with human review, to demonstrate how far an agent-driven development flow — from technical research to code and tests — holds up on a real project, from scratch to deployment.

## Project goal

StreamTube itself (videos, channels, comments) is the vehicle, not the point. What this repository documents and validates is the **AI workflow**:

- How far planning, technical decision research, implementation, and testing can be carried out by AI agents operating with explicit context (rules, skills, and MCP tools), with a human reviewing and approving.
- How to structure that context (`.claude/rules`, `.claude/skills`, `.mcp.json`) so the agent produces code consistent with the project's conventions without manually repeating instructions.
- What quality results — in terms of architecture, test coverage, and adherence to best practices — when the full cycle (research → plan → implementation → tests) is guided by dedicated skills.

## Platform features

- **Anonymous access:** anyone can watch videos without signing up.
- **Sign-up with email confirmation:** the email prefix becomes the channel name.
- **Login, logout, and password recovery** via an email token flow.
- **Robust video upload** (up to 10GB) without blocking the application.
- **Asynchronous processing** of video (metadata, duration, thumbnail generation) via a background queue.
- **Streaming** playback without needing a full download, plus direct download.
- **Video management:** draft → publish flow, editing of title/description/category/thumbnail, public or unlisted visibility.
- **Channels:** public page, admin dashboard, nickname/name/description editing.
- **Social interactions:** likes/dislikes on videos and comments, nested comment replies, channel subscriptions.
- **Discovery:** home page with paginated listings, category filtering, search by title/channel, related suggestions.

Full roadmap and criteria for each stage in [`docs/project-plan.md`](docs/project-plan.md).

## Repository structure

```
.
├── stream-tube-backend/   # Spring Boot API (current development focus)
├── nextjs-project/        # Next.js frontend (not yet initialized)
├── docs/                  # Project plan, architecture, phases and technical decisions
├── .claude/
│   ├── rules/              # Mandatory conventions applied to each file category
│   └── skills/             # Specialized capabilities invoked on demand by agents
└── .mcp.json               # MCP servers available to agents (e.g., Postgres access)
```

## How the AI workflow is structured

Development follows a repeatable cycle per project phase, backed by three Claude Code mechanisms:

### 1. Rules — always-on conventions

Files in [`stream-tube-backend/.claude/rules`](stream-tube-backend/.claude/rules) (`spring-controllers`, `spring-entities`, `spring-dtos`, `spring-security`, `spring-testing`, `spring-layer-separation`, `spring-modulith`, `liquibase-migrations`, `spring-configuration-observability`, `spring-common-conventions`) describe the backend's architectural standards — layering, modularization, security, migrations, and testing — and are applied automatically to the matching files, without needing to be repeated in every prompt.

### 2. Skills — specialized capabilities on demand

Skills in [`.claude/skills`](.claude/skills) cover the lifecycle of each phase:

| Skill | Role in the workflow |
|---|---|
| `research` | Explores technical alternatives and produces the decisions document for a phase (`docs/decisions/`) |
| `plan-phase` | Turns the decisions into a detailed implementation plan (`docs/phases/phase-NN.md`) |
| `implement-phase` | Executes the plan step by step, respecting dependencies, running tests after each increment, and only moving forward once tests pass |
| `java-architect` / `spring-boot-engineer` | Design and implementation of Java/Spring Boot 3.x architecture (layers, transactions, concurrency, REST) |
| `jpa-pattern` | JPA/Hibernate/Spring Data persistence patterns (entities, relationships, queries, migrations) |
| `database-optimizer` / `postgres-pro` | PostgreSQL data modeling, indexing, and query performance |
| `test-master` | Strategy and generation of unit tests, integration tests, and coverage |

This chaining (`research` → `plan-phase` → `implement-phase`) is what makes it possible to reconstruct, for every delivered phase, *why* a technical decision was made — not just *what* was implemented.

### 3. MCP — access to external systems

The MCP server configured in [`.mcp.json`](.mcp.json) gives agents direct access to the project's PostgreSQL database (`postgres://streamtube@localhost:5432/streamtube`), allowing them to inspect real schema and data during development and review, instead of inferring from migrations alone.

## Testing as part of the workflow

Testing is treated as a mandatory step in the cycle, not a later review:

- The `implement-phase` skill only moves to the next increment if the previous increment's tests pass.
- The backend uses JUnit 5, Testcontainers (a real PostgreSQL container) and Spring Security Test for integration, plus layer tests via `spring-boot-starter-*-test`.
- The rule is to run the tests related to the change during development, and the full suite before finishing any task.

## Backend (`stream-tube-backend/`)

Spring Boot 3.x, Java 17+, modular architecture ([Spring Modulith](https://spring.io/projects/spring-modulith)) with PostgreSQL and Liquibase migrations. 100% Docker-based environment.

Full setup instructions, commands, and troubleshooting in [`stream-tube-backend/CLAUDE.md`](stream-tube-backend/CLAUDE.md).

```bash
# Start containers
docker compose up -d

# Run the application (inside the container)
docker compose exec spring-boot-app ./mvnw spring-boot:run

# Check API health
curl http://localhost:8080/actuator/health
```

## Project documentation

- [`docs/project-plan.md`](docs/project-plan.md) — overview, phases, and points of attention
- [`docs/software-arch.memaid`](docs/software-arch.memaid) — C4 architecture diagram
- [`docs/decisions/`](docs/decisions) — technical decisions recorded per phase
- [`docs/phases/`](docs/phases) — implementation plans and progress per phase
