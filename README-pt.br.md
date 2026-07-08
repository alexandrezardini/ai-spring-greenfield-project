# StreamTube

*[Read this in English](README.md)*

StreamTube é uma plataforma de compartilhamento de vídeos (estilo YouTube). Usuários cadastrados podem fazer upload, gerenciar e publicar vídeos; visitantes anônimos podem assistir livremente; funcionalidades sociais (comentários, inscrições, likes) exigem autenticação.

Este repositório é, antes de tudo, um **estudo de caso**: o projeto é construído **100% através de workflows de IA** (skills, MCP e rules), com revisão humana, para demonstrar até que ponto um fluxo de desenvolvimento orientado por agentes — da pesquisa técnica ao código e aos testes — se sustenta em um projeto real, do zero ao deploy.

## Objetivo do projeto

O StreamTube em si (vídeos, canais, comentários) é o veículo, não o fim. O que este repositório documenta e valida é o **workflow de IA**:

- Até que ponto planejamento, pesquisa de decisões técnicas, implementação e testes podem ser conduzidos por agentes de IA operando com contexto explícito (regras, skills e ferramentas MCP), com humano revisando e aprovando.
- Como estruturar esse contexto (`.claude/rules`, `.claude/skills`, `.mcp.json`) para que o agente produza código consistente com as convenções do projeto sem repetição manual de instruções.
- Qual a qualidade resultante em termos de arquitetura, cobertura de testes e aderência a boas práticas quando o ciclo completo (pesquisa → plano → implementação → testes) é guiado por skills dedicadas.

## Funcionalidades da plataforma

- **Acesso anônimo:** qualquer pessoa assiste vídeos sem cadastro.
- **Cadastro com confirmação por e-mail:** o prefixo do e-mail vira o nome do canal.
- **Login, logout e recuperação de senha** via fluxo de token por e-mail.
- **Upload robusto de vídeos** (até 10GB) sem travar a aplicação.
- **Processamento assíncrono** de vídeo (metadados, duração, geração de thumbnail) via fila em background.
- **Streaming** do vídeo sem necessidade de download completo, além de download direto.
- **Gerenciamento de vídeos:** rascunho → publicação, edição de título/descrição/categoria/thumbnail, visibilidade pública ou unlisted.
- **Canais:** página pública, painel de administração, edição de nickname/nome/descrição.
- **Interações sociais:** likes/dislikes em vídeos e comentários, comentários com respostas aninhadas, inscrição em canais.
- **Descoberta:** home page com listagens paginadas, filtro por categoria, busca por título/canal, sugestões relacionadas.

Roadmap completo e critérios de cada etapa em [`docs/project-plan.md`](docs/project-plan.md).

## Estrutura do repositório

```
.
├── stream-tube-backend/   # API Spring Boot (foco atual do desenvolvimento)
├── nextjs-project/        # Frontend Next.js (ainda não iniciado)
├── docs/                  # Plano do projeto, arquitetura, fases e decisões técnicas
├── .claude/
│   ├── rules/              # Convenções obrigatórias aplicadas a cada categoria de arquivo
│   └── skills/             # Capacidades especializadas invocadas sob demanda pelos agentes
└── .mcp.json               # Servidores MCP disponíveis para os agentes (ex.: acesso ao Postgres)
```

## Como o workflow de IA é estruturado

O desenvolvimento segue um ciclo repetível por fase do projeto, apoiado em três mecanismos do Claude Code:

### 1. Rules — convenções sempre ativas

Arquivos em [`stream-tube-backend/.claude/rules`](stream-tube-backend/.claude/rules) (`spring-controllers`, `spring-entities`, `spring-dtos`, `spring-security`, `spring-testing`, `spring-layer-separation`, `spring-modulith`, `liquibase-migrations`, `spring-configuration-observability`, `spring-common-conventions`) descrevem os padrões arquiteturais do backend — camadas, modularização, segurança, migrations e testes — e são aplicadas automaticamente aos arquivos correspondentes, sem precisar ser repetidas a cada prompt.

### 2. Skills — capacidades especializadas sob demanda

Skills em [`.claude/skills`](.claude/skills) cobrem o ciclo de vida de cada fase:

| Skill | Papel no workflow |
|---|---|
| `research` | Explora alternativas técnicas e gera o documento de decisões de uma fase (`docs/decisions/`) |
| `plan-phase` | Transforma as decisões em um plano de implementação detalhado (`docs/phases/phase-NN.md`) |
| `implement-phase` | Executa o plano passo a passo, respeitando dependências, rodando testes após cada incremento e só avançando com testes verdes |
| `java-architect` / `spring-boot-engineer` | Design e implementação de arquitetura Java/Spring Boot 3.x (camadas, transações, concorrência, REST) |
| `jpa-pattern` | Padrões de persistência JPA/Hibernate/Spring Data (entidades, relacionamentos, queries, migrations) |
| `database-optimizer` / `postgres-pro` | Modelagem, indexação e performance de queries PostgreSQL |
| `test-master` | Estratégia e geração de testes unitários, integração e cobertura |

Esse encadeamento (`research` → `plan-phase` → `implement-phase`) é o que permite reconstituir, para cada fase entregue, *por que* uma decisão técnica foi tomada, não só *o que* foi implementado.

### 3. MCP — acesso a sistemas externos

O servidor MCP configurado em [`.mcp.json`](.mcp.json) dá aos agentes acesso direto ao PostgreSQL do projeto (`postgres://streamtube@localhost:5432/streamtube`), permitindo inspecionar schema e dados reais durante o desenvolvimento e a revisão, em vez de inferir a partir de migrations isoladamente.

## Testes como parte do workflow

Testar é tratado como etapa obrigatória do ciclo, não como revisão posterior:

- A skill `implement-phase` só avança para o próximo incremento se os testes do incremento anterior passarem.
- O backend usa JUnit 5, Testcontainers (PostgreSQL real em container) e Spring Security Test para integração, além de testes de camada com `spring-boot-starter-*-test`.
- A regra é rodar os testes relacionados à mudança durante o desenvolvimento e a suíte completa antes de finalizar qualquer tarefa.

## Backend (`stream-tube-backend/`)

Spring Boot 3.x, Java 17+, arquitetura modular ([Spring Modulith](https://spring.io/projects/spring-modulith)) com PostgreSQL e migrations via Liquibase. Ambiente 100% Docker.

Instruções completas de setup, comandos e troubleshooting em [`stream-tube-backend/CLAUDE.md`](stream-tube-backend/CLAUDE.md).

```bash
# Subir containers
docker compose up -d

# Rodar a aplicação (dentro do container)
docker compose exec spring-boot-app ./mvnw spring-boot:run

# Verificar saúde da API
curl http://localhost:8080/actuator/health
```

## Documentação do projeto

- [`docs/project-plan.md`](docs/project-plan.md) — visão geral, fases e pontos de atenção
- [`docs/software-arch.memaid`](docs/software-arch.memaid) — diagrama de arquitetura C4
- [`docs/decisions/`](docs/decisions) — decisões técnicas registradas por fase
- [`docs/phases/`](docs/phases) — planos de implementação e progresso por fase
