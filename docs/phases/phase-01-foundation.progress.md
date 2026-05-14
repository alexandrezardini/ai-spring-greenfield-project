# Phase 01 — Configuração Base do Projeto — Progress

**Status:** completed
**SIs:** 9/9 completed

### SI-01.1 — Rename root Java package to `com.streamtube.backend`
- **Status:** completed
- **Tests:** StreamTubeBackendApplicationTests — 1 test, 0 failures (BUILD SUCCESS)
- **Observations:** target/ era de propriedade do root (gerado via Docker); requer sudo para limpar. Java 26 disponível em /home/alexandre/.jdks/openjdk-26.0.1.

### SI-01.2 — Scaffold Spring Modulith module placeholders
- **Status:** completed
- **Tests:** ModularityTests — 1 test, 0 failures (BUILD SUCCESS)
- **Observations:** none

### SI-01.3 — Migrate configuration to YAML and create dev/test profiles
- **Status:** completed
- **Tests:** StreamTubeBackendApplicationTests — 1 test, 0 failures (BUILD SUCCESS)
- **Observations:** spring.liquibase.enabled mantido como false em application.yml (será ativado no SI-01.7).

### SI-01.4 — Add Lombok dependency and annotation processor
- **Status:** completed
- **Tests:** no tests
- **Observations:** CommonModule.java criado em common/ como smoke check de @Slf4j. Warnings de sun.misc.Unsafe do Java 26 são esperados e não afetam o build.

### SI-01.5 — Add Spring Boot Actuator and remove HelloController
- **Status:** completed
- **Tests:** ActuatorHealthIntegrationTest — 3 testes, 0 falhas (BUILD SUCCESS)
- **Observations:** Spring Boot 4.0 moveu @AutoConfigureMockMvc para org.springframework.boot.webmvc.test.autoconfigure (não mais em boot.test.autoconfigure.web.servlet). Requer @ActiveProfiles("test") para carregar application-test.yml com show-details=always. Componente liquibase não verificado aqui (será adicionado em SI-01.7 após habilitar Liquibase).

### SI-01.6 — Fix Docker Compose, rename Postgres service to `db`, introduce .env
- **Status:** completed
- **Tests:** no tests
- **Observations:** none

### SI-01.7 — Enable Liquibase with initial PostgreSQL extensions
- **Status:** completed
- **Tests:** LiquibaseExtensionsIntegrationTest — 2 testes, 0 falhas (BUILD SUCCESS)
- **Observations:** Spring Boot 4.0 removeu LiquibaseHealthIndicator — o componente liquibase não aparece em /actuator/health. A verificação de Liquibase é feita diretamente via LiquibaseExtensionsIntegrationTest. O endpoint /actuator/liquibase existe mas não está exposto (apenas health,info estão expostos).

### SI-01.8 — Add Spotless Maven plugin and EditorConfig
- **Status:** completed
- **Tests:** no tests (spotless:check passa em mvn verify)
- **Observations:** google-java-format 1.24/1.25/1.26 incompatíveis com Java 26 (NoSuchMethodError em Log$DeferredDiagnosticHandler.getDiagnostics). Resolvido com google-java-format 1.27.0. .editorconfig criado na raiz do monorepo. 16 ficheiros formatados via spotless:apply.

### SI-01.9 — Audit and consolidate the AI coding foundation
- **Status:** completed
- **Tests:** no tests
- **Observations:** Nenhuma referência stale encontrada. Usos de "org.example" em spring-modulith.md e spring-common-conventions.md são exemplos genéricos válidos. Usos de "postgres" em skills são variáveis Java (PostgreSQLContainer) e URLs de protocolo — não nomes de serviço Docker.
