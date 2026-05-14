# StreamTube — Planejamento Geral do Projeto

## 1. Visão Geral

O StreamTube é uma plataforma de compartilhamento de vídeos onde usuários cadastrados podem fazer upload, gerenciar e publicar vídeos. Usuários anônimos podem assistir livremente, enquanto funcionalidades sociais como comentários, inscrições e likes são exclusivas de usuários autenticados.

### Principais Características

- **Acesso anônimo:** qualquer pessoa pode assistir vídeos sem cadastro.
- **Cadastro com confirmação:** registro via e-mail com confirmação obrigatória. O prefixo do e-mail se torna o nome do canal.
- **Upload robusto:** suporte a arquivos de até 10GB sem impactar a performance do sistema.
- **Gerenciamento de vídeos:** rascunhos, edição de informações, visibilidade pública/unlisted, thumbnails customizadas.
- **Interações sociais:** likes/dislikes, comentários com respostas, inscrição em canais.
- **Canais:** cada usuário possui um canal com página pública e painel de administração.
- **Recuperação de senha:** fluxo completo de reset via e-mail.
- **Sugestões:** vídeos relacionados por categoria exibidos na sidebar.

### Stack Tecnológica

- **Frontend:** Next.js
- **Backend:** Spring Boot
- **Banco de dados:** PostgreSQL

---

## 2. Arquitetura do Software

Veja o diagrama de arquitetura do projeto: [software-arch.mermaid](diagrams/software-arch.mermaid)

---

## 3. Fases do Projeto

### Fase 01 — Configuração Base do Projeto

Preparação da fundação backend do projeto: repositório, ambiente de desenvolvimento, projeto Spring Boot, banco de dados PostgreSQL e serviços auxiliares.

- Repositório com estrutura preparada para monorepo
- Projeto Spring Boot backend inicializado em `stream-tube-backend/`
- Projeto Next.js frontend será criado posteriormente, fora do escopo backend atual
- Ambiente de desenvolvimento local para o backend e serviços auxiliares via Docker Compose
- Estrutura inicial do banco de dados PostgreSQL (schema, migrations e seeds) (sem tabelas ainda)
- Fundação de IA para coding.

**Entregáveis:** ambiente de desenvolvimento backend funcional, aplicação Spring Boot inicializada e banco de dados configurado.

---

### Fase 02 — Cadastro, Login e Gerenciamento de Conta

> Depende de: Fase 01

Fluxo backend completo de criação de conta, confirmação por e-mail, login, logout e recuperação de senha.

- Serviço de envio de e-mails transacionais
- API de cadastro de usuário com e-mail e senha
- Criação automática do canal do usuário a partir do prefixo do e-mail
- Confirmação de conta via API acionada por token enviado por e-mail
- API de login e controle de sessão do usuário
- API de logout
- API de recuperação de senha: solicitação via e-mail → link com token → redefinição
- Contratos de API necessários para futuras telas de cadastro, login, confirmação de conta e recuperação de senha no frontend Next.js

**Entregáveis:** APIs backend do fluxo completo de cadastro → confirmação → login → recuperação de senha funcionando. Canal criado automaticamente para cada usuário.

---

### Fase 03 — Upload e Processamento de Vídeos

> Depende de: Fase 01, Fase 02

Upload de arquivos grandes sem travar o sistema, processamento automático do vídeo e geração de URL única.

- Serviço de armazenamento de arquivos (vídeos e thumbnails)
- Serviço de processamento em segundo plano (filas)
- Upload de vídeos com suporte a arquivos de até 10GB sem impacto na performance
- Pré-cadastro automático do vídeo como rascunho ao iniciar o upload
- Processamento automático do vídeo após upload (extração de duração e metadados)
- Geração automática de thumbnail a partir de um frame do vídeo
- URL única por vídeo, sem conflito com outros vídeos
- Reprodução via streaming (sem necessidade de download completo)
- Download do vídeo pelo usuário

**Entregáveis:** upload de até 10GB funcional, processamento automático do vídeo, streaming funcionando, URLs únicas geradas.

---

### Fase 04 — Gerenciamento de Vídeos e Canal

> Depende de: Fase 02, Fase 03

APIs backend para edição das informações do vídeo, fluxo de rascunho e publicação, gerenciamento do canal e dados da página pública.

- Categorias de vídeo disponíveis na plataforma
- API de edição das informações do vídeo: título, descrição, categoria e thumbnail customizada
- Visibilidade do vídeo: público (aparece para todos) ou unlisted (somente via link)
- Fluxo de rascunho → publicação
- APIs de listagem e gerenciamento de vídeos do canal (thumbnail, título, visualizações, likes, comentários, tempo de publicação e status)
- API de edição de vídeos a partir do painel futuro
- API de edição das informações do canal: nickname, nome e descrição
- API pública do canal com informações e listagem de vídeos

**Entregáveis:** APIs backend para edição completa de vídeos, rascunho/publicação, gerenciamento de canal e dados públicos do canal.

---

### Fase 05 — Página de Visualização do Vídeo

> Depende de: Fase 03, Fase 04

APIs backend necessárias para visualização de vídeo, player futuro, descrição, sugestões e acesso anônimo.

- API para disponibilizar dados e URLs de reprodução do vídeo
- Dados necessários para o player futuro: URL de streaming/download, duração e metadados disponíveis
- API para dados da página: vídeo principal + informações + sugestões
- Descrição do vídeo disponível via API
- Contagem de visualizações
- Sugestões de vídeos da mesma categoria
- Acesso anônimo à visualização de vídeos
- Endpoint de download do vídeo
- Vídeos unlisted acessíveis apenas via link direto (sem aparecer em listagens)

**Entregáveis:** APIs backend para visualização, sugestões, download, streaming e acesso anônimo.

---

### Fase 06 — Interações Sociais (Likes, Comentários, Inscrições)

> Depende de: Fase 02, Fase 05

APIs backend para likes/dislikes em vídeos e comentários, comentários com respostas e inscrição em canais.

- API de like e dislike em vídeos (usuários autenticados)
- API de comentários em vídeos (usuários autenticados)
- API de respostas a comentários (comentários aninhados)
- API de like e dislike em comentários (usuários autenticados)
- API de inscrição em canais (seguir/deixar de seguir)
- API de listagem de canais seguidos com acesso rápido aos vídeos
- Contagem de inscritos na página do canal
- Contratos de API necessários para a futura interface de comentários, likes e inscrições no frontend Next.js

**Entregáveis:** APIs backend de likes/dislikes, comentários com respostas, inscrição em canais e listagem de canais seguidos.

---

### Fase 07 — Página Inicial, Busca e Finalização

> Depende de: todas as fases anteriores

APIs backend para home page futura, busca, navegação geral e preparação do backend para produção.

- API de listagem de vídeos para home page futura (thumbnail, título, canal, visualizações e tempo de publicação)
- API de filtro de vídeos por categoria
- API de busca por título e canal
- Dados necessários para header/navbar futuro, autenticação do usuário e navegação
- Paginação ou suporte a scroll infinito nas listagens de vídeos
- Contratos preparados para consumo responsivo pelo frontend Next.js
- Testes dos fluxos principais do backend
- Ambiente de produção e deploy do backend

**Entregáveis:** APIs backend para home, busca, navegação, listagens paginadas, testes realizados e backend preparado para produção.

---

## 4. Pontos de Atenção

- **Upload de arquivos grandes:** o upload de até 10GB precisa ser feito de forma que não trave o backend Spring Boot e permita retomar em caso de falha de conexão.
- **Processamento de vídeos:** a extração de informações do vídeo é pesada e deve acontecer em segundo plano, sem bloquear o usuário.
- **URLs únicas:** cada vídeo precisa de uma URL curta e única que nunca conflite com outro vídeo.
- **Armazenamento:** vídeos grandes consomem muito espaço. É importante planejar o crescimento e os custos de armazenamento desde o início.
- **Streaming:** o vídeo deve começar a ser reproduzido sem que o usuário precise baixar o arquivo inteiro.
- **Comentários aninhados:** definir até quantos níveis de resposta serão permitidos para manter a interface organizada no futuro frontend Next.js.
- **Like/dislike anônimo:** como qualquer usuário autenticado pode dar like/dislike, é preciso evitar abusos (ex: múltiplos likes do mesmo usuário).
- **Separação de escopo:** backend deve ser sempre planejado e implementado em Spring Boot; frontend deve ser planejado e implementado separadamente em Next.js.
