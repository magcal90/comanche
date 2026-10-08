# SPEC-002 — Project Comanche System Architecture

**Project:** Comanche  
**Description:** AI-Assisted AIOps and Operational Visibility Platform for IoT Devices  
**Reference Organization:** Davy Jones Burger  
**Status:** Draft  
**Version:** 0.2  
**Type:** System Architecture Specification  
**Depends On:** SPEC-001 — Vision and Scope

---

## 1. Purpose

This specification defines the high-level architecture, component responsibilities, service boundaries, communication patterns, data ownership, and deployment topology for Project Comanche.

The architecture SHALL support:

- Fleet-wide IoT device monitoring.
- Real-time operational visibility.
- Deterministic abnormality detection.
- Alert and incident management.
- AI-assisted incident investigation.
- Human-reviewed remediation recommendations.
- Engineer-approved device commands.
- Deterministic recovery verification.

The architecture SHALL remain suitable for a resource-constrained AI Engineering capstone project.

---

## 2. Architectural Principles

The system SHALL follow these principles:

1. **Separation of simulation and platform:** Apache Pekko SHALL be used only for the device simulator.
2. **IoT protocol boundary:** MQTT SHALL be the communication interface between devices and Comanche.
3. **Event-driven integration:** Kafka SHALL provide asynchronous communication between backend components where appropriate.
4. **Deterministic monitoring:** Telemetry processing, abnormality detection, and incident creation SHALL NOT depend on AI.
5. **Read-only AI investigation:** The AIOps agent SHALL have no device-control capabilities.
6. **Human-controlled remediation:** Device-changing actions SHALL require explicit engineer approval.
7. **Service-owned data:** Services SHALL access operational data through defined interfaces rather than sharing database tables.
8. **Failure isolation:** AI unavailability SHALL NOT interrupt core monitoring.
9. **Local-first development:** The system SHALL run locally without mandatory paid cloud infrastructure.
10. **Architectural simplicity:** The system SHALL avoid unnecessary microservices and infrastructure.

---

## 3. System Context

Comanche operates between a fleet of IoT devices and two primary user groups.

### 3.1 External Actors

**CIO / Operations Director**

Uses Comanche to:

- Monitor fleet availability.
- View outlet-level device utilization.
- Identify out-of-service devices.
- Compare lunch and dinner operational activity.
- Understand operational capacity and business impact.

**IT Operations Engineer**

Uses Comanche to:

- Monitor device health.
- Review alerts and incidents.
- Examine telemetry and logs.
- Request or review AI investigations.
- Review remediation recommendations.
- Approve, reject, or defer remediation.
- Verify device recovery.

**IoT Device**

Represents a physical or simulated kiosk/POS terminal that:

- Publishes telemetry.
- Publishes operational and device events.
- Reports connectivity and status.
- Receives authorized commands.
- Acknowledges command execution.

**AI Provider**

Provides LLM inference for investigation and recommendation.

The provider MAY be OpenAI or a locally hosted Ollama model.

---

## 4. High-Level Architecture

```text
              DAVY JONES BURGER
              IoT Device Estate
                       |
             +---------+---------+
             |                   |
        Kiosk Devices       POS Terminals
             |                   |
             +---------+---------+
                       |
                      MQTT
                       |
                       v
              +----------------+
              | MQTT Broker    |
              | Mosquitto      |
              +--------+-------+
                       |
                       v
              +----------------+
              | IoT Gateway    |
              | Spring Boot    |
              +--------+-------+
                       |
                      Kafka
                       |
                       v
              +----------------+
              | Operations     |
              | Service        |
              |                |
              | Device State   |
              | Telemetry      |
              | Detection      |
              | Alerts         |
              | Incidents      |
              | Utilization    |
              | Commands       |
              | Tickets        |
              +---+---------+--+
                  |         |
                  v         |
             PostgreSQL     |
                            |
                     Incident Events
                            |
                            v
                    +---------------+
                    | AIOps Service |
                    | Spring AI     |
                    +-------+-------+
                            |
                  Read-only APIs
                  Knowledge Sources
                            |
                            v
                    AI Investigation
                    Recommendation
                            |
                            v
                    Operations Service
                            |
                            v
                      React Web UI
                            |
                            v
                       Engineer
                    Review / Approve
                            |
                            v
                    Operations Service
                    Command Authorization
                            |
                            v
                       IoT Gateway
                            |
                           MQTT
                            |
                            v
                         Device
```

This diagram represents logical data flow rather than a strict physical deployment layout.

---

## 5. Component Inventory

The MVP SHALL consist of the following components.

| Component | Technology | Type |
|---|---|---|
| Device Simulator | Java 21, Apache Pekko | External simulator |
| MQTT Broker | Eclipse Mosquitto | Infrastructure |
| IoT Gateway | Spring Boot | Backend service |
| Kafka | Apache Kafka | Infrastructure |
| Operations Service | Spring Boot | Backend service |
| PostgreSQL | PostgreSQL | Database |
| AIOps Service | Spring Boot, Spring AI | Backend service |
| Web Console | React, TypeScript | Frontend |
| AI Provider | OpenAI / Ollama | External or local dependency |

The initial design SHALL NOT split alerts, incidents, commands, tickets, and utilization into separate microservices.

---

## 6. Device Simulator

**Module:** `comanche-device-simulator`

**Technology:**

- Java 21
- Apache Pekko
- MQTT client library

### 6.1 Responsibilities

The simulator SHALL:

- Instantiate configured kiosks and POS terminals.
- Maintain independent state for each device.
- Simulate normal device behaviour.
- Simulate customer ordering activity.
- Simulate lunch and dinner workload changes.
- Generate telemetry.
- Generate operational events.
- Support deterministic fault injection.
- Publish device messages through MQTT.
- Receive authorized MQTT commands.
- Simulate remediation outcomes.

### 6.2 Isolation

The simulator SHALL NOT:

- Call Operations Service methods directly.
- Write directly to the Comanche database.
- Publish internal Kafka events.
- Invoke the AIOps Service directly.
- Share in-memory device objects with platform services.

The simulator SHALL use the same MQTT interface that physical devices would use.

### 6.3 Actor Model

The preferred model is:

```text
DeviceFarm
    |
    +-- KioskActor
    +-- KioskActor
    +-- KioskActor
    |
    +-- PosTerminalActor
    +-- PosTerminalActor
```

One device actor represents one simulated physical device.

Detailed simulation behaviour SHALL be defined in SPEC-004.

---

## 7. MQTT Broker

**Component:** Eclipse Mosquitto

### 7.1 Responsibilities

The MQTT broker SHALL:

- Accept device MQTT connections.
- Receive published telemetry.
- Route telemetry to subscribed gateway clients.
- Deliver device events and status.
- Deliver authorized commands to target devices.
- Deliver command acknowledgements to Comanche.

### 7.2 Communication Direction

Device to platform:

```text
Device
   |
   | publish telemetry/events
   v
MQTT Broker
   |
   | subscribed delivery
   v
IoT Gateway
```

Platform to device:

```text
Operations Service
   |
   | authorized command
   v
IoT Gateway
   |
   | MQTT publish
   v
MQTT Broker
   |
   | subscribed delivery
   v
Device
```

### 7.3 Security Boundary

The AIOps Service SHALL NOT have MQTT command-publishing credentials.

MQTT topic access SHOULD be restricted using broker authentication and access-control rules.

Detailed MQTT requirements SHALL be defined in SPEC-005.

---

## 8. IoT Gateway Service

**Module:** `comanche-iot-gateway`

**Technology:** Spring Boot

### 8.1 Responsibilities

The IoT Gateway SHALL:

- Connect to the MQTT broker.
- Subscribe to approved device topics.
- Receive telemetry and device events.
- Validate incoming message envelopes.
- Normalize incoming messages.
- Publish internal events to Kafka.
- Receive authorized command requests from the Operations Service.
- Publish approved commands to MQTT.
- Receive command acknowledgements.
- Forward command outcomes to the Operations Service.

### 8.2 Inbound Flow

```text
MQTT Message
    |
    v
Payload Validation
    |
    v
Device Identity Validation
    |
    v
Message Normalization
    |
    v
Kafka Event Publication
```

### 8.3 Outbound Flow

```text
Authorized Command Request
    |
    v
Gateway Validation
    |
    v
MQTT Command Publication
    |
    v
Device Acknowledgement
    |
    v
Command Outcome Event
```

### 8.4 Gateway Constraints

The IoT Gateway SHALL NOT:

- Perform AI investigation.
- Calculate business utilization.
- Own incident lifecycle rules.
- Independently authorize remediation.
- Execute arbitrary commands requested by the AI.

The gateway SHALL accept device-changing commands only through a trusted application interface.

---

## 9. Kafka Event Backbone

**Component:** Apache Kafka

Kafka SHALL be the internal asynchronous event backbone.

### 9.1 Responsibilities

Kafka SHALL transport:

- Device telemetry events.
- Device status events.
- Device operational events.
- Device fault events.
- Alert events.
- Incident events.
- Command outcome events.

### 9.2 Candidate Topics

```text
comanche.device.telemetry.v1

comanche.device.status.v1

comanche.device.events.v1

comanche.alert.events.v1

comanche.incident.events.v1

comanche.command.events.v1
```

Final naming and event schemas SHALL be defined in SPEC-006.

### 9.3 Event Processing

Consumers SHALL be designed to tolerate duplicate delivery.

Where event ordering is important, related device events SHOULD use `deviceId` as the Kafka partition key.

### 9.4 Scope

Kafka SHALL NOT be exposed directly to IoT devices.

The MVP SHALL NOT require a complex stream-processing framework unless demonstrated requirements justify it.

---

## 10. Operations Service

**Module:** `comanche-ops-service`

**Technology:**

- Java 21
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Kafka consumer/producer

The Operations Service is the primary operational domain service.

### 10.1 Responsibilities

The Operations Service SHALL own:

- Organization and site registry.
- Device registry.
- Device health state.
- Device operational state.
- Device connectivity state.
- Telemetry processing.
- Current device metrics.
- Abnormality detection.
- Alert lifecycle.
- Incident lifecycle.
- Operational utilization.
- Management dashboard metrics.
- Remediation recommendations and review status.
- Engineer approval records.
- Command authorization.
- Command lifecycle.
- Service tickets.
- Recovery verification.

### 10.2 Internal Modules

The Operations Service SHOULD be organized into logical modules:

```text
comanche-ops-service
|
+-- organization
+-- site
+-- device
+-- telemetry
+-- detection
+-- alert
+-- incident
+-- utilization
+-- recommendation
+-- approval
+-- command
+-- ticket
+-- recovery
```

These SHALL initially be modules/packages within one deployable service, not separate microservices.

### 10.3 Detection Engine

The detection engine SHALL use deterministic rules.

Candidate rule types:

- Threshold.
- Consecutive abnormal readings.
- Time-duration persistence.
- Multi-signal correlation.
- Missing telemetry timeout.
- Optional trend detection.

The engine SHALL support alert deduplication.

The detection engine SHALL NOT invoke an LLM for every telemetry message.

### 10.4 Operational Metrics

The Operations Service SHALL calculate:

- Fleet availability.
- Site availability.
- Number of devices taking orders.
- Number of idle devices.
- Number of out-of-service devices.
- Utilization by site.
- Lunch/dinner utilization.
- Active incident counts.

### 10.5 Data Access

The Operations Service SHALL expose read-only APIs for AIOps investigation.

The AIOps Service SHALL NOT directly query the Operations Service database.

---

## 11. PostgreSQL Data Storage

PostgreSQL SHALL be the primary persistent database for the MVP.

### 11.1 Data Ownership

The Operations Service SHALL own the operational schema.

Candidate tables include:

```text
organization
site
device
device_current_state
device_telemetry
device_event
alert
incident
incident_evidence
investigation_report
remediation_recommendation
remediation_approval
device_command
service_ticket
```

This list is conceptual and SHALL be refined during detailed design.

### 11.2 Telemetry Retention

The MVP MAY retain a bounded telemetry history.

Retention SHALL be configurable to avoid uncontrolled database growth.

### 11.3 Service Isolation

The AIOps Service SHALL NOT access operational database tables directly.

All operational evidence retrieval SHALL occur through approved APIs.

---

## 12. AIOps Service

**Module:** `comanche-aiops-service`

**Technology:**

- Java 21
- Spring Boot
- Spring AI
- Configurable LLM provider

### 12.1 Responsibilities

The AIOps Service SHALL:

- Receive investigation requests.
- Retrieve relevant incident information.
- Gather device telemetry.
- Retrieve device logs.
- Retrieve relevant configuration.
- Search historical incidents.
- Search runbooks.
- Correlate evidence.
- Produce probable diagnoses.
- Produce remediation recommendations.
- Explain uncertainty.
- Return structured investigation reports.

### 12.2 Investigation Trigger

An investigation MAY be initiated by:

- A qualifying incident event.
- An explicit engineer request.

The system SHOULD avoid repeated investigations for duplicate alerts belonging to the same unresolved incident.

### 12.3 Read-Only Tools

Candidate AI tools:

```text
getDeviceStatus(deviceId)

getTelemetry(deviceId, timeRange)

getDeviceLogs(deviceId, timeRange)

getDeviceConfiguration(deviceId)

searchHistoricalIncidents(query)

searchRunbooks(query)

searchEngineeringIssues(query)

getServiceTicket(ticketId)
```

All tools SHALL be read-only with respect to managed devices and operational authorization state.

### 12.4 Prohibited Capabilities

The AIOps Service SHALL NOT possess:

```text
restartDevice()

restartPrinter()

publishMqttCommand()

updateDeviceConfiguration()

executeDeviceShellCommand()

approveRemediation()

executeRemediation()
```

The service SHALL NOT hold credentials that permit equivalent actions through indirect APIs.

### 12.5 Investigation Output

The AIOps Service SHALL return a structured investigation report.

Candidate fields:

```text
investigationId
incidentId
deviceId

probableCause
confidence
reasoningSummary

evidence[]
relatedIncidents[]
relevantRunbooks[]

recommendedAction
recommendationReason
risk

modelProvider
modelIdentifier
investigatedAt
```

AI confidence SHALL be treated as a model-generated estimate, not a calibrated probability unless separately validated.

### 12.6 Persistence

The AIOps Service SHOULD remain stateless where practical.

The Operations Service MAY persist investigation reports and recommendations through a dedicated authenticated interface.

This interface SHALL NOT permit the AIOps Service to approve or execute remediation.

---

## 13. AI Provider Integration

The system SHOULD support:

- OpenAI for hosted inference.
- Ollama for local inference where practical.

The selected provider and model SHALL be configurable.

Provider failures SHALL be handled gracefully.

### 13.1 AI Unavailability

When AI is unavailable:

- Telemetry ingestion SHALL continue.
- Device-state updates SHALL continue.
- Alerts SHALL continue.
- Incidents SHALL continue.
- Dashboards SHALL remain operational.
- Engineers SHALL still be able to investigate manually.
- Authorized deterministic command workflows SHALL remain available.

An AI outage SHALL NOT be interpreted as a device outage.

---

## 14. Web Operations Console

**Module:** `comanche-web`

**Technology:**

- React
- TypeScript

### 14.1 User Roles

The MVP SHALL distinguish:

**Management User**

- View fleet metrics.
- View outlet metrics.
- View utilization.
- View incidents.
- View device availability.

**Operations Engineer**

- View fleet and device information.
- Inspect incidents.
- Review AI investigations.
- Review evidence.
- Approve or reject remediation.
- View command execution results.
- Verify recovery.

### 14.2 Primary Screens

Candidate screens:

```text
Fleet Dashboard

Site Dashboard

Device Details

Telemetry History

Alerts

Incidents

AI Investigation Report

Remediation Review

Service Tickets
```

### 14.3 Backend Access

The frontend SHALL communicate through authenticated application APIs.

The frontend SHALL NOT:

- Connect directly to PostgreSQL.
- Publish device MQTT commands.
- Access Kafka directly.
- Call device administration interfaces directly.

### 14.4 Live Updates

The MVP MAY initially use periodic REST polling.

Server-Sent Events or WebSockets MAY be introduced if justified by demonstration needs.

Real-time dashboard updates SHALL NOT require direct browser access to MQTT or Kafka.

---

## 15. Communication Patterns

The architecture SHALL distinguish synchronous queries from asynchronous events.

### 15.1 REST

REST SHALL be used for:

- Dashboard queries.
- Site details.
- Device details.
- Telemetry history queries.
- Incident details.
- Investigation requests.
- AI read-only evidence retrieval.
- Engineer approval/rejection.
- Ticket queries.

### 15.2 Kafka

Kafka SHALL be used for asynchronous events, including:

- Device telemetry ingestion.
- Device status updates.
- Device operational events.
- Alert and incident notifications.
- Command outcome notifications.

### 15.3 MQTT

MQTT SHALL be used for:

- Device telemetry publication.
- Device event publication.
- Device status reporting.
- Approved device commands.
- Device command acknowledgements.

### 15.4 Protocol Responsibility

```text
MQTT
Device ↔ IoT Gateway

Kafka
Internal asynchronous integration

REST
Frontend ↔ Backend
AIOps ↔ Read-only operational APIs
Engineer approval APIs
```

---

## 16. End-to-End Telemetry Flow

```text
KioskActor
    |
    | Publish telemetry
    v
Mosquitto
    |
    v
IoT Gateway
    |
    | Validate / normalize
    v
Kafka
    |
    v
Operations Service
    |
    +-- Update current device state
    |
    +-- Store telemetry
    |
    +-- Evaluate detection rules
    |
    +-- Update operational metrics
    |
    +-- Generate alert if necessary
    |
    +-- Create/update incident
```

The telemetry ingestion path SHALL NOT depend on the AIOps Service.

---

## 17. End-to-End Investigation Flow

```text
Detection Engine
       |
       v
     Alert
       |
       v
    Incident
       |
       v
Incident Event
       |
       v
AIOps Service
       |
       +-- getDeviceStatus
       |
       +-- getTelemetry
       |
       +-- getDeviceLogs
       |
       +-- searchHistoricalIncidents
       |
       +-- searchRunbooks
       |
       v
Investigation Report
       |
       v
Operations Service
       |
       v
Engineer Web Console
```

The AI SHALL retrieve evidence through approved interfaces.

The AI SHALL NOT modify device state or command authorization state.

---

## 18. End-to-End Remediation Flow

```text
AI Recommendation
        |
        v
Operations Service
        |
        v
Engineer Web Console
        |
        v
Engineer Reviews Evidence
        |
    +---+---+
    |       |
  Reject  Approve
    |       |
    v       v
 Record   Authorization
Decision  Validation
            |
            v
       Command Record
            |
            v
        IoT Gateway
            |
            v
       MQTT Broker
            |
            v
           Device
            |
            v
      Command Result
            |
            v
      Operations Service
            |
            v
     Recovery Verification
```

### 18.1 Command Authorization

The Operations Service SHALL verify:

- Engineer identity.
- Engineer authorization.
- Recommendation review status.
- Target device identity.
- Allowed command type.
- Command parameters.
- Approval validity.
- Duplicate command prevention.

### 18.2 Gateway Validation

The IoT Gateway SHALL reject commands that do not originate from its trusted command interface.

The gateway SHALL NOT accept arbitrary MQTT publication requests from the AIOps Service.

### 18.3 Recovery Verification

Command acknowledgement SHALL NOT be treated as proof of recovery.

The Operations Service SHALL evaluate subsequent telemetry and device state.

---

## 19. Security Architecture

The MVP SHALL implement a minimal but meaningful security model.

### 19.1 Authentication

Management and engineer operations SHALL require authenticated users.

### 19.2 Authorization

Only authorized engineers SHALL approve device-changing actions.

### 19.3 Service Permissions

Service credentials SHALL follow least-privilege principles.

In particular:

- Device clients SHALL publish only to permitted device topics.
- Device clients SHALL subscribe only to their permitted command topics.
- The IoT Gateway SHALL have the required broker permissions.
- The AIOps Service SHALL have no MQTT command permissions.
- The AIOps Service SHALL have no device administration credentials.

### 19.4 Auditability

The system SHALL record:

- Engineer approval or rejection.
- Identity of approving engineer.
- Recommendation being reviewed.
- Command issued.
- Target device.
- Command outcome.
- Recovery verification outcome.

### 19.5 Secrets

Secrets SHALL NOT be committed to Git.

Local development SHOULD use environment variables or local secret configuration.

---

## 20. Reliability and Failure Handling

### 20.1 MQTT Unavailable

The IoT Gateway SHALL detect broker disconnection and attempt controlled reconnection.

Devices MAY buffer or retry messages according to SPEC-005.

### 20.2 Kafka Unavailable

The IoT Gateway SHALL handle publication failures without silently claiming successful event delivery.

Retry and buffering limits SHALL be explicitly defined.

### 20.3 Operations Service Unavailable

Kafka consumer recovery SHOULD allow the Operations Service to resume processing retained events, subject to configured retention and consumer offsets.

### 20.4 AIOps Service Unavailable

Monitoring and incident management SHALL continue.

Investigations MAY be marked pending or failed and retried according to a bounded policy.

### 20.5 Device Offline

Missing telemetry SHALL be evaluated using deterministic connectivity timeout rules.

### 20.6 Duplicate Messages

Consumers SHALL tolerate duplicate delivery.

Commands SHALL use unique identifiers and idempotency controls to reduce accidental repeated execution.

### 20.7 Out-of-Order Telemetry

The Operations Service SHALL distinguish event time from ingestion time and SHALL NOT allow stale telemetry to incorrectly overwrite newer device state.

---

## 21. Deployment Architecture

The MVP SHALL support local execution using Docker Compose.

### 21.1 Local Environment

```text
Developer Machine
|
+-- Docker Compose
|    |
|    +-- Mosquitto
|    +-- Kafka
|    +-- PostgreSQL
|    +-- IoT Gateway
|    +-- Operations Service
|    +-- AIOps Service
|
+-- React Development Server
|
+-- Pekko Device Simulator
|
+-- Ollama (optional)
```

The simulator and React frontend MAY also run inside containers.

### 21.2 Cloud Deployment

Cloud deployment MAY be introduced for demonstration or CI/CD learning.

The MVP SHALL NOT require:

- Kubernetes.
- Managed Kafka.
- Managed MQTT.
- Managed PostgreSQL.
- Production high-availability infrastructure.

---

## 22. CI/CD Architecture

GitHub Actions SHOULD provide automated workflows for:

- Java compilation.
- Unit testing.
- Spring Boot integration testing.
- Frontend linting.
- Frontend testing.
- Frontend production build.
- Docker image build where applicable.

The initial CI pipeline SHOULD prioritize reproducible builds and automated test feedback.

Deployment automation MAY be added after the local vertical slice is operational.

---

## 23. Observability

The system SHOULD provide:

- Structured application logs.
- Service health endpoints.
- MQTT connection status.
- Kafka consumer health.
- Telemetry ingestion counts.
- Alert and incident counts.
- AI investigation status.
- Command execution audit records.

Spring Boot Actuator SHOULD be used for basic backend health monitoring.

Prometheus, Grafana, and distributed tracing MAY be added later if time permits.

---

## 24. Architecture Requirements

**REQ-ARCH-001**  
The device simulator SHALL remain separate from the Comanche platform.

**REQ-ARCH-002**  
Platform services SHALL NOT depend on Pekko.

**REQ-ARCH-003**  
IoT devices SHALL communicate through MQTT.

**REQ-ARCH-004**  
Kafka SHALL provide internal asynchronous event integration.

**REQ-ARCH-005**  
The IoT Gateway SHALL own the MQTT protocol integration boundary.

**REQ-ARCH-006**  
The Operations Service SHALL own operational domain state and incident lifecycle.

**REQ-ARCH-007**  
Technical health and operational activity SHALL be represented independently.

**REQ-ARCH-008**  
Abnormality detection SHALL be deterministic.

**REQ-ARCH-009**  
AI investigation SHALL use read-only operational interfaces.

**REQ-ARCH-010**  
The AIOps Service SHALL NOT possess device-control capabilities.

**REQ-ARCH-011**  
Device-changing commands SHALL require authorized engineer approval.

**REQ-ARCH-012**  
The Operations Service SHALL validate command authorization before execution.

**REQ-ARCH-013**  
The IoT Gateway SHALL NOT independently authorize remediation.

**REQ-ARCH-014**  
Recovery SHALL be verified using observed device telemetry.

**REQ-ARCH-015**  
Core monitoring SHALL continue when AI is unavailable.

**REQ-ARCH-016**  
The system SHALL support local development without mandatory paid cloud services.

**REQ-ARCH-017**  
Backend services SHALL NOT directly share ownership of operational database tables.

**REQ-ARCH-018**  
The MVP SHALL avoid unnecessary service decomposition.

**REQ-ARCH-019**  
The React frontend SHALL NOT directly access MQTT, Kafka, or PostgreSQL.

**REQ-ARCH-020**  
The platform SHALL preserve audit records for remediation approvals and command execution.

---

## 25. Architecture Acceptance Criteria

**AC-002-01**  
A Pekko kiosk can publish telemetry through MQTT.

**AC-002-02**  
The IoT Gateway can receive and validate the telemetry.

**AC-002-03**  
The IoT Gateway can publish normalized events to Kafka.

**AC-002-04**  
The Operations Service can consume Kafka events and update device state.

**AC-002-05**  
The React dashboard can display current device health and operational state.

**AC-002-06**  
The Operations Service can calculate outlet-level kiosk utilization.

**AC-002-07**  
A deterministic rule can detect printer degradation.

**AC-002-08**  
A qualifying alert can create an incident.

**AC-002-09**  
An incident can initiate AI investigation.

**AC-002-10**  
The AI can retrieve telemetry and incident evidence through read-only APIs.

**AC-002-11**  
The AI cannot publish MQTT commands or invoke device-control APIs.

**AC-002-12**  
An engineer can approve or reject a recommendation.

**AC-002-13**  
An unapproved recommendation cannot trigger a device-changing command.

**AC-002-14**  
An approved command can reach the target simulator actor through MQTT.

**AC-002-15**  
The device can acknowledge command execution.

**AC-002-16**  
The Operations Service can verify recovery using subsequent telemetry.

**AC-002-17**  
Telemetry ingestion and deterministic alert detection continue when the AIOps Service is unavailable.

**AC-002-18**  
The platform can be started locally without requiring paid cloud infrastructure.

---

## 26. Architecture Decisions to Record

The following Architecture Decision Records (ADRs) SHOULD accompany the specification:

| ADR | Decision |
|---|---|
| ADR-001 | Apache Pekko for external IoT simulation |
| ADR-002 | MQTT as the device communication boundary |
| ADR-003 | Kafka for internal asynchronous events |
| ADR-004 | Spring Boot for backend services |
| ADR-005 | Operations Service as the primary operational domain owner |
| ADR-006 | Spring AI for incident investigation |
| ADR-007 | Strict separation of AI investigation and device control |
| ADR-008 | Human-approved remediation |
| ADR-009 | PostgreSQL for operational persistence |
| ADR-010 | Local-first, low-cost deployment |

---

## 27. Open Design Decisions

The following details remain to be finalized in subsequent specifications:

1. MQTT topic naming, QoS, retained messages, and session behaviour — SPEC-005.
2. Kafka event envelopes, partitions, retries, and consumer idempotency — SPEC-006.
3. Alert thresholds, persistence, deduplication, and incident correlation — SPEC-007.
4. AI investigation orchestration, timeouts, and tool contracts — SPEC-008.
5. Knowledge retrieval and runbook integration — SPEC-009.
6. Command authorization, idempotency, and recovery verification — SPEC-010.
7. Ticket lifecycle and optional external integration — SPEC-011.
8. Frontend screen requirements and user interactions — SPEC-012.
9. Authentication, authorization, and audit controls — SPEC-014.
10. Docker Compose and CI/CD implementation details — SPEC-015.

The MVP SHALL prioritize a functioning end-to-end vertical slice before implementing optional extensions.

---

## 28. Recommended Implementation Sequence

### Phase 1 — IoT Vertical Slice

Implement:

1. Pekko kiosk simulator.
2. Mosquitto broker.
3. Spring Boot IoT Gateway.
4. Kafka event publication.
5. Operations Service device-state processing.
6. PostgreSQL persistence.
7. React device dashboard.

**Target outcome:** A simulated kiosk appears in Comanche with live telemetry and operational state.

### Phase 2 — Detection and Incidents

Implement:

1. Printer degradation scenario.
2. Deterministic detection rules.
3. Alert lifecycle.
4. Incident lifecycle.
5. Incident dashboard.

**Target outcome:** A simulated fault produces an incident with supporting evidence.

### Phase 3 — AI Investigation

Implement:

1. Spring AI integration.
2. Read-only investigation tools.
3. Historical incident/runbook retrieval.
4. Structured investigation report.
5. Remediation recommendation UI.

**Target outcome:** AI investigates an incident and produces an evidence-based recommendation.

### Phase 4 — Human-Approved Remediation

Implement:

1. Engineer approval.
2. Command authorization.
3. MQTT command publication.
4. Simulator command handling.
5. Command acknowledgement.
6. Recovery verification.

**Target outcome:** An engineer can approve remediation and Comanche can verify whether the device recovers.

### Phase 5 — Management Visibility and CI/CD

Complete:

1. Fleet dashboard.
2. Outlet dashboard.
3. Lunch/dinner utilization.
4. Automated tests.
5. GitHub Actions.
6. Demo documentation.
7. Capstone presentation.

---

## 29. Final Architecture Statement

Project Comanche SHALL separate device simulation, IoT messaging, deterministic operations, AI investigation, and human authorization into clearly defined architectural responsibilities.

The AI component SHALL be capable of investigating incidents autonomously through read-only tools, but SHALL NOT possess the ability to modify managed devices.

All device-changing actions SHALL pass through authenticated engineer approval and deterministic command execution.

**Architecture principle:**

> MQTT connects devices to Comanche. Kafka connects internal services. Deterministic software detects abnormalities. AI investigates incidents. Engineers approve remediation. Application services execute authorized commands and verify recovery.
