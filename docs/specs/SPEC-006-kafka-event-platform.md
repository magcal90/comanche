# SPEC-006 — Kafka Event Platform and Integration Contracts

**Project:** Comanche  
**Description:** AI-Assisted AIOps and Operational Visibility Platform for IoT Devices  
**Reference Organization:** Davy Jones Burger  
**Status:** Draft  
**Version:** 0.2  
**Type:** Event-Driven Architecture and Integration Specification  
**Depends On:** SPEC-001 through SPEC-005  
**Related Specifications:** SPEC-007 (Detection and Incidents), SPEC-008 (AI Investigation), SPEC-010 (Remediation)

---

## 1. Purpose

This specification defines Apache Kafka's role as the internal asynchronous event backbone of Project Comanche.

It establishes:

- Kafka responsibilities and architectural boundaries.
- Topic naming and ownership.
- Event envelope and payload contracts.
- Event producers and consumers.
- Partitioning and ordering.
- Delivery guarantees.
- Idempotency and duplicate handling.
- Error handling and retries.
- Event retention.
- Integration with MQTT.
- Incident and command event flows.
- Security, observability, and testing.

Kafka SHALL enable loosely coupled communication between Comanche services without becoming the authoritative operational database.

---

## 2. Architectural Principles

The Kafka integration SHALL follow these principles:

1. MQTT remains the external IoT communication protocol.
2. Kafka remains an internal integration mechanism.
3. The IoT Gateway translates validated MQTT messages into Kafka events.
4. The Operations Service owns operational business state.
5. Kafka events are immutable records of occurrences or requested processing.
6. Consumers SHALL tolerate at-least-once delivery.
7. Device-specific ordering SHALL be preserved where required.
8. Database writes and Kafka publication SHALL account for partial failures.
9. AI investigation SHALL NOT be required for telemetry processing.
10. Kafka SHALL NOT provide a direct path for AI-controlled remediation.

---

## 3. System Architecture

```text
                    IoT DEVICES
                         |
                        MQTT
                         |
                         v
                 +---------------+
                 | MQTT Broker   |
                 | Mosquitto     |
                 +-------+-------+
                         |
                         v
                 +---------------+
                 | IoT Gateway   |
                 | Spring Boot   |
                 +-------+-------+
                         |
                         | Kafka Events
                         v
            +--------------------------+
            |       Apache Kafka       |
            |                          |
            | Device Telemetry         |
            | Device Status            |
            | Device Events            |
            | Command Results          |
            | Alert Events             |
            | Incident Events          |
            +------------+-------------+
                         |
             +-----------+-----------+
             |                       |
             v                       v
    +------------------+     +------------------+
    | Operations       |     | AIOps Service    |
    | Service          |     | Spring AI        |
    |                  |     |                  |
    | Device State     |     | Incident Trigger |
    | Detection        |     | Investigation    |
    | Alerts           |     | Recommendations  |
    | Incidents        |     +------------------+
    | Commands         |
    +--------+---------+
             |
             v
        PostgreSQL
```

The diagram illustrates event relationships. It does not imply that every service consumes every Kafka topic.

---

## 4. Kafka Deployment

### 4.1 Technology

The MVP SHALL use Apache Kafka with a Java/Spring Boot integration.

Spring for Apache Kafka SHOULD be used by Spring Boot services.

### 4.2 Local Deployment

Kafka SHALL be available through Docker Compose.

The preferred local setup is a single-node Kafka broker using KRaft mode.

ZooKeeper SHALL NOT be required for a new MVP deployment.

### 4.3 Scope

The MVP SHALL NOT require:

- Multi-broker high availability.
- Kafka Connect.
- Debezium.
- Kafka Streams.
- Apache Flink.
- A managed cloud Kafka service.
- An external Schema Registry.

These MAY be added only when a demonstrated requirement justifies them.

---

## 5. Topic Naming Convention

Kafka topics SHALL use:

`comanche.<domain>.<event-category>.v<major-version>`

### 5.1 Initial Topic Catalog

| Topic | Producer | Primary Consumer |
|---|---|---|
| `comanche.device.telemetry.v1` | IoT Gateway | Operations Service |
| `comanche.device.status.v1` | IoT Gateway | Operations Service |
| `comanche.device.events.v1` | IoT Gateway | Operations Service |
| `comanche.alert.events.v1` | Operations Service | Optional notification consumers |
| `comanche.incident.events.v1` | Operations Service | AIOps Service |
| `comanche.command.requests.v1` | Operations Service | IoT Gateway |
| `comanche.command.results.v1` | IoT Gateway | Operations Service |

These names are the proposed canonical Kafka topics for the MVP.

### 5.2 Topic Rules

**REQ-KAFKA-001**  
Topic names SHALL be defined centrally in shared configuration or contract definitions.

**REQ-KAFKA-002**  
Topic names SHALL NOT be duplicated as unrelated hard-coded strings throughout services.

**REQ-KAFKA-003**  
A breaking event-contract change SHALL require a new major topic version or an explicitly managed compatibility migration.

**REQ-KAFKA-004**  
IoT devices SHALL NOT publish directly to Kafka.

**REQ-KAFKA-005**  
The AIOps Service SHALL NOT produce command-request events.

---

## 6. Topic Responsibilities

### 6.1 Device Telemetry Topic

`comanche.device.telemetry.v1`

Contains validated telemetry received from devices.

Examples:

- CPU utilization.
- Memory utilization.
- Printer temperature.
- Printer error rate.
- Printer paper level.

The Operations Service SHALL consume this topic to persist telemetry, update current device metrics, and evaluate deterministic detection rules.

### 6.2 Device Status Topic

`comanche.device.status.v1`

Contains device-reported status information.

Examples:

- Application running.
- Application stopped.
- Network connected.
- Printer degraded.

The Operations Service SHALL distinguish device-reported status from its own derived health and connectivity state.

### 6.3 Device Events Topic

`comanche.device.events.v1`

Contains device-generated technical and operational events.

Examples:

- `OPERATIONAL_STATE_CHANGED`
- `APPLICATION_STOPPED`
- `PRINTER_ERROR`
- `PAPER_LOW`
- `ORDER_SESSION_STARTED`

The Operations Service SHALL use these events for device-state tracking, utilization, and incident evidence.

### 6.4 Alert Events Topic

`comanche.alert.events.v1`

Contains alert lifecycle notifications.

Examples:

- `ALERT_CREATED`
- `ALERT_ACKNOWLEDGED`
- `ALERT_RESOLVED`

The Operations Service SHALL remain authoritative for alert state.

### 6.5 Incident Events Topic

`comanche.incident.events.v1`

Contains incident lifecycle notifications.

Examples:

- `INCIDENT_CREATED`
- `INCIDENT_UPDATED`
- `INCIDENT_RESOLVED`
- `INCIDENT_CLOSED`

The AIOps Service MAY consume qualifying incident events to initiate investigations.

### 6.6 Command Requests Topic

`comanche.command.requests.v1`

Contains commands that have already passed deterministic authorization in the Operations Service.

The IoT Gateway SHALL consume this topic and publish permitted commands to MQTT.

This topic SHALL NOT be writable by the AIOps Service.

### 6.7 Command Results Topic

`comanche.command.results.v1`

Contains command acknowledgements and execution outcomes received from devices through MQTT.

The Operations Service SHALL consume this topic to update command lifecycle records.

---

## 7. Event Envelope

All Kafka events SHALL use a versioned JSON envelope for the MVP.

### 7.1 Required Fields

| Field | Type | Description |
|---|---|---|
| `eventId` | String | Unique internal event identifier |
| `eventType` | String | Event classification |
| `schemaVersion` | String | Event contract version |
| `occurredAt` | ISO-8601 String | Original occurrence time |
| `publishedAt` | ISO-8601 String | Event publication time |
| `source` | String | Producing service |
| `aggregateId` | String | Primary entity identifier |
| `correlationId` | String | Related workflow identifier |
| `payload` | Object | Event-specific data |

### 7.2 Optional Fields

- `causationId`
- `deviceId`
- `siteId`
- `sourceMessageId`
- `traceId`
- `deviceSequenceNumber`

### 7.3 Example

```json
{
  "eventId": "evt-kafka-10001",
  "eventType": "DEVICE_TELEMETRY_RECEIVED",
  "schemaVersion": "1.0",
  "occurredAt": "2026-10-08T04:00:00Z",
  "publishedAt": "2026-10-08T04:00:01Z",
  "source": "comanche-iot-gateway",
  "aggregateId": "KIOSK-TAM-003",
  "correlationId": "msg-10001",
  "sourceMessageId": "msg-10001",
  "deviceId": "KIOSK-TAM-003",
  "siteId": "SITE-TAMPINES",
  "payload": {}
}
```

### 7.4 Event Envelope Requirements

**REQ-KAFKA-006**  
Every Kafka event SHALL include a unique eventId.

**REQ-KAFKA-007**  
Every Kafka event SHALL identify its eventType.

**REQ-KAFKA-008**  
Every Kafka event SHALL include schemaVersion.

**REQ-KAFKA-009**  
Every Kafka event SHALL identify the producing service.

**REQ-KAFKA-010**  
Events SHALL preserve original occurrence time when available.

**REQ-KAFKA-011**  
Events SHALL include correlation information sufficient to trace related processing.

---

## 8. MQTT-to-Kafka Mapping

The IoT Gateway SHALL convert validated MQTT messages into Kafka events.

### 8.1 Mapping Table

| MQTT Message | Kafka Event Type |
|---|---|
| `TELEMETRY` | `DEVICE_TELEMETRY_RECEIVED` |
| `STATUS` | `DEVICE_STATUS_RECEIVED` |
| `DEVICE_EVENT` | `DEVICE_EVENT_RECEIVED` |
| `COMMAND_RESULT` | `DEVICE_COMMAND_RESULT_RECEIVED` |

### 8.2 Mapping Requirements

The gateway SHALL preserve:

- Original deviceId.
- Original MQTT messageId.
- Original eventTime.
- Message payload.
- Relevant device sequence information, if available.

The gateway SHALL add:

- Internal eventId.
- Producing service identity.
- Publication timestamp.
- Correlation metadata.

### 8.3 Identity

A retransmitted MQTT message with the same source messageId SHALL represent the same logical device message.

The Operations Service SHALL use stable source identifiers for idempotent processing.

---

## 9. Telemetry Event Contract

### 9.1 Example

```json
{
  "eventId": "evt-tel-10001",
  "eventType": "DEVICE_TELEMETRY_RECEIVED",
  "schemaVersion": "1.0",
  "occurredAt": "2026-10-08T04:00:00Z",
  "publishedAt": "2026-10-08T04:00:01Z",
  "source": "comanche-iot-gateway",
  "aggregateId": "KIOSK-TAM-003",
  "correlationId": "tel-10001",
  "sourceMessageId": "tel-10001",
  "deviceId": "KIOSK-TAM-003",
  "siteId": "SITE-TAMPINES",
  "payload": {
    "metrics": {
      "printer.temperature": 72.0,
      "printer.errorRate": 0.19,
      "printer.paperLevel": 68.0,
      "system.cpu.utilization": 35.0
    }
  }
}
```

### 9.2 Processing

The Operations Service SHALL:

1. Validate the event contract.
2. Check whether the source message was previously processed.
3. Persist accepted telemetry.
4. Update current metrics when the event is sufficiently recent.
5. Evaluate applicable detection rules.
6. Update relevant monitoring projections.
7. Commit processing progress only after the required database operations succeed.

### 9.3 Telemetry Rules

**REQ-KAFKA-012**  
Duplicate telemetry events SHALL NOT create duplicate stored logical measurements.

**REQ-KAFKA-013**  
Stale telemetry SHALL NOT overwrite newer current-state values.

**REQ-KAFKA-014**  
Telemetry processing SHALL NOT require an AI service call.

---

## 10. Device Event Contract

### 10.1 Example

```json
{
  "eventId": "evt-device-20001",
  "eventType": "DEVICE_EVENT_RECEIVED",
  "schemaVersion": "1.0",
  "occurredAt": "2026-10-08T04:00:10Z",
  "publishedAt": "2026-10-08T04:00:11Z",
  "source": "comanche-iot-gateway",
  "aggregateId": "KIOSK-TAM-003",
  "correlationId": "msg-20001",
  "sourceMessageId": "msg-20001",
  "deviceId": "KIOSK-TAM-003",
  "siteId": "SITE-TAMPINES",
  "payload": {
    "eventType": "OPERATIONAL_STATE_CHANGED",
    "previousState": "IDLE",
    "newState": "TAKING_ORDER"
  }
}
```

### 10.2 Processing

Operational-state events SHALL be processed in a manner that supports time-based utilization calculations.

The Operations Service SHALL NOT assume that Kafka arrival time equals the actual state-transition time.

---

## 11. Alert Event Contract

### 11.1 Example

```json
{
  "eventId": "evt-alert-30001",
  "eventType": "ALERT_CREATED",
  "schemaVersion": "1.0",
  "occurredAt": "2026-10-08T04:01:00Z",
  "publishedAt": "2026-10-08T04:01:01Z",
  "source": "comanche-ops-service",
  "aggregateId": "ALERT-30001",
  "correlationId": "INC-40001",
  "deviceId": "KIOSK-TAM-003",
  "siteId": "SITE-TAMPINES",
  "payload": {
    "alertId": "ALERT-30001",
    "ruleId": "PRINTER_HIGH_TEMPERATURE",
    "severity": "HIGH",
    "status": "ACTIVE",
    "observedValue": 72.0,
    "threshold": 70.0
  }
}
```

### 11.2 Authority

The Operations Service SHALL remain authoritative for alert lifecycle state.

Kafka consumers SHALL NOT infer that an alert event grants permission to change a device.

---

## 12. Incident Event Contract

### 12.1 Example

```json
{
  "eventId": "evt-incident-40001",
  "eventType": "INCIDENT_CREATED",
  "schemaVersion": "1.0",
  "occurredAt": "2026-10-08T04:01:05Z",
  "publishedAt": "2026-10-08T04:01:06Z",
  "source": "comanche-ops-service",
  "aggregateId": "INC-40001",
  "correlationId": "INC-40001",
  "deviceId": "KIOSK-TAM-003",
  "siteId": "SITE-TAMPINES",
  "payload": {
    "incidentId": "INC-40001",
    "severity": "HIGH",
    "status": "OPEN",
    "alertIds": [
      "ALERT-30001"
    ],
    "summary": "Printer temperature exceeded configured threshold"
  }
}
```

### 12.2 AI Investigation Trigger

The AIOps Service MAY consume `INCIDENT_CREATED` events.

An investigation SHALL be correlated to the incidentId.

Repeated delivery of the same incident event SHALL NOT create uncontrolled duplicate investigations.

### 12.3 Investigation Failure

Failure to initiate AI investigation SHALL NOT roll back a valid incident.

The incident SHALL remain visible to engineers.

---

## 13. Command Request Contract

Command requests SHALL represent previously authorized actions.

They SHALL NOT represent raw AI recommendations.

### 13.1 Example

```json
{
  "eventId": "evt-command-50001",
  "eventType": "DEVICE_COMMAND_AUTHORIZED",
  "schemaVersion": "1.0",
  "occurredAt": "2026-10-08T04:05:00Z",
  "publishedAt": "2026-10-08T04:05:01Z",
  "source": "comanche-ops-service",
  "aggregateId": "CMD-50001",
  "correlationId": "INC-40001",
  "deviceId": "KIOSK-TAM-003",
  "siteId": "SITE-TAMPINES",
  "payload": {
    "commandId": "CMD-50001",
    "incidentId": "INC-40001",
    "recommendationId": "REC-50001",
    "approvalId": "APR-50001",
    "commandType": "RESTART_PRINTER_SUBSYSTEM",
    "parameters": {},
    "authorizedBy": "engineer-001",
    "authorizedAt": "2026-10-08T04:05:00Z",
    "expiresAt": "2026-10-08T04:06:00Z"
  }
}
```

### 13.2 Producer Restriction

Only the Operations Service SHALL produce command-request events.

The AIOps Service SHALL NOT have Kafka producer permissions for the command-request topic.

### 13.3 Gateway Validation

The IoT Gateway SHALL validate:

- Event schema.
- Producer identity through configured Kafka security controls.
- Command type.
- Target device.
- Required authorization references.
- Command expiry.
- Duplicate commandId.

The presence of an approvalId alone SHALL NOT constitute cryptographic proof of authorization.

The gateway SHALL rely on the trusted Operations Service command boundary and enforced producer permissions.

### 13.4 Publication

After validation, the gateway SHALL translate the command request into the MQTT command contract defined in SPEC-005.

---

## 14. Command Result Contract

### 14.1 Example

```json
{
  "eventId": "evt-command-result-50001",
  "eventType": "DEVICE_COMMAND_RESULT_RECEIVED",
  "schemaVersion": "1.0",
  "occurredAt": "2026-10-08T04:05:08Z",
  "publishedAt": "2026-10-08T04:05:09Z",
  "source": "comanche-iot-gateway",
  "aggregateId": "CMD-50001",
  "correlationId": "INC-40001",
  "sourceMessageId": "cmd-result-50001",
  "deviceId": "KIOSK-TAM-003",
  "siteId": "SITE-TAMPINES",
  "payload": {
    "commandId": "CMD-50001",
    "status": "SUCCEEDED",
    "description": "Printer restart completed"
  }
}
```

### 14.2 Processing

The Operations Service SHALL:

1. Identify the matching command.
2. Validate the reporting device.
3. Deduplicate repeated results.
4. Update command execution status.
5. Preserve command outcome evidence.
6. Initiate or continue recovery verification when appropriate.

A SUCCEEDED result SHALL NOT automatically resolve the incident.

---

## 15. Kafka Partitioning

Kafka partitioning SHALL support ordering of related device events.

### 15.1 Partition Keys

Recommended keys:

| Topic | Partition Key |
|---|---|
| Device telemetry | `deviceId` |
| Device status | `deviceId` |
| Device events | `deviceId` |
| Alert events | `deviceId` |
| Incident events | `incidentId` |
| Command requests | `deviceId` |
| Command results | `deviceId` |

### 15.2 Ordering Guarantees

Kafka provides ordering within a partition of a single topic.

Kafka SHALL NOT be assumed to provide ordering across different topics.

The Operations Service SHALL account for possible arrival differences between telemetry, status, and device events.

### 15.3 Partition Count

The MVP MAY use one partition per topic initially.

Additional partitions MAY be configured for scalability experiments.

Partition counts SHALL be selected with awareness of consumer parallelism and ordering requirements.

---

## 16. Consumer Groups

Each independently deployed service SHALL use its own consumer group.

Recommended group IDs:

```text
comanche-ops-service

comanche-aiops-service

comanche-iot-gateway-commands
```

### 16.1 Consumer Group Rules

**REQ-KAFKA-015**  
Consumers within the same logical service SHALL use a consistent consumer group.

**REQ-KAFKA-016**  
Independent services SHALL NOT share a consumer group when each must receive the same event.

**REQ-KAFKA-017**  
Consumer offsets SHALL be managed so that failed processing can be retried safely.

---

## 17. Delivery Guarantees

The MVP SHALL use at-least-once processing semantics for events requiring reliable handling.

Exactly-once end-to-end processing across MQTT, Kafka, PostgreSQL, and device command execution SHALL NOT be assumed.

### 17.1 Producer Reliability

Kafka producers SHOULD use:

- Acknowledgement configuration appropriate to the deployment.
- Idempotent producer support.
- Bounded retries.
- Delivery timeout configuration.

### 17.2 Consumer Reliability

Consumers SHALL:

- Process records idempotently.
- Avoid acknowledging successful processing before required persistence succeeds.
- Handle retryable failures.
- Distinguish malformed events from transient failures.

### 17.3 Transaction Boundaries

Kafka offset commits and PostgreSQL transactions are separate unless an explicit coordination mechanism is implemented.

The MVP SHALL rely on idempotent processing rather than assuming atomic transactions across Kafka and PostgreSQL.

---

## 18. Idempotency

Idempotency SHALL be enforced at business-processing boundaries.

### 18.1 Device Telemetry

The logical identity SHOULD use:

`deviceId + sourceMessageId`

A database uniqueness constraint SHOULD prevent duplicate accepted telemetry records.

### 18.2 Device Events

The logical identity SHOULD use:

`deviceId + sourceMessageId`

Duplicate events SHALL NOT create repeated state transitions.

### 18.3 Alerts

Alert deduplication SHALL use the detection engine's logical alert identity.

The same abnormal condition SHALL NOT create a new alert on every reading.

### 18.4 Incidents

Repeated alert or incident events SHALL NOT create duplicate incidents or investigations.

### 18.5 Commands

A commandId SHALL identify one logical command.

Retries SHALL preserve commandId.

A new commandId SHALL represent a new authorized action.

---

## 19. Database and Event Consistency

The Operations Service SHALL persist operational state in PostgreSQL.

Publishing an event after a database change creates a potential dual-write failure.

### 19.1 Required Strategy

The MVP SHOULD implement a transactional outbox for operational events that must be published reliably after database commits.

Examples:

- Alert created.
- Incident created.
- Command authorized.

### 19.2 Transactional Outbox

```text
Operations Service
       |
       | Database transaction
       v
+------------------------+
| PostgreSQL             |
|                        |
| Update Incident        |
| Insert Outbox Event    |
+------------------------+
       |
       | Commit
       v
Outbox Publisher
       |
       v
Kafka
```

### 19.3 Outbox Publication

The outbox publisher SHALL:

- Select unpublished events.
- Publish them to Kafka.
- Mark them as published after broker acknowledgement.
- Retry failed publications.
- Preserve stable eventId values across retries.

### 19.4 Outbox Scope

The MVP MAY implement an application-level scheduled outbox publisher.

Debezium or Kafka Connect is not required.

### 19.5 Duplicate Publication

An outbox event MAY be published more than once if failure occurs after Kafka acknowledgement but before the database publication marker is updated.

Consumers SHALL therefore remain idempotent.

---

## 20. Command Delivery Reliability

Command delivery requires stricter controls than ordinary telemetry.

### 20.1 Authorization Persistence

The Operations Service SHALL persist the approved command and its authorization before publishing a command request.

### 20.2 Gateway Consumption

The gateway SHALL consume the authorized command request and validate it.

### 20.3 MQTT Publication

The gateway SHALL publish the command to the device-specific MQTT topic.

### 20.4 Retry Semantics

Retries SHALL preserve commandId.

The gateway SHALL NOT create a new logical command when retrying publication.

### 20.5 Expiry

Expired commands SHALL NOT be dispatched.

The device SHALL also reject expired commands according to SPEC-005.

### 20.6 Execution Result

The device SHALL return command outcomes through MQTT.

The gateway SHALL forward outcomes to Kafka.

### 20.7 Recovery

The Operations Service SHALL verify device recovery independently from command publication and acknowledgement.

---

## 21. Retry and Dead-Letter Handling

The system SHALL distinguish transient processing failures from permanently invalid events.

### 21.1 Retryable Failures

Examples:

- Temporary PostgreSQL outage.
- Temporary downstream service unavailability.
- Transient processing exception.

These MAY be retried with bounded backoff.

### 21.2 Non-Retryable Failures

Examples:

- Unsupported schema version.
- Invalid required fields.
- Unsupported event type.
- Malformed payload.

These SHALL NOT be retried indefinitely.

### 21.3 Dead-Letter Topics

Candidate dead-letter topics:

```text
comanche.device.telemetry.dlt.v1

comanche.device.events.dlt.v1

comanche.incident.events.dlt.v1

comanche.command.requests.dlt.v1
```

Dead-letter events SHOULD preserve:

- Original topic.
- Original partition.
- Original offset.
- Original event payload.
- Error classification.
- Failure timestamp.

### 21.4 Command Failures

A command request sent to a dead-letter topic SHALL also result in a visible command failure or dispatch-exception state in the Operations Service.

The system SHALL NOT silently lose authorized commands.

---

## 22. Event Retention

Kafka SHALL use bounded retention policies.

### 22.1 Candidate Defaults

| Event Category | Candidate Retention |
|---|---|
| Telemetry | 24 hours |
| Device status | 3 days |
| Device events | 7 days |
| Alert events | 7 days |
| Incident events | 7 days |
| Command requests | 7 days |
| Command results | 7 days |
| Dead-letter events | 7 days |

These are proposed development defaults and SHALL be configurable.

### 22.2 PostgreSQL Persistence

PostgreSQL SHALL store operational records needed beyond Kafka retention.

Kafka SHALL NOT be treated as the sole historical source for incidents or management reporting.

### 22.3 Retention Requirements

**REQ-KAFKA-018**  
Kafka retention SHALL be configurable.

**REQ-KAFKA-019**  
Kafka SHALL NOT require indefinite telemetry retention.

**REQ-KAFKA-020**  
Operational records required for incident investigation SHALL be persisted according to the Operations Service data policy.

---

## 23. Schema Evolution

Event contracts SHALL use explicit schema versions.

### 23.1 Compatible Changes

Compatible changes MAY include:

- Adding optional fields.
- Adding new event types handled safely by consumers.
- Adding optional metadata.

### 23.2 Breaking Changes

Breaking changes MAY include:

- Removing required fields.
- Changing field types.
- Changing event semantics.
- Renaming required fields without compatibility support.

Breaking changes SHALL require explicit migration planning.

### 23.3 Unknown Fields

Consumers SHOULD tolerate unknown optional fields.

Consumers SHALL reject unsupported major schema versions.

---

## 24. Security

Kafka access SHALL follow least-privilege principles.

### 24.1 Producer Permissions

The IoT Gateway SHOULD be authorized to produce:

- Device telemetry.
- Device status.
- Device events.
- Command results.

The Operations Service SHOULD be authorized to produce:

- Alert events.
- Incident events.
- Authorized command requests.

### 24.2 Consumer Permissions

The Operations Service SHOULD consume:

- Device telemetry.
- Device status.
- Device events.
- Command results.

The AIOps Service SHOULD consume:

- Incident events.

The IoT Gateway SHOULD consume:

- Authorized command requests.

### 24.3 AI Restrictions

**REQ-KAFKA-021**  
The AIOps Service SHALL NOT have producer access to command-request topics.

**REQ-KAFKA-022**  
The AIOps Service SHALL NOT possess MQTT command-publishing credentials.

**REQ-KAFKA-023**  
Kafka topic permissions SHALL NOT be treated as a substitute for application-level command authorization.

### 24.4 Local Development

A single-node local Kafka setup MAY initially run without authentication in an isolated development environment.

Security acceptance tests SHALL verify the intended producer/consumer restrictions using an appropriately secured configuration.

---

## 25. Observability

Kafka integration SHOULD expose:

- Producer publication counts.
- Producer failure counts.
- Consumer lag.
- Consumer processing latency.
- Consumer retry counts.
- Dead-letter counts.
- Outbox backlog.
- Outbox publication failures.
- Command dispatch delays.

### 25.1 Correlation

Logs SHOULD include:

```text
eventId
eventType
deviceId
incidentId
commandId
correlationId
topic
partition
offset
```

Only applicable fields need to be present.

### 25.2 Monitoring

Spring Boot Actuator SHOULD expose service health.

Kafka connectivity and consumer health SHOULD be included where practical.

---

## 26. Failure Scenarios

### 26.1 Kafka Unavailable During MQTT Ingestion

The gateway SHALL NOT claim successful Kafka publication when publication fails.

A bounded retry or buffering policy SHALL be applied.

For QoS 0 telemetry, loss MAY be acceptable if explicitly documented.

For important device events and command results, stronger delivery handling SHOULD be used.

### 26.2 Operations Service Unavailable

Kafka SHALL retain events within the configured retention period.

When the service recovers, its consumer group SHOULD resume from committed offsets.

### 26.3 Duplicate Kafka Delivery

The Operations Service SHALL process duplicate records idempotently.

### 26.4 Database Failure

Required database operations SHALL fail without committing the associated Kafka processing offset as successful.

### 26.5 Outbox Publisher Failure

Committed outbox events SHALL remain available for later publication.

### 26.6 AIOps Service Unavailable

Incident events SHALL remain available for retry within retention limits.

Core monitoring SHALL continue.

### 26.7 Gateway Failure During Command Dispatch

The command SHALL retain its stable commandId.

The recovered gateway SHALL avoid creating a second logical command.

The device SHALL use commandId deduplication to reduce repeated execution risk.

---

## 27. Testing Strategy

### 27.1 Contract Tests

Tests SHALL verify:

- Required event envelope fields.
- Schema version.
- Event type.
- Device identity.
- Timestamp formats.
- Payload validation.
- Unknown-field compatibility.
- Unsupported-version rejection.

### 27.2 Producer Tests

Tests SHOULD verify:

- Correct topic selection.
- Correct partition key.
- Correct serialization.
- Stable event identity on retries.
- Publication error handling.

### 27.3 Consumer Tests

Tests SHALL verify:

- Successful event processing.
- Duplicate delivery.
- Invalid event handling.
- Retryable database failure.
- Non-retryable schema failure.
- Offset management.

### 27.4 Outbox Tests

Tests SHALL verify:

- Business record and outbox event commit together.
- Failed Kafka publication leaves the outbox event pending.
- Successful publication marks the event published.
- Retrying publication preserves eventId.
- Duplicate publication does not cause duplicate business effects.

### 27.5 Command Tests

Tests SHALL verify:

- Only authorized command records produce command requests.
- Gateway receives authorized command events.
- Duplicate command requests preserve commandId.
- Expired commands are not dispatched.
- Command results update the correct command.
- Command acknowledgement does not automatically resolve an incident.

### 27.6 Integration Tests

Integration tests SHOULD use an actual Kafka broker, preferably through Testcontainers or Docker Compose.

Mock-only tests SHALL NOT be considered sufficient to validate Kafka integration.

---

## 28. Requirements Summary

**REQ-KAFKA-024**  
The IoT Gateway SHALL publish validated MQTT telemetry to Kafka.

**REQ-KAFKA-025**  
The Operations Service SHALL consume device telemetry and events.

**REQ-KAFKA-026**  
Kafka consumers SHALL tolerate duplicate delivery.

**REQ-KAFKA-027**  
Device events requiring ordering SHALL use consistent partition keys.

**REQ-KAFKA-028**  
Consumers SHALL NOT assume ordering across separate topics.

**REQ-KAFKA-029**  
Operational domain state SHALL remain owned by the Operations Service.

**REQ-KAFKA-030**  
Incident creation SHALL remain independent of AI availability.

**REQ-KAFKA-031**  
AI investigation triggers SHALL be idempotent.

**REQ-KAFKA-032**  
Command requests SHALL originate only from authorized Operations Service workflows.

**REQ-KAFKA-033**  
Command dispatch SHALL preserve commandId across retries.

**REQ-KAFKA-034**  
Command execution outcomes SHALL return through the gateway.

**REQ-KAFKA-035**  
Command acknowledgement SHALL NOT imply verified recovery.

**REQ-KAFKA-036**  
Critical operational events SHALL use a reliable publication strategy.

**REQ-KAFKA-037**  
Kafka event retention SHALL be bounded and configurable.

**REQ-KAFKA-038**  
Failed events SHALL not be retried indefinitely without classification.

**REQ-KAFKA-039**  
Kafka event contracts SHALL be versioned.

**REQ-KAFKA-040**  
The MVP SHALL run using a local single-node Kafka deployment.

---

## 29. Acceptance Criteria

**AC-006-01**  
The IoT Gateway publishes a validated MQTT telemetry message to `comanche.device.telemetry.v1`.

**AC-006-02**  
The Kafka event preserves the original deviceId, messageId, and eventTime.

**AC-006-03**  
The Operations Service consumes telemetry and persists the accepted measurement.

**AC-006-04**  
Duplicate telemetry delivery does not create duplicate logical measurements.

**AC-006-05**  
Stale telemetry does not overwrite newer current device metrics.

**AC-006-06**  
Device operational events can be consumed and used to update operational-state history.

**AC-006-07**  
A deterministic alert can produce an alert event.

**AC-006-08**  
A newly created incident produces an incident event.

**AC-006-09**  
The AIOps Service can consume an incident event and initiate an investigation.

**AC-006-10**  
Duplicate incident events do not produce uncontrolled duplicate investigations.

**AC-006-11**  
An AI outage does not interrupt device telemetry processing or incident creation.

**AC-006-12**  
An authorized command record produces a command-request event.

**AC-006-13**  
The IoT Gateway consumes the command request and publishes the corresponding MQTT command.

**AC-006-14**  
The AIOps Service cannot produce command-request events under the secured configuration.

**AC-006-15**  
The gateway preserves commandId when retrying command dispatch.

**AC-006-16**  
A command result received through MQTT is forwarded to Kafka.

**AC-006-17**  
The Operations Service updates the corresponding command lifecycle record.

**AC-006-18**  
A successful command result does not automatically resolve the incident.

**AC-006-19**  
A database transaction can persist an incident and its outbox event atomically.

**AC-006-20**  
An outbox publisher can recover from a temporary Kafka outage without losing committed events.

**AC-006-21**  
Malformed events are rejected or routed to a dead-letter mechanism.

**AC-006-22**  
Consumer processing resumes after temporary service unavailability.

**AC-006-23**  
Kafka integration tests execute against a real broker.

**AC-006-24**  
The Kafka platform runs locally without mandatory paid cloud services.

---

## 30. Implementation Milestones

### Milestone 1 — Kafka Foundation

Implement:

- Kafka Docker Compose configuration.
- Spring Kafka dependencies.
- Topic configuration.
- Event envelope.
- JSON serialization/deserialization.
- Producer and consumer configuration.

**Deliverable:** Backend services can publish and consume test events.

### Milestone 2 — Device Ingestion

Implement:

- MQTT-to-Kafka telemetry mapping.
- Device status mapping.
- Device event mapping.
- Operations Service consumers.
- Idempotent telemetry persistence.

**Deliverable:** Simulated device telemetry reaches PostgreSQL through Kafka.

### Milestone 3 — Alerts and Incidents

Implement:

- Alert events.
- Incident events.
- Incident-triggered AI investigation.
- Duplicate-safe investigation triggers.
- Transactional outbox for reliable operational events.

**Deliverable:** Deterministic incidents can trigger asynchronous AI investigation.

### Milestone 4 — Authorized Commands

Implement:

- Authorized command request event.
- Gateway command consumer.
- MQTT command publication.
- Command result event.
- Operations Service command result consumer.
- Retry and idempotency handling.

**Deliverable:** An engineer-approved command reaches a device and its result returns through Kafka.

### Milestone 5 — Reliability and Security

Implement:

- Retry policies.
- Dead-letter handling.
- Outbox recovery.
- Consumer lag metrics.
- Secured topic permissions for acceptance testing.
- Failure-injection integration tests.

**Deliverable:** Kafka integration behaves predictably under duplicate delivery and temporary failures.

---

## 31. Open Design Decisions

The following details SHALL be finalized during implementation planning:

1. Exact Kafka and Spring Kafka versions.
2. Topic partition counts.
3. Replication factor for local versus future deployments.
4. Producer acknowledgement and retry configuration.
5. Consumer offset commit strategy.
6. JSON serializer and schema validation library.
7. Dead-letter topic naming and retention.
8. Retry backoff and maximum attempts.
9. Transactional outbox polling frequency.
10. Outbox concurrency and locking strategy.
11. Gateway buffering behaviour when Kafka is unavailable.
12. Kafka authentication for secured integration tests.
13. Event contract packaging and shared library boundaries.
14. Handling of delayed or out-of-order operational-state events.
15. Command dispatch timeout and reconciliation strategy.

---

## 32. Final Event-Driven Architecture Principle

Kafka SHALL decouple internal Comanche services while preserving clear domain ownership.

The IoT Gateway SHALL translate validated MQTT messages into internal events.

The Operations Service SHALL remain responsible for deterministic monitoring, operational state, incidents, and command authorization.

The AIOps Service SHALL consume incident information for investigation but SHALL NOT gain device-control authority through Kafka.

**Architectural statement:**

> MQTT connects Comanche to devices. Kafka connects Comanche's backend components. PostgreSQL preserves authoritative operational state. Deterministic services authorize and execute remediation workflows. AI investigates and recommends without controlling devices.
