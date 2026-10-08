# SPEC-001 — Project Comanche Vision and Scope

**Project:** Comanche  
**Description:** AI-Assisted AIOps and Operational Visibility Platform for IoT Devices  
**Reference Organization:** Davy Jones Burger  
**Status:** Draft  
**Version:** 0.4  
**Type:** AI Engineering Capstone Project  
**Priority:** MVP Foundation

---

## 1. Purpose

Project Comanche is an AI-assisted IoT operations platform designed to monitor, manage, and provide operational visibility into fleets of self-ordering kiosks and Point-of-Sale (POS) terminals.

The reference organization is **Davy Jones Burger**, a fictional fast-food restaurant chain operating multiple outlets across Singapore.

Comanche serves two primary audiences:

1. **CIOs and Operations Directors**, who require real-time visibility into device availability, ordering activity, fleet utilization, and operational performance.
2. **IT Operations Engineers**, who require device monitoring, abnormality detection, incident investigation, root-cause analysis, and remediation recommendations.

The platform combines deterministic monitoring, event-driven microservices, and agentic AI investigation.

**AI SHALL investigate incidents and recommend remediation, but SHALL NOT directly control or modify IoT devices.**

All device-changing remediation actions require explicit authorization from an engineer.

---

## 2. Business Context

Davy Jones Burger operates multiple restaurant outlets equipped with self-ordering kiosks and POS terminals.

These devices are critical to daily restaurant operations.

Device failures may result in:

- Reduced customer ordering capacity.
- Longer customer queues.
- Increased reliance on manual ordering.
- Disruption during lunch and dinner peak periods.
- Increased operational support workload.
- Reduced customer satisfaction.

Management needs to understand whether deployed devices are being used effectively and whether outlets have sufficient operational capacity.

IT Operations Engineers need to identify device abnormalities, investigate failures, and restore affected devices efficiently.

---

## 3. Business Objectives

### 3.1 Operational Visibility

Comanche SHALL provide management with fleet-level and outlet-level visibility.

Management SHALL be able to determine:

- Total number of deployed devices.
- Number of online and offline devices.
- Number of healthy, degraded, and failed devices.
- Number of devices out of service.
- Number of kiosks actively serving customers.
- Number of idle kiosks.
- Kiosk utilization by outlet.
- Utilization during lunch and dinner periods.
- Outlets experiencing reduced ordering capacity.
- Active incidents affecting business operations.

### 3.2 Incident Management

Comanche SHALL:

- Continuously ingest device telemetry.
- Track device health and operational state.
- Detect abnormalities using deterministic rules.
- Generate alerts when defined conditions are met.
- Create incidents for conditions requiring investigation.
- Preserve relevant diagnostic evidence.
- Support AI-assisted incident investigation.
- Present remediation recommendations to engineers.
- Require engineer approval before device-changing actions.
- Verify recovery using subsequent device telemetry.

### 3.3 AI-Assisted Investigation

Comanche SHALL use AI to help engineers understand:

- What is wrong with a device.
- When abnormal behaviour began.
- Which measurements indicate a problem.
- Which device logs or events are relevant.
- Whether similar incidents occurred previously.
- Which runbooks may apply.
- What the probable root cause is.
- Which remediation action is recommended.
- What uncertainty or risk accompanies the recommendation.

AI SHALL NOT independently perform remediation.

---

## 4. System Vision

The intended operational lifecycle is:

```text
OBSERVE
   ↓
DETECT
   ↓
ALERT
   ↓
INCIDENT
   ↓
AI INVESTIGATE
   ↓
CORRELATE EVIDENCE
   ↓
DIAGNOSE
   ↓
RECOMMEND
   ↓
ENGINEER REVIEW
   ↓
APPROVE / REJECT / DEFER
   ↓
AUTHORIZED REMEDIATION
   ↓
VERIFY RECOVERY
   ↓
UPDATE INCIDENT / TICKET
```

Detection and incident creation SHALL remain operational even when the AI service is unavailable.

---

## 5. Scope Boundary

Comanche is an IoT/edge-device operations platform.

### 5.1 Supported Device Types

The MVP SHALL support:

- `SELF_ORDER_KIOSK`
- `POS_TERMINAL`

The domain model SHOULD support shared device concepts without becoming a general-purpose infrastructure management framework.

### 5.2 Out of Scope

The MVP SHALL NOT implement monitoring for:

- Enterprise servers.
- Network routers.
- Network switches.
- Cloud infrastructure.
- Industrial machinery.
- SNMP-managed equipment.
- OPC-UA industrial systems.
- General-purpose endpoint management.

These may be considered separate future extensions.

---

## 6. Reference Organization and Sites

The domain hierarchy SHALL be:

```text
Organization
    │
    ├── Site
    │     ├── Device
    │     ├── Device
    │     └── Device
    │
    └── Site
          ├── Device
          └── Device
```

An Organization represents the business operating the device fleet.

A Site represents one restaurant outlet.

A Device represents an individual kiosk or POS terminal.

Example:

```text
Davy Jones Burger
│
├── SITE-TAMPINES
│   ├── KIOSK-TAM-001
│   ├── KIOSK-TAM-002
│   ├── KIOSK-TAM-003
│   └── POS-TAM-001
│
├── SITE-ORCHARD
│   ├── KIOSK-ORC-001
│   ├── KIOSK-ORC-002
│   └── POS-ORC-001
│
└── SITE-JURONG
    ├── KIOSK-JUR-001
    ├── KIOSK-JUR-002
    └── POS-JUR-001
```

Actual sites and device quantities SHALL be configuration-driven.

---

## 7. Device State Model

Comanche SHALL distinguish technical health from business operational activity.

### 7.1 Health State

Candidate values:

- `HEALTHY`
- `DEGRADED`
- `FAILED`
- `OFFLINE`

Health state represents the technical condition of a device.

### 7.2 Operational State

Candidate values:

- `IDLE`
- `TAKING_ORDER`
- `PROCESSING_PAYMENT`
- `COMPLETING_ORDER`
- `OUT_OF_SERVICE`

Operational state represents what the device is doing.

A device MAY be healthy but idle.

A device MAY be degraded while continuing to serve customers.

A device MAY be out of service because of a technical failure or an intentional operational decision.

### 7.3 Connectivity State

Connectivity SHALL be tracked independently where necessary.

Candidate values:

- `CONNECTED`
- `DISCONNECTED`
- `UNKNOWN`

Detailed state definitions and allowed transitions SHALL be specified in SPEC-003.

---

## 8. Management Dashboard

Comanche SHALL provide a web-based operations dashboard.

### 8.1 Fleet-Level Dashboard

The dashboard SHALL present:

- Total devices.
- Online devices.
- Offline devices.
- Healthy devices.
- Degraded devices.
- Failed devices.
- Out-of-service devices.
- Devices actively processing orders.
- Idle devices.
- Active incidents.
- Fleet availability.

### 8.2 Outlet-Level Dashboard

Each outlet SHALL display:

- Total kiosks.
- Available kiosks.
- Kiosks taking orders.
- Idle kiosks.
- Out-of-service kiosks.
- POS terminal availability.
- Current utilization.
- Active incidents.

### 8.3 Peak-Hour Monitoring

Comanche SHALL support configurable operating periods, including:

- Lunch peak.
- Dinner peak.
- Off-peak periods.

Management SHALL be able to compare utilization across outlets and time periods.

### 8.4 Utilization

Kiosk utilization SHALL be derived from time spent actively serving customers relative to eligible available operating time.

The detailed calculation, including denominator rules and treatment of out-of-service periods, SHALL be defined in SPEC-003 or a dedicated metrics specification.

Low utilization SHALL NOT automatically be treated as a technical device failure.

---

## 9. IoT Device Simulation

The MVP SHALL use **Apache Pekko** to simulate a fleet of kiosks and POS terminals.

Pekko SHALL be used exclusively as the device simulation runtime.

The simulator SHALL operate independently of Comanche's production services.

### 9.1 Device Actors

Each simulated kiosk or POS terminal SHALL be represented by an independently operating actor.

Each actor SHALL maintain its own:

- Device identity.
- Technical state.
- Operational state.
- Connectivity state.
- Application state.
- Peripheral state.
- Simulated workload.
- Fault scenario state.

### 9.2 Normal Business Activity

The simulator SHALL generate realistic operational behaviour.

Example kiosk lifecycle:

```text
IDLE
  ↓
TAKING_ORDER
  ↓
PROCESSING_PAYMENT
  ↓
COMPLETING_ORDER
  ↓
IDLE
```

The simulator SHOULD support configurable customer activity profiles for lunch, dinner, and off-peak periods.

### 9.3 Fault Simulation

The simulator SHALL support controlled fault injection.

Initial candidate scenarios:

- Printer overheating.
- Printer error-rate increase.
- Paper depletion.
- Application failure.
- Network disconnection.
- Device becoming unavailable.

Faults SHOULD be capable of progressing over time rather than immediately switching a device to FAILED.

### 9.4 Determinism

The simulator SHOULD support reproducible scenarios through configurable random seeds and controlled simulation parameters.

---

## 10. Simulator Isolation Requirements

**REQ-SIM-001**  
Pekko SHALL be used only for device simulation.

**REQ-SIM-002**  
Comanche platform services SHALL NOT depend on Pekko at compile time.

**REQ-SIM-003**  
Comanche platform services SHALL NOT require the simulator to be running for normal platform startup.

**REQ-SIM-004**  
Simulated devices SHALL communicate with Comanche through MQTT.

**REQ-SIM-005**  
Simulator actors SHALL NOT directly invoke internal Spring Boot service methods.

**REQ-SIM-006**  
Simulation-control events such as `InjectFault` SHALL remain internal to the simulator.

**REQ-SIM-007**  
Comanche SHALL receive device telemetry and operational events without needing to know whether they originate from simulated or physical devices.

---

## 11. MQTT Device Communication

MQTT SHALL be the external communication boundary between IoT devices and Comanche.

Devices SHALL use MQTT to exchange:

- Telemetry.
- Device status.
- Operational events.
- Device events.
- Approved commands.
- Command acknowledgements.

An MQTT broker SHALL mediate publish/subscribe communication.

Eclipse Mosquitto is the preferred MVP broker.

Detailed topic naming, payload contracts, QoS, reconnect behaviour, and command acknowledgement semantics SHALL be defined in SPEC-005.

---

## 12. Kafka Event Backbone

Apache Kafka SHALL provide asynchronous event integration between Comanche backend components.

The primary ingestion flow SHALL be:

```text
IoT Device
    ↓
MQTT Broker
    ↓
IoT Gateway
    ↓
Kafka
    ↓
Operations Service
    ↓
Device State / Alerts / Incidents
```

Devices SHALL NOT communicate directly with Kafka.

Kafka SHALL NOT replace MQTT as the device-facing protocol.

Detailed event schemas, topic conventions, and delivery semantics SHALL be defined in SPEC-006.

---

## 13. Abnormality Detection

Comanche SHALL use deterministic software to detect device abnormalities.

The detection engine SHALL NOT require an LLM.

### 13.1 Detection Methods

The MVP SHALL support:

- Threshold-based detection.
- Consecutive-reading or time-duration persistence.
- Device connectivity timeout detection.
- Correlation of multiple telemetry signals.

Trend detection SHOULD be supported where practical.

Historical-baseline or ML-based anomaly detection is not required for the MVP.

### 13.2 Candidate Detection Rules

Initial candidate abnormalities include:

- Printer temperature too high.
- Printer error rate too high.
- Printer temperature and error rate simultaneously elevated.
- Printer paper level critically low.
- Application stopped unexpectedly.
- Network disconnected.
- Device telemetry missing beyond an allowed interval.

Thresholds and persistence periods SHALL be configurable.

### 13.3 Noise Reduction

The detection engine SHALL avoid creating a new alert for every abnormal telemetry sample.

It SHALL support appropriate alert deduplication and state transitions.

### 13.4 Operational Insights

Unusual kiosk utilization during lunch or dinner MAY produce an operational insight.

Operational insights SHALL be distinguished from technical device alerts.

Detailed detection behaviour SHALL be specified in SPEC-007.

---

## 14. Alerts and Incidents

An Alert represents a detected abnormal condition.

An Incident represents an operational issue requiring investigation or action.

Comanche SHALL support:

- Alert creation.
- Alert state tracking.
- Alert deduplication.
- Incident creation.
- Incident evidence collection.
- Incident status tracking.
- Incident resolution.

Multiple related alerts MAY contribute to one incident.

The platform SHALL preserve enough diagnostic evidence for AI investigation and engineer review.

---

## 15. AI-Assisted Incident Investigation

Comanche SHALL provide an AIOps investigation capability using Spring AI.

The AI agent MAY independently determine which permitted read-only tools to use during an investigation.

Candidate tools include:

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

The agent MAY:

- Gather evidence.
- Correlate telemetry.
- Identify patterns.
- Search operational knowledge.
- Produce a probable diagnosis.
- Explain uncertainty.
- Recommend remediation.

The AI SHALL NOT be responsible for routine telemetry threshold evaluation.

---

## 16. AI Provider Support

The AI implementation SHALL use Spring AI where practical.

The preferred hosted AI provider is OpenAI.

Local development SHOULD support Ollama where feasible.

The AI model and provider SHALL be configurable.

The domain model SHALL NOT depend on a specific LLM provider or model identifier.

Normal telemetry ingestion and alert detection SHALL continue when the AI provider is unavailable.

---

## 17. AI Security Boundary

The AIOps agent SHALL be restricted to investigation and recommendation.

The agent SHALL NOT have access to:

- MQTT command-publishing credentials.
- Device write APIs.
- Device administration credentials.
- SSH access to managed devices.
- Arbitrary shell execution on managed devices.
- Device configuration modification APIs.
- Device restart tools.
- Direct remediation execution tools.

The AIOps agent SHALL NOT directly publish commands to IoT devices.

This restriction SHALL be enforced through application architecture and access permissions, not merely through LLM prompt instructions.

**Governing principle: AI investigates and recommends. Engineers decide. Deterministic software executes.**

---

## 18. Human-Approved Remediation

AI-generated remediation recommendations SHALL be presented to an authorized engineer.

The engineer SHALL be able to:

- Approve.
- Reject.
- Defer.

Approval SHALL be explicitly recorded.

The deterministic application SHALL validate the approved action before execution.

The command path SHALL be:

```text
AI Recommendation
        ↓
Engineer Review
        ↓
Engineer Approval
        ↓
Operations Service
        ↓
Command Authorization
        ↓
IoT Gateway
        ↓
MQTT Broker
        ↓
Device
```

The AI SHALL NOT bypass this path.

---

## 19. Recovery Verification

Successful command delivery SHALL NOT automatically imply successful remediation.

Comanche SHALL evaluate subsequent telemetry and device state before declaring recovery.

Candidate recovery outcomes:

- `RECOVERED`
- `NOT_RECOVERED`
- `WORSENED`
- `INCONCLUSIVE`

Recovery verification SHALL be deterministic.

---

## 20. Service Ticket Management

Comanche SHALL support service tickets associated with incidents.

A service ticket SHOULD contain:

- Ticket identifier.
- Incident identifier.
- Device and site.
- Severity.
- Symptoms.
- Relevant telemetry.
- Relevant logs.
- Probable root cause.
- Supporting evidence.
- AI recommendation.
- Engineer decision.
- Remediation attempts.
- Recovery outcome.

The MVP MAY use internal ticket storage.

External Jira integration is optional and SHALL NOT be required for completing the capstone.

---

## 21. Primary End-to-End Demonstration

The primary demonstration SHALL use progressive kiosk printer degradation.

### Step 1 — Normal Operation

A simulated kiosk is healthy and accepting orders.

### Step 2 — Fault Injection

The simulator activates a printer degradation scenario.

### Step 3 — Telemetry Deterioration

Example printer temperature:

```text
51°C → 58°C → 65°C → 72°C
```

Example printer error rate:

```text
1% → 4% → 10% → 19%
```

### Step 4 — Deterministic Detection

Comanche detects the abnormality using configured rules and persistence conditions.

### Step 5 — Incident Creation

An incident is created with supporting evidence.

### Step 6 — AI Investigation

The AI retrieves telemetry, logs, and relevant operational knowledge.

### Step 7 — Recommendation

The AI produces a probable diagnosis and recommends a printer subsystem restart.

### Step 8 — Engineer Review

The engineer examines the evidence and approves or rejects the recommendation.

### Step 9 — Remediation

Following approval, deterministic software publishes the authorized command through MQTT.

### Step 10 — Recovery

The simulated device responds to the command and produces updated telemetry.

### Step 11 — Verification

Comanche evaluates subsequent telemetry and determines whether recovery occurred.

### Step 12 — Closure

The incident and service ticket are updated with the outcome.

---

## 22. MVP Components

The expected MVP components are:

| Component | Technology | Responsibility |
|---|---|---|
| Device Simulator | Java, Apache Pekko | Simulated kiosks/POS |
| MQTT Broker | Eclipse Mosquitto | Device messaging |
| IoT Gateway | Spring Boot | MQTT/Kafka integration |
| Event Backbone | Apache Kafka | Internal events |
| Operations Service | Spring Boot | Devices, metrics, alerts, incidents, commands |
| Database | PostgreSQL | Operational persistence |
| AIOps Service | Spring AI | Investigation and recommendations |
| Web Console | React, TypeScript | Management and engineering UI |
| Local Deployment | Docker Compose | Development environment |
| CI/CD | GitHub Actions | Build and test automation |

The architecture SHOULD avoid unnecessary additional microservices.

---

## 23. Resource Constraints

Comanche is an educational capstone project.

The implementation SHALL prioritize:

- Open-source technologies.
- Local development.
- Free or low-cost services.
- Minimal infrastructure overhead.
- Reproducible setup.
- Automated testing.
- Reliable demonstration.

Normal development SHALL NOT depend on paid cloud infrastructure.

AI API usage SHOULD be configurable and limited to control costs.

---

## 24. Target Simulation Scale

The project SHOULD support:

| Environment | Target Devices |
|---|---:|
| Basic development | 5–10 |
| Capstone demonstration | 10–20 |
| Normal simulation | 50–100 |
| Optional scalability experiment | Approximately 1,000 |

These are engineering targets, not production capacity guarantees.

---

## 25. Explicit Non-Goals

The MVP does NOT require:

- Kubernetes deployment.
- Production-grade high availability.
- Multi-region infrastructure.
- Industrial equipment integration.
- SNMP monitoring.
- Complex predictive maintenance models.
- Machine-learning anomaly detection.
- Autonomous AI remediation.
- Unrestricted device administration.
- Real payment processing.
- Real customer personal information.
- Full restaurant order management.
- Real Jira integration.
- Production-scale fleet deployment.

These SHALL NOT block MVP completion.

---

## 26. Specification-Driven Development

Development SHALL follow:

```text
Business Requirement
        ↓
Specification
        ↓
Architecture Decision
        ↓
Implementation
        ↓
Automated Tests
        ↓
Acceptance Verification
```

Codex or another coding agent MAY assist with implementation.

The approved specifications SHALL remain authoritative.

When requirements are ambiguous, the specification SHOULD be clarified before implementation.

---

## 27. Acceptance Criteria

**AC-001-01**  
The simulator can create multiple independently operating kiosks and POS terminals.

**AC-001-02**  
Simulator telemetry enters Comanche through MQTT.

**AC-001-03**  
Comanche platform services have no runtime dependency on Pekko.

**AC-001-04**  
Management can view device availability by outlet.

**AC-001-05**  
Management can distinguish kiosks taking orders, idle, and out of service.

**AC-001-06**  
Management can view utilization during configured lunch and dinner periods.

**AC-001-07**  
Progressive printer degradation generates a deterministic alert.

**AC-001-08**  
Repeated abnormal telemetry does not create uncontrolled duplicate alerts.

**AC-001-09**  
A qualifying abnormal condition can generate an incident.

**AC-001-10**  
The AI retrieves operational evidence before producing a diagnosis.

**AC-001-11**  
The AI cannot directly modify or control a device.

**AC-001-12**  
A device-changing command cannot execute without authorized engineer approval.

**AC-001-13**  
Comanche verifies recovery from subsequent device telemetry.

**AC-001-14**  
Core monitoring continues when the AI service is unavailable.

**AC-001-15**  
The MVP can run locally without mandatory paid cloud infrastructure.

**AC-001-16**  
The end-to-end printer degradation scenario can be demonstrated reliably.

---

## 28. Governing Principles

1. Deliver a complete, demonstrable capstone.
2. Solve a meaningful business operations problem.
3. Provide both management and engineering visibility.
4. Keep device health separate from operational activity.
5. Use Pekko only for simulation.
6. Use MQTT for device communication.
7. Use Kafka for internal asynchronous integration.
8. Use deterministic rules for abnormality detection.
9. Use AI for investigation, correlation, and reasoning.
10. Keep AI access read-only with respect to managed devices.
11. Require explicit engineer approval for device-changing actions.
12. Use deterministic application software to execute authorized actions.
13. Verify remediation through observed telemetry.
14. Prefer open-source, local, and low-cost technologies.
15. Avoid speculative abstractions and unnecessary services.
16. Prioritize reliable end-to-end functionality over feature count.

---

## 29. Success Criteria

Project Comanche is considered successful when it can demonstrate:

- A simulated fleet of restaurant kiosks and POS terminals.
- Live operational and technical monitoring.
- Outlet-level fleet utilization and availability.
- Deterministic abnormality detection.
- Incident creation and evidence collection.
- AI-assisted root-cause investigation.
- A remediation recommendation with supporting evidence.
- Engineer-controlled approval.
- Authorized device remediation through MQTT.
- Independent verification of recovery.

**Final Architectural Statement**

> Comanche monitors the devices. Pekko simulates the devices. Deterministic rules detect abnormalities. AI investigates incidents and recommends remedies. Engineers authorize changes. Deterministic application software executes approved actions and verifies recovery.
