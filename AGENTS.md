# AGENTS.md

Monorepo for a novel-creation platform: Spring Boot 3.4.5 / Java 17 backend in `lingmo-server/`; the `lingmo-web/` frontend is planned but not present yet. Single Maven module; no CI.

## Commands

Run Maven commands from `lingmo-server/` (the repo root has no `pom.xml`).

- `mvn test` — full suite; currently ~30s and self-contained (JUnit 5 + Mockito, no `@SpringBootTest`, no Postgres/Redis/Kafka). Negative-path tests print WARN/stack traces; trust the Maven summary.
- `mvn -Dtest=CreationPipelineServiceTest test` — one class. Add `#method` for one test.
- `mvn -DskipTests package`; `mvn spring-boot:run` for the app.
- Java 17 + Maven 3.9.x. There is no `mvnw`/`mvnw.cmd` (only `lingmo-server/.mvn/wrapper/maven-wrapper.properties`); use system/IDE Maven.
- `lingmo-server/pom.xml` sets source encoding to UTF-8. Keep it: on Chinese Windows the default GBK breaks compilation of Chinese comments/strings.

## Run / setup

- Defaults in `lingmo-server/src/main/resources/application.yml`: port `8088`, servlet context path `/api`, profile `dev`. `lingmo-server/src/main/resources/application-dev.yml` is gitignored and overrides the defaults; without it the checked-in values are used.
- `spring.ai.openai.*` keys are placeholders (`sk-xxxx`); AI/RAG calls fail until real chat + embedding keys are configured locally.
- Default infrastructure: PostgreSQL `novel_system` at `localhost:5432`; PGVector DB `novel_knowledge_base` at `192.168.220.138:5432`; Redis at `192.168.220.138:6379` (db 15); Kafka at `192.168.220.138:9092`; MinIO at `192.168.220.138:9000`.
- No Flyway/Liquibase. `lingmo-server/sql/` is additive, not a full base schema: it contains character-card, v2 pipeline/world-state, and vector-KB DDL, but not `book_info`, `user_info`, or the other base tables. A fresh checkout needs an existing `novel_system` schema/dump.
- Apply `lingmo-server/sql/character_cards.sql` then `lingmo-server/sql/multi_agent.sql` to `novel_system`; apply `lingmo-server/sql/knowledge_base.psql` to `novel_knowledge_base` (needs pgvector). Pipeline tables depend on `multi_agent.sql`.
- `lingmo-server/sql/multi_agent.sql` is GBK-encoded while the other SQL files are UTF-8; convert it to UTF-8 if psql rejects its Chinese comments.
- Kafka topics (`novel.creation.task`, `novel.chapter.finalized`) are created by `KafkaConfig`; no manual topic setup.

## Architecture

- Package root: `lingmo-server/src/main/java/com/linpj/novel/create`. Two PostgreSQL datasources are bound by `@MapperScan`: `mapper.novel` uses the primary `novel-system`, `mapper.knowledge` uses `novel-knowledge`. Keep new mappers in the correct package and don't add a global `@MapperScan`.
- `pipeline.CreationPipelineService` is the v2 core. `CreationMode` selects topology: FAST = Writer; STANDARD = Writer + Critic rewrite loop; REFINED = Planner + Writer + Critic + Polisher; PRECISE = REFINED + parallel dual draft. `maxRewriteRounds` is clamped to 1–3.
- App entrypoint is `NovelCreationPlatformApplication`; root `Main.java` is not the main class but a `/health` controller.
- Entry points: `CreationPipelineController` under `/api/book/create/ai` (`/pipeline` SSE, `/pipeline/async`, `/pipeline/task/{id}`, `/pipeline/run/{runId}`). `NovelAICreationController` keeps the legacy `/polish`, `/activate`, `/create`, `/continue`, `/dialogue` endpoints backed by `NovelAICreationService`; don't break them when editing the pipeline.
- `agent.runtime.AgentRuntime` is the opt-in L2 bounded step loop. Currently only Critic is wired through `CriticL2Gateway`; Writer/Planner stay L1 even if the autonomy matrix says L2.
- `PipelineFeatures` (`novel.pipeline.features.*`) defaults all five mechanisms to false; `AutonomyProperties` (`novel.pipeline.autonomy.enabled`) defaults false. Request `features` overrides only known keys. Keep flag-off behavior equivalent to the legacy path.
- Safety gates (`InputGuard`, `StreamSafetyGate`, `FinalDraftGate`) and budget-degradation rules are hard-coded, not feature flags; don't bypass them.
- World-state consistency lives in `worldstate/*` ledgers + `rule/*` deterministic checks. Stage artifacts are persisted in `creation_stage_artifact` for resume/debug. Pipeline protocol types belong in `pojo.pipeline`; REST DTOs in `pojo.dto`.
- JSONB fields use `JsonbTypeHandler`: entities need `@TableName(..., autoResultMap = true)` and the field needs `@TableField(typeHandler = JsonbTypeHandler.class)`.

## Traps

- `server.servlet.context-path=/api` plus controllers that already declare `/api/...` doubles the prefix. Effective pipeline URL is `/api/api/book/create/ai/pipeline`; character-card, cost, review-queue, rule-exemption, and the legacy AI controller do the same. Check the controller mapping plus context path before testing with curl.
- Auth is default-deny: `AuthInterceptor` matches `/**`; only paths in `WebMvcConfig.excludePathPatterns` are public (login/register/refresh, public book reads, docs). `/health` is not exempt. Add an exclusion for any new public endpoint.
- Redisson (`RedissonConfig`) explicitly uses Redis DB 0, and the Jedis chat-memory repository is built without a database (default DB 0); only the Spring Data Redis connection behind `RedisTemplate` uses `spring.data.redis.database` (15). Don't look for chat memory or task locks in DB 15.
- Knife4j 4.5.0 is intentionally pinned but binary-incompatible with Boot 3.4.5: `/v3/api-docs` may return 500 (`ControllerAdviceBean` `NoSuchMethodError`). Don't treat it as a regression or blindly upgrade.
- okhttp is pinned to 4.12.0 for MinIO 8.6.0; Boot 3.4.5's BOM would select 5.1.0, which is incompatible. Don't remove the override.
- `lingmo-server/README.md` still lists some API paths (`/ai-chat`, `/knowledge-base`, ...) that don't match controller mappings. Trust `pom.xml`, `application.yml`, and controller mappings.
- `AI_CREATION_OPTIMIZATION.md` is a proposal, not an implemented spec (its `ContentQualityService`, `ChapterSummaryService`, `AiCreationVersion`, etc. do not exist). Use code as the source of truth.
- Design docs referenced from comments under `docs/` are gitignored and absent from a fresh clone.

## Verification

- No lint/formatter/static-analysis config and no CI. `cd lingmo-server && mvn test` is the gate.
- Tests are unit-level; there is no test profile/config for Spring-context tests.
