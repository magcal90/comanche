## External Spring Boot Skills

Use the Spring Boot 4 skills adapted from:
https://github.com/rrezartprebreza/spring-boot-skills

Available skills under `.agents/skills/` include:

- `comanche-spec-implementation`: specification-driven development workflow
- `multi-module-maven`: Maven parent and module conventions
- `configuration-properties`: typed configuration and externalized credentials
- `event-driven-messaging`: Kafka event contracts, producer/consumer reliability, retries and idempotency
- `testing-pyramid`: unit, slice and integration testing conventions

Read the relevant `SKILL.md` and supporting references before implementing affected functionality.
Use only the Spring Boot 4 variants of external skills.
Read relevant SKILL.md files and supporting examples,
but treat their guidance as subordinate to Comanche specifications.

### Precedence

When instructions conflict, use this order:

1. Explicit task instructions
2. Approved Project Comanche specifications under `docs/specs/`
3. Project Comanche architecture and engineering conventions in `AGENTS.md`
4. External Spring Boot skills and examples

Never silently change Comanche event schemas, topic names, module boundaries, or functional requirements to match a generic example.

### Spring Boot 4 Compatibility

- Target Java 21 and Spring Boot 4.1.1.
- Inspect the existing Maven dependency management before introducing dependencies.
- Prefer Spring Boot 4-compatible starters and testing patterns.
- Use Jakarta namespaces where applicable.
- Prefer `@MockitoBean` for Spring-managed test mocks.
- Do not assume Spring Boot 3 configuration or dependency conventions apply unchanged.

### MQTT-to-Kafka Gateway

For gateway development:

- Follow the MQTT topic and Kafka event contracts in the specifications.
- Read `event-driven-messaging`, `configuration-properties`, and `testing-pyramid`.
- Preserve telemetry metadata and device identity.
- Use explicit validation and error handling.
- Avoid unbounded retries and accidental message-loss paths.
- Verify behavior against the existing Mosquitto and Kafka infrastructure.
- Do not introduce a database outbox into the gateway unless the architecture and requirements justify it.

### Completion Requirements

For every specification implementation:

- Identify the applicable requirements and acceptance criteria.
- Implement only the requested milestone.
- Add appropriate automated tests.
- Run `mvn clean verify`.
- Report integration tests separately from unit tests.
- Document any requirements not yet verified.
- Do not commit automatically.

## Project Architecture

- `comanche-iot-gateway`: MQTT ingestion, validation and Kafka publishing.
- `comanche-operations-service`: device registry, telemetry processing,
  incidents, alerts, PostgreSQL persistence and REST APIs.
- `comanche-aiops-service`: AI-assisted incident investigation and recommendations.

Technology stack:
- Java 21, Spring Boot 4.1.1, Maven multi-module
- Eclipse Mosquitto, Apache Kafka, PostgreSQL
- Spring AI for AIOps
- Apache Pekko Typed for device simulation
- React and TypeScript for frontend development

Preserve module boundaries and avoid introducing unnecessary dependencies.

## Development Workflow

1. Read relevant specifications.
2. Inspect existing code and configuration.
3. Load applicable skills.
4. Present an implementation plan before substantial changes.
5. Implement only the approved scope.
6. Add automated tests.
7. Run applicable Maven verification.
8. Report changes, test results and outstanding issues.
9. Suggest a Git commit message.

Do not automatically commit or push changes.
Do not modify unrelated modules.
Never commit credentials or secrets.

