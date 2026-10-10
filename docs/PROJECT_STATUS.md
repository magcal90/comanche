# Project Comanche — Development Status

**Status:** Paused — development will resume later.

## Completed

- Multi-module Java 21 Maven project
- Docker infrastructure with Kafka, Mosquitto and PostgreSQL
- MQTT-to-Kafka IoT Gateway
- Operations Service device-event ingestion and persistence
- Apache Pekko Typed kiosk simulator
- Deterministic kiosk operational state machine
- MQTT 3.1.1 QoS 1 communication
- UUIDv7 message identities
- Reliable MQTT publishing, retries and bounded buffering
- Progressive printer, CPU and memory fault simulation
- Unit, actor and MQTT integration tests

## Outstanding

- Fix Operations Service Testcontainers lifecycle defect
- Complete simulator-to-Operations end-to-end testing
- Implement SPEC-007 detection, alerts and incidents
- Implement Spring AI AIOps investigation
- Build React operations dashboard
- Add device commands and remediation

## Known limitations

- Simulator outbound messages are not persisted across process restarts
- MQTT recovery is triggered by publication activity
- Simulator fault control is currently internal
- MQTT Last Will timestamps require end-to-end validation
- The full Maven reactor is currently blocked by an Operations Service integration-test lifecycle defect

## Resuming development

1. Restore Docker Desktop and WSL integration.
2. Run `mvn clean verify`.
3. Fix the Operations Testcontainers lifecycle issue.
4. Run end-to-end kiosk ingestion tests.
5. Proceed with SPEC-007 detection and incident management.

## Next project

AI-powered UAT Testing Agent using React, Spring Boot, MCP and agentic AI.