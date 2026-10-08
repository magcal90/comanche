# SPEC-005 — IoT MQTT Communication

**Project:** Comanche  
**Description:** AI-Assisted AIOps and Operational Visibility Platform for IoT Devices  
**Reference Organization:** Davy Jones Burger  
**Status:** Draft  
**Version:** 0.2  
**Type:** Interface and Communication Specification  
**Depends On:** SPEC-001, SPEC-002, SPEC-003, SPEC-004  
**Related Specifications:** SPEC-006 (Kafka), SPEC-007 (Detection and Incidents), SPEC-010 (Remediation)

---

## 1. Purpose

This specification defines the MQTT communication interface between IoT devices and Project Comanche.

It establishes:

- MQTT broker responsibilities.
- Topic naming conventions.
- Device identity and authentication.
- Telemetry and event message contracts.
- Device status and operational-state reporting.
- Command delivery and acknowledgements.
- Quality of Service (QoS).
- Retained-message policies.
- Reconnection and offline behaviour.
- Duplicate and out-of-order message handling.
- Security and authorization boundaries.
- Integration testing and acceptance criteria.

The interface SHALL support both Apache Pekko-simulated devices and future physical IoT devices.

---

## 2. Scope

MQTT SHALL be used exclusively for device-facing communication.

The primary participants are:

1. **IoT Device** — A self-ordering kiosk or POS terminal.
2. **MQTT Broker** — Eclipse Mosquitto.
3. **IoT Gateway** — A Spring Boot service responsible for MQTT integration.
4. **Operations Service** — The operational domain service that processes device events and authorizes commands.

Apache Kafka SHALL remain the internal asynchronous event backbone.

The AIOps Service SHALL NOT communicate directly with devices through MQTT.

---

## 3. Communication Architecture

```text
+---------------------------------------+
|           IoT Device Estate           |
|                                       |
|  Kiosk-001   Kiosk-002   POS-001       |
|      |           |         |          |
+------+-----------+---------+----------+
       |           |         |
       +-----------+---------+
                   |
                   | MQTT
                   v
         +-------------------+
         | Eclipse Mosquitto |
         | MQTT Broker       |
         +---------+---------+
                   |
                   | MQTT
                   v
         +-------------------+
         | IoT Gateway       |
         | Spring Boot       |
         +---------+---------+
                   |
                   | Kafka
                   v
         +-------------------+
         | Operations Service|
         |                   |
         | Device State      |
         | Detection Engine  |
         | Alerts/Incidents  |
         | Command Approval  |
         +-------------------+
```

The IoT Gateway SHALL own both inbound MQTT message processing and outbound authorized command publication.

---

## 4. MQTT Protocol Version

The MVP SHALL use MQTT 3.1.1 for broad client compatibility.

MQTT 5.0 MAY be introduced later.

The simulator and gateway SHALL use compatible protocol versions.

The implementation SHALL NOT depend on MQTT 5.0-only features for MVP functionality.

---

## 5. MQTT Broker

Eclipse Mosquitto SHALL be the reference MQTT broker.

### 5.1 Responsibilities

The broker SHALL:

- Authenticate MQTT clients.
- Enforce topic access permissions.
- Route device telemetry to subscribers.
- Route device events and status.
- Deliver authorized commands.
- Deliver command acknowledgements.
- Support client reconnection.

### 5.2 Deployment

The broker SHALL run locally through Docker Compose.

Development MAY use an isolated local broker configuration.

Production-like testing SHOULD enable authentication and topic-level access controls.

Anonymous access SHALL NOT be used in security acceptance tests.

---

## 6. Topic Namespace

All MQTT topics SHALL use the following root:

`comanche/devices`

Device-specific topics SHALL include the unique `deviceId`.

### 6.1 Topic Definitions

| Topic | Publisher | Subscriber | Purpose |
|---|---|---|---|
| `comanche/devices/{deviceId}/telemetry` | Device | Gateway | Device measurements |
| `comanche/devices/{deviceId}/status` | Device | Gateway | Connectivity and reported status |
| `comanche/devices/{deviceId}/events` | Device | Gateway | Device and operational events |
| `comanche/devices/{deviceId}/commands` | Gateway | Device | Authorized commands |
| `comanche/devices/{deviceId}/command-results` | Device | Gateway | Command acknowledgements and outcomes |

### 6.2 Topic Rules

**REQ-MQTT-001**  
Every device-facing topic SHALL contain a deviceId.

**REQ-MQTT-002**  
Device IDs SHALL be stable and unique within the deployment.

**REQ-MQTT-003**  
Devices SHALL publish only to their authorized topics.

**REQ-MQTT-004**  
Devices SHALL subscribe only to their authorized command topics.

**REQ-MQTT-005**  
The AIOps Service SHALL NOT publish or subscribe to device command topics.

**REQ-MQTT-006**  
Topic names SHALL NOT contain sensitive credentials or personal information.

---

## 7. MQTT Client Identity

Each physical or simulated device SHALL have a unique MQTT client identity.

### 7.1 Device Client ID

Recommended format:

`comanche-device-{deviceId}`

Example:

`comanche-device-KIOSK-TAM-003`

### 7.2 Gateway Client ID

Recommended format:

`comanche-iot-gateway-{instanceId}`

The instance identifier SHALL be unique for simultaneously connected gateway instances.

### 7.3 Authentication

Device clients SHALL authenticate using configured credentials.

For the MVP, username/password authentication MAY be used.

Credentials SHALL NOT be embedded in source code.

TLS SHOULD be enabled for communication outside a trusted local development environment.

### 7.4 Identity Validation

The IoT Gateway SHALL validate that the device identity declared in a message is consistent with the device identity in the MQTT topic.

Broker ACLs SHALL restrict which device topics each client can publish to.

---

## 8. Common Message Envelope

All application messages SHALL use JSON for the MVP.

Each message SHALL contain a versioned envelope.

### 8.1 Required Fields

| Field | Type | Description |
|---|---|---|
| `schemaVersion` | String | Message contract version |
| `messageId` | String | Unique message identifier |
| `deviceId` | String | Device identity |
| `eventTime` | ISO-8601 String | Time the event occurred |
| `messageType` | String | Message category |
| `payload` | Object | Message-specific content |

### 8.2 Example Envelope

```json
{
  "schemaVersion": "1.0",
  "messageId": "msg-001",
  "deviceId": "KIOSK-TAM-003",
  "eventTime": "2026-10-08T04:00:00Z",
  "messageType": "TELEMETRY",
  "payload": {}
}
```

### 8.3 Envelope Requirements

**REQ-MQTT-007**  
Every application message SHALL include a schemaVersion.

**REQ-MQTT-008**  
Every application message SHALL include a unique messageId.

**REQ-MQTT-009**  
Every application message SHALL include deviceId.

**REQ-MQTT-010**  
Every application message SHALL include eventTime.

**REQ-MQTT-011**  
The gateway SHALL reject unsupported schema versions.

**REQ-MQTT-012**  
The gateway SHALL reject malformed JSON or missing required fields.

---

## 9. Telemetry Messages

Telemetry messages SHALL report device measurements.

### 9.1 Topic

`comanche/devices/{deviceId}/telemetry`

### 9.2 Example

```json
{
  "schemaVersion": "1.0",
  "messageId": "tel-10001",
  "deviceId": "KIOSK-TAM-003",
  "eventTime": "2026-10-08T04:00:00Z",
  "messageType": "TELEMETRY",
  "payload": {
    "metrics": {
      "system.cpu.utilization": 35.0,
      "system.memory.utilization": 42.0,
      "printer.temperature": 72.0,
      "printer.errorRate": 0.19,
      "printer.paperLevel": 68.0
    }
  }
}
```

### 9.3 Metric Units

Metric units SHALL follow SPEC-003.

For example:

- `printer.temperature`: degrees Celsius.
- `printer.errorRate`: ratio from 0.0 to 1.0.
- `printer.paperLevel`: percentage from 0 to 100.
- `system.cpu.utilization`: percentage from 0 to 100.
- `system.memory.utilization`: percentage from 0 to 100.

### 9.4 Telemetry Requirements

**REQ-MQTT-013**  
Telemetry SHALL use numeric values for numeric metrics.

**REQ-MQTT-014**  
Telemetry SHALL NOT contain arbitrary executable content.

**REQ-MQTT-015**  
Telemetry publication frequency SHALL be configurable.

**REQ-MQTT-016**  
Telemetry messages SHALL NOT be retained by the broker.

**REQ-MQTT-017**  
The gateway SHALL validate metric names and value ranges according to supported contracts.

---

## 10. Device Status Messages

Status messages SHALL describe the device's reported connectivity and application condition.

### 10.1 Topic

`comanche/devices/{deviceId}/status`

### 10.2 Example

```json
{
  "schemaVersion": "1.0",
  "messageId": "status-10001",
  "deviceId": "KIOSK-TAM-003",
  "eventTime": "2026-10-08T04:00:00Z",
  "messageType": "STATUS",
  "payload": {
    "connectionStatus": "ONLINE",
    "applicationStatus": "RUNNING",
    "printerStatus": "DEGRADED"
  }
}
```

### 10.3 Status Authority

Reported status is an observation.

The Operations Service SHALL independently derive effective HealthState and ConnectivityState.

A device-reported ONLINE status SHALL NOT override a connectivity timeout.

---

## 11. Operational Events

Operational events SHALL represent customer-facing device activity.

### 11.1 Topic

`comanche/devices/{deviceId}/events`

### 11.2 Example

```json
{
  "schemaVersion": "1.0",
  "messageId": "evt-20001",
  "deviceId": "KIOSK-TAM-003",
  "eventTime": "2026-10-08T04:00:00Z",
  "messageType": "DEVICE_EVENT",
  "payload": {
    "eventType": "OPERATIONAL_STATE_CHANGED",
    "previousState": "IDLE",
    "newState": "TAKING_ORDER"
  }
}
```

### 11.3 Operational State Events

Devices SHALL publish operational-state changes needed for utilization calculation.

The Operations Service SHALL calculate historical utilization using accepted state transitions.

### 11.4 Event Requirements

**REQ-MQTT-018**  
Operational-state changes SHALL include the new state.

**REQ-MQTT-019**  
Events SHALL include eventTime.

**REQ-MQTT-020**  
Duplicate operational events SHALL NOT create duplicate logical transitions.

**REQ-MQTT-021**  
Stale events SHALL NOT incorrectly overwrite newer accepted state.

---

## 12. Technical Device Events

Technical events SHALL use the same device events topic.

Candidate event types:

- `APPLICATION_STARTED`
- `APPLICATION_STOPPED`
- `PRINTER_ERROR`
- `PRINTER_RECOVERED`
- `PAPER_LOW`
- `PAPER_EMPTY`
- `NETWORK_DISCONNECTED`
- `NETWORK_RESTORED`
- `PAYMENT_TERMINAL_UNAVAILABLE`

### 12.1 Example

```json
{
  "schemaVersion": "1.0",
  "messageId": "evt-20002",
  "deviceId": "KIOSK-TAM-003",
  "eventTime": "2026-10-08T04:01:00Z",
  "messageType": "DEVICE_EVENT",
  "payload": {
    "eventType": "PRINTER_ERROR",
    "errorCode": "PRINTER_THERMAL_WARNING",
    "description": "Printer temperature elevated"
  }
}
```

Technical events SHALL provide evidence to the Operations Service.

The simulator SHALL NOT determine whether Comanche should create an alert or incident.

---

## 13. Command Messages

Commands SHALL be published only after deterministic application authorization.

### 13.1 Topic

`comanche/devices/{deviceId}/commands`

### 13.2 Example

```json
{
  "schemaVersion": "1.0",
  "messageId": "cmd-msg-10001",
  "deviceId": "KIOSK-TAM-003",
  "eventTime": "2026-10-08T04:05:00Z",
  "messageType": "DEVICE_COMMAND",
  "payload": {
    "commandId": "CMD-10001",
    "commandType": "RESTART_PRINTER_SUBSYSTEM",
    "parameters": {},
    "expiresAt": "2026-10-08T04:06:00Z"
  }
}
```

### 13.3 Supported Commands

The MVP SHOULD support:

- `RESTART_PRINTER_SUBSYSTEM`
- `RESTART_APPLICATION`

The command allowlist SHALL be defined and enforced by deterministic application code.

### 13.4 Command Requirements

**REQ-MQTT-022**  
Only the IoT Gateway SHALL publish device commands.

**REQ-MQTT-023**  
Every command SHALL contain a unique commandId.

**REQ-MQTT-024**  
Every command SHALL identify a supported commandType.

**REQ-MQTT-025**  
Every command SHALL identify the target device.

**REQ-MQTT-026**  
Commands SHALL include an expiry time or equivalent validity constraint.

**REQ-MQTT-027**  
Expired commands SHALL NOT execute.

**REQ-MQTT-028**  
Commands SHALL NOT be retained by the broker.

**REQ-MQTT-029**  
The gateway SHALL reject unauthorized or unsupported command requests.

**REQ-MQTT-030**  
The AIOps Service SHALL NOT publish device commands.

---

## 14. Command Results

Devices SHALL publish command acknowledgements and outcomes.

### 14.1 Topic

`comanche/devices/{deviceId}/command-results`

### 14.2 Example — Accepted

```json
{
  "schemaVersion": "1.0",
  "messageId": "cmd-result-10001",
  "deviceId": "KIOSK-TAM-003",
  "eventTime": "2026-10-08T04:05:01Z",
  "messageType": "COMMAND_RESULT",
  "payload": {
    "commandId": "CMD-10001",
    "status": "ACCEPTED",
    "description": "Printer restart command accepted"
  }
}
```

### 14.3 Example — Completed

```json
{
  "schemaVersion": "1.0",
  "messageId": "cmd-result-10002",
  "deviceId": "KIOSK-TAM-003",
  "eventTime": "2026-10-08T04:05:08Z",
  "messageType": "COMMAND_RESULT",
  "payload": {
    "commandId": "CMD-10001",
    "status": "SUCCEEDED",
    "description": "Printer restart completed"
  }
}
```

### 14.4 Command Result Status

Candidate values:

- `ACCEPTED`
- `REJECTED`
- `SUCCEEDED`
- `FAILED`

### 14.5 Command Result Rules

**REQ-MQTT-031**  
Every command result SHALL reference commandId.

**REQ-MQTT-032**  
Every command result SHALL identify the reporting device.

**REQ-MQTT-033**  
The gateway SHALL forward command results to the Operations Service.

**REQ-MQTT-034**  
A SUCCEEDED command result SHALL NOT automatically resolve an incident.

**REQ-MQTT-035**  
Recovery SHALL be determined from subsequent telemetry or trusted device-state observations.

---

## 15. Quality of Service

The MVP SHALL use explicit QoS policies.

| Message Category | Recommended QoS | Retained |
|---|---:|---|
| Telemetry | 0 | No |
| Device status | 1 | Yes, with restrictions |
| Device events | 1 | No |
| Commands | 1 | No |
| Command results | 1 | No |

### 15.1 QoS 0

QoS 0 provides at-most-once delivery.

It is suitable for frequent telemetry where occasional message loss is acceptable.

### 15.2 QoS 1

QoS 1 provides at-least-once delivery.

Duplicate messages are possible.

Application-level deduplication SHALL be implemented where required.

### 15.3 QoS Requirements

**REQ-MQTT-036**  
The system SHALL NOT assume QoS 1 provides exactly-once application processing.

**REQ-MQTT-037**  
Command processing SHALL be idempotent where practical.

**REQ-MQTT-038**  
The Operations Service SHALL tolerate duplicate event delivery.

**REQ-MQTT-039**  
QoS selection SHALL be configurable where justified.

---

## 16. Retained Messages

Retained messages SHALL be used selectively.

### 16.1 Telemetry

Telemetry SHALL NOT be retained.

### 16.2 Device Events

Device events SHALL NOT be retained.

### 16.3 Commands

Commands SHALL NOT be retained.

This prevents a newly connected device from receiving an old retained command.

### 16.4 Device Status

Device status MAY be retained to provide the most recent reported status.

Retained status SHALL NOT be treated as proof of current connectivity.

The Operations Service SHALL determine current connectivity using message freshness and trusted broker events where available.

### 16.5 Last Will and Testament

Device clients SHOULD configure MQTT Last Will and Testament (LWT).

Example LWT status:

```json
{
  "schemaVersion": "1.0",
  "messageId": "lwt-KIOSK-TAM-003-session-01",
  "deviceId": "KIOSK-TAM-003",
  "eventTime": "2026-10-08T04:00:00Z",
  "messageType": "STATUS",
  "payload": {
    "connectionStatus": "OFFLINE",
    "reason": "UNEXPECTED_DISCONNECT"
  }
}
```

Because LWT payloads are configured before disconnection, their eventTime SHALL represent message creation time rather than an exact broker-detected disconnect time.

The gateway SHALL record its own receipt timestamp.

LWT SHALL be treated as a connectivity signal, not an authoritative health diagnosis.

---

## 17. Reconnection Behaviour

Devices and the gateway SHALL support controlled reconnection.

### 17.1 Device Reconnection

After reconnecting, a device SHALL:

1. Re-establish MQTT connectivity.
2. Re-subscribe to its command topic.
3. Publish a fresh status message.
4. Resume telemetry publication.
5. Resume device event publication.

### 17.2 Gateway Reconnection

After reconnecting, the IoT Gateway SHALL restore required subscriptions.

### 17.3 Offline Behaviour

Devices MAY continue local operation during MQTT disconnection.

Comanche SHALL determine OFFLINE state using its deterministic connectivity rules.

### 17.4 Buffered Messages

The MVP MAY support bounded buffering.

Unlimited buffering SHALL NOT be permitted.

Buffered messages SHALL retain original eventTime values.

---

## 18. Duplicate Handling

MQTT QoS 1 delivery can produce duplicate messages.

The IoT Gateway and downstream consumers SHALL support duplicate-safe processing.

### 18.1 Message Deduplication

The messageId SHALL identify a logical published message.

Repeated delivery of the same messageId SHALL NOT create duplicate logical effects.

### 18.2 Command Deduplication

The commandId SHALL identify a logical device command.

A device SHALL NOT execute the same commandId repeatedly within its configured deduplication retention period.

### 18.3 Command Retries

Retries SHALL preserve commandId.

A new commandId SHALL represent a new logical command authorization, not a retransmission of an existing command.

---

## 19. Out-of-Order Messages

Messages MAY arrive out of order.

The gateway SHALL preserve eventTime and receivedAt information.

The Operations Service SHALL NOT overwrite newer accepted device state with stale telemetry.

### 19.1 Ordering Strategy

The MVP SHOULD use per-device ordering metadata where necessary.

Candidate fields:

- deviceId.
- messageId.
- eventTime.
- sequenceNumber.
- sessionId.

The final ordering strategy SHALL be coordinated with SPEC-006.

### 19.2 Clock Reliability

Device clocks MAY differ from platform clocks.

The platform SHALL NOT rely exclusively on eventTime for security-sensitive command authorization or expiry checks.

---

## 20. Command Authorization Boundary

The MQTT broker SHALL NOT be responsible for business-level remediation authorization.

Authorization SHALL occur in the Operations Service.

### 20.1 Authorized Flow

```text
AI Recommendation
        |
        v
Engineer Review
        |
        v
Engineer Approval
        |
        v
Operations Service
        |
        | Validate authorization
        v
Authorized Command
        |
        v
IoT Gateway
        |
        | Publish MQTT command
        v
MQTT Broker
        |
        v
Target Device
```

### 20.2 Prohibited Flow

```text
AI Agent
    |
    v
MQTT Command
    |
    v
Device
```

This flow SHALL NOT be technically possible under the deployed permissions.

### 20.3 Authorization Requirements

**REQ-MQTT-040**  
The AIOps Service SHALL NOT possess MQTT command-publishing credentials.

**REQ-MQTT-041**  
The gateway SHALL accept outbound commands only through a trusted application interface.

**REQ-MQTT-042**  
The gateway SHALL NOT expose an unrestricted arbitrary-topic MQTT publish API.

**REQ-MQTT-043**  
Commands SHALL be restricted to supported command types and validated parameters.

**REQ-MQTT-044**  
The Operations Service SHALL maintain the engineer approval and command audit record.

---

## 21. MQTT Access Control

The broker SHALL enforce least-privilege topic permissions.

### 21.1 Device Permissions

A device SHOULD be permitted to:

**Publish:**

```text
comanche/devices/{ownDeviceId}/telemetry
comanche/devices/{ownDeviceId}/status
comanche/devices/{ownDeviceId}/events
comanche/devices/{ownDeviceId}/command-results
```

**Subscribe:**

```text
comanche/devices/{ownDeviceId}/commands
```

### 21.2 Gateway Permissions

The gateway SHALL be permitted to:

**Subscribe:**

```text
comanche/devices/+/telemetry
comanche/devices/+/status
comanche/devices/+/events
comanche/devices/+/command-results
```

**Publish:**

```text
comanche/devices/+/commands
```

### 21.3 AIOps Permissions

The AIOps Service SHALL have no MQTT command permissions.

The preferred implementation is that the AIOps Service has no MQTT connection at all.

### 21.4 Credential Separation

Device, gateway, and application credentials SHALL be separated.

Credentials SHALL NOT be shared unnecessarily between services.

---

## 22. Gateway Validation

The IoT Gateway SHALL validate incoming messages before forwarding them to Kafka.

Validation SHALL include:

- Valid JSON.
- Supported schemaVersion.
- Required envelope fields.
- Supported messageType.
- Topic/deviceId consistency.
- Valid timestamps.
- Supported metric names.
- Valid metric ranges.
- Message-size limits.
- Known or permitted device identity.

### 22.1 Invalid Messages

Invalid messages SHALL NOT enter normal operational processing.

The gateway SHOULD log a structured rejection reason.

Sensitive credentials SHALL NOT appear in rejection logs.

### 22.2 Unknown Devices

The MVP SHALL use a registered-device model.

Messages from unknown devices SHALL be rejected or quarantined.

Automatic device registration is not required.

---

## 23. MQTT-to-Kafka Mapping

The IoT Gateway SHALL normalize accepted MQTT messages into internal Kafka events.

Candidate mapping:

| MQTT Message | Kafka Topic |
|---|---|
| Telemetry | `comanche.device.telemetry.v1` |
| Status | `comanche.device.status.v1` |
| Device event | `comanche.device.events.v1` |
| Command result | `comanche.command.events.v1` |

Exact Kafka schemas SHALL be defined in SPEC-006.

### 23.1 Mapping Requirements

**REQ-MQTT-045**  
The gateway SHALL preserve original deviceId.

**REQ-MQTT-046**  
The gateway SHALL preserve original messageId.

**REQ-MQTT-047**  
The gateway SHALL preserve original eventTime.

**REQ-MQTT-048**  
The gateway SHALL add gateway receipt or processing metadata where appropriate.

**REQ-MQTT-049**  
The gateway SHALL NOT silently discard valid messages because Kafka is temporarily unavailable.

A bounded retry, buffering, or explicit failure policy SHALL be defined.

---

## 24. Error Handling

### 24.1 Broker Unavailable

Clients SHALL attempt controlled reconnection.

### 24.2 Invalid Payload

The gateway SHALL reject malformed messages.

### 24.3 Unsupported Schema

The gateway SHALL reject unsupported schema versions.

### 24.4 Unknown Device

The gateway SHALL reject or quarantine messages from unregistered devices.

### 24.5 Command Timeout

The Operations Service SHALL determine command timeout according to its command lifecycle rules.

### 24.6 Command Failure

A failed command SHALL remain recorded.

The system SHALL NOT automatically assume recovery.

### 24.7 Gateway Failure

The gateway SHALL restore subscriptions after restart.

MQTT QoS and session behaviour SHALL be configured consistently with the selected delivery guarantees.

---

## 25. Observability

The IoT Gateway SHOULD expose operational metrics including:

- MQTT connection status.
- MQTT messages received.
- Messages rejected.
- Kafka events published.
- Kafka publication failures.
- Commands published.
- Command results received.
- MQTT reconnect count.
- Processing latency.

Structured logs SHOULD include:

```text
deviceId
messageId
messageType
topic
receivedAt
processingResult
```

Logs SHALL NOT include authentication secrets.

---

## 26. Performance Requirements

The MQTT integration SHALL support the capstone demonstration scale.

### 26.1 Target Scale

| Environment | Devices |
|---|---:|
| Development | 5–10 |
| Demonstration | 10–20 |
| Normal simulation | 50–100 |
| Optional scalability test | Approximately 1,000 |

### 26.2 Message Frequency

Telemetry frequency SHALL be configurable.

For example, 100 devices publishing once every five seconds would generate approximately 20 telemetry messages per second, excluding status, events, and command traffic.

This is an illustrative load calculation, not a performance guarantee.

### 26.3 Resource Controls

The gateway SHALL avoid unbounded memory buffering.

Message-size limits SHALL be enforced.

---

## 27. Testing Strategy

### 27.1 Contract Tests

Tests SHALL verify:

- Required envelope fields.
- Schema-version validation.
- Topic/device identity matching.
- Metric type validation.
- Metric range validation.
- Invalid JSON rejection.
- Unsupported messageType rejection.

### 27.2 MQTT Integration Tests

Tests SHOULD verify:

- Device telemetry publication.
- Gateway telemetry subscription.
- Device event publication.
- Command publication.
- Command result publication.
- QoS 1 duplicate handling.
- Broker reconnection.
- Retained-message behaviour.

### 27.3 Security Tests

Tests SHALL verify:

- A device cannot publish to another device's topics.
- A device cannot publish commands.
- The gateway can publish authorized commands.
- The AIOps Service cannot publish MQTT commands.
- Unauthorized clients cannot subscribe to protected topics.

### 27.4 End-to-End Tests

An end-to-end test SHALL demonstrate:

```text
Pekko Device
    |
    v
MQTT Broker
    |
    v
IoT Gateway
    |
    v
Kafka
    |
    v
Operations Service
```

A second test SHALL demonstrate:

```text
Engineer Approval
    |
    v
Operations Service
    |
    v
IoT Gateway
    |
    v
MQTT Command
    |
    v
Pekko Device
    |
    v
Command Result
```

---

## 28. Acceptance Criteria

**AC-005-01**  
A registered simulated kiosk can connect to Mosquitto.

**AC-005-02**  
The kiosk can publish telemetry to its assigned topic.

**AC-005-03**  
The IoT Gateway receives the telemetry.

**AC-005-04**  
The gateway validates the message envelope.

**AC-005-05**  
A malformed telemetry message is rejected.

**AC-005-06**  
A message with a mismatched topic deviceId and payload deviceId is rejected.

**AC-005-07**  
An unknown device is rejected or quarantined.

**AC-005-08**  
Operational-state events are received and forwarded.

**AC-005-09**  
The gateway can publish an authorized command.

**AC-005-10**  
The target device receives the command.

**AC-005-11**  
The device publishes a command acknowledgement.

**AC-005-12**  
The device publishes a final command outcome.

**AC-005-13**  
Duplicate command delivery does not cause unintended repeated execution.

**AC-005-14**  
Expired commands are rejected.

**AC-005-15**  
Commands are not retained by the broker.

**AC-005-16**  
The AIOps Service cannot publish device commands.

**AC-005-17**  
A device cannot publish to another device's protected topics.

**AC-005-18**  
The gateway reconnects after temporary broker unavailability.

**AC-005-19**  
Telemetry messages preserve eventTime and messageId through MQTT-to-Kafka mapping.

**AC-005-20**  
A successful command result does not automatically resolve the associated incident.

---

## 29. Implementation Milestones

### Milestone 1 — MQTT Foundation

Implement:

- Mosquitto Docker Compose configuration.
- Authentication.
- Topic ACLs.
- Gateway MQTT client.
- Simulator MQTT client.
- Connection and reconnection handling.

### Milestone 2 — Telemetry and Events

Implement:

- Common JSON envelope.
- Telemetry payload.
- Device status payload.
- Device event payload.
- Gateway validation.
- MQTT-to-Kafka mapping.

### Milestone 3 — Commands

Implement:

- Command payload.
- Command-result payload.
- Command routing.
- Command expiry validation.
- Command deduplication.
- Command outcome forwarding.

### Milestone 4 — Security

Implement:

- Per-device topic restrictions.
- Gateway-only command publishing.
- Separate service credentials.
- Security integration tests.

### Milestone 5 — End-to-End Verification

Validate the complete telemetry and command paths with the Pekko simulator and Operations Service.

---

## 30. Open Design Decisions

The following details remain to be finalized before implementation:

1. Exact MQTT client library and version.
2. MQTT session persistence and clean-session configuration.
3. Command deduplication retention period.
4. Gateway buffering and retry limits.
5. Message-size limits.
6. Whether sequenceNumber and sessionId become mandatory.
7. Device clock-skew tolerance.
8. Retained status expiry and stale-status presentation.
9. Broker credential provisioning for simulated devices.
10. Command delivery behaviour across gateway restarts.
11. Precise MQTT-to-Kafka publication failure semantics.
12. MQTT 5.0 adoption beyond the MVP.

---

## 31. Final Communication Principle

MQTT SHALL provide a stable, device-facing communication interface independent of the internal Comanche implementation.

The IoT Gateway SHALL translate validated device messages into internal Kafka events and publish only authorized commands to devices.

The AIOps Service SHALL remain isolated from device-control capabilities.

**Architectural statement:**

> Devices communicate through MQTT. The IoT Gateway validates and routes messages. Kafka transports internal events. The Operations Service authorizes device-changing actions. AI investigates and recommends but cannot publish device commands.
