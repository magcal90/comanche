# SPEC-007 — Device Alert and Incident Management

**Project:** Comanche  
**Description:** AI-Assisted AIOps and Operational Visibility Platform for IoT Devices  
**Reference Organization:** Davy Jones Burger  
**Status:** Draft  
**Version:** 0.2  
**Type:** Functional and Domain Behaviour Specification  
**Depends On:** SPEC-001 through SPEC-006  
**Related Specifications:** SPEC-008 (AI Investigation), SPEC-010 (Remediation), SPEC-011 (Service Tickets)

---

## 1. Purpose

This specification defines the deterministic abnormality detection, alert management, incident correlation, and incident lifecycle capabilities of Project Comanche.

It establishes:

- Telemetry and device-event evaluation.
- Configurable detection rules.
- Threshold, persistence, and correlation logic.
- Connectivity monitoring.
- Alert creation and deduplication.
- Alert severity classification.
- Incident creation and grouping.
- Incident lifecycle transitions.
- AI investigation triggers.
- Human review and remediation integration.
- Recovery verification requirements.
- Auditability, reliability, and acceptance criteria.

The system SHALL detect technical abnormalities and create incidents without depending on a Large Language Model (LLM).

AI SHALL assist with investigation and recommendations, not determine whether basic monitoring rules have been violated.

---

## 2. Scope

### 2.1 In Scope

This specification covers:

1. Deterministic evaluation of device telemetry.
2. Technical device-event processing.
3. Connectivity timeout detection.
4. Alert generation and lifecycle.
5. Alert deduplication.
6. Multi-signal correlation.
7. Incident creation.
8. Incident grouping.
9. Incident severity.
10. Incident lifecycle management.
11. AI investigation initiation.
12. Remediation workflow integration.
13. Recovery verification integration.
14. Alert and incident event publication.
15. Operational audit records.

### 2.2 Out of Scope

The following are defined elsewhere:

- MQTT message contracts — SPEC-005.
- Kafka transport contracts — SPEC-006.
- AI agent implementation — SPEC-008.
- Knowledge retrieval and runbooks — SPEC-009.
- Remediation command execution — SPEC-010.
- Service-ticket implementation — SPEC-011.

The MVP SHALL NOT require predictive-maintenance machine learning or AI-based anomaly detection.

---

## 3. Architectural Responsibilities

The Operations Service SHALL own abnormality detection, alerts, and incidents.

```text
Pekko Device Simulator
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
+---------------------------+
| Operations Service        |
|                           |
| Telemetry Processor       |
| Device State Manager      |
| Detection Engine          |
| Alert Manager             |
| Incident Manager          |
| Incident Event Publisher  |
+-------------+-------------+
              |
              +----> PostgreSQL
              |
              +----> Kafka Incident Events
                            |
                            v
                       AIOps Service
```

### 3.1 Separation of Responsibilities

**Device Simulator**

Produces device behaviour, telemetry, and technical events.

**IoT Gateway**

Validates and forwards device messages.

**Detection Engine**

Evaluates deterministic rules against accepted observations.

**Alert Manager**

Creates, updates, acknowledges, and resolves alerts.

**Incident Manager**

Groups related alerts into actionable incidents and manages incident lifecycle.

**AIOps Service**

Investigates incidents and recommends actions using read-only evidence retrieval.

**Remediation Subsystem**

Executes engineer-authorized actions and verifies outcomes.

---

## 4. Core Concepts

### 4.1 Observation

An Observation is an accepted telemetry measurement, status report, or device event.

An observation is evidence, not necessarily an abnormality.

### 4.2 Detection Rule

A DetectionRule is a deterministic definition of an abnormal condition.

Examples:

- Printer temperature exceeds 70°C.
- Printer error rate exceeds 10%.
- Application reports STOPPED.
- Device heartbeat is missing for more than 60 seconds.

### 4.3 Detection Condition

A DetectionCondition represents the evaluated state of a rule for a particular device.

A condition MAY be:

- NORMAL.
- PENDING.
- FIRING.
- CLEARING.

### 4.4 Alert

An Alert represents a confirmed abnormal condition produced by a detection rule.

An alert SHALL contain supporting evidence.

### 4.5 Incident

An Incident represents an operational issue requiring investigation, intervention, or tracking.

An incident MAY contain multiple related alerts.

### 4.6 Correlation

Correlation associates related observations and alerts with a common operational problem.

Correlation SHALL be deterministic in the MVP.

---

## 5. Detection Engine

The Detection Engine SHALL operate within the Operations Service.

It SHALL consume accepted device observations and evaluate applicable rules.

### 5.1 Processing Flow

```text
Accepted Device Observation
            |
            v
    Validate Device State
            |
            v
    Select Applicable Rules
            |
            v
     Evaluate Conditions
            |
            v
     Update Rule State
            |
       +----+----+
       |         |
     NORMAL    FIRING
       |         |
       v         v
   No Alert   Create/Update Alert
                    |
                    v
             Incident Correlation
                    |
                    v
             Create/Update Incident
```

### 5.2 Detection Requirements

**REQ-DET-001**  
The Detection Engine SHALL use deterministic evaluation rules.

**REQ-DET-002**  
The Detection Engine SHALL NOT require AI inference.

**REQ-DET-003**  
Rules SHALL be configurable.

**REQ-DET-004**  
Rules SHALL support device-type applicability.

**REQ-DET-005**  
Rules SHALL preserve evidence supporting abnormality detection.

**REQ-DET-006**  
Duplicate observations SHALL NOT advance rule persistence counters more than once.

**REQ-DET-007**  
Stale observations SHALL NOT incorrectly change current detection state.

---

## 6. Rule Types

The MVP SHALL support the following rule types.

### 6.1 Threshold Rule

Evaluates whether a measurement crosses a configured threshold.

Example:

`printer.temperature >= 70.0`

### 6.2 Consecutive Sample Rule

Requires a condition to hold for a configured number of consecutive accepted observations.

Example:

`printer.temperature >= 70.0 for 3 consecutive readings`

### 6.3 Duration Rule

Requires a condition to persist for a configured duration.

Example:

`network disconnected for more than 30 seconds`

### 6.4 Connectivity Timeout Rule

Detects missing device communication.

Example:

`lastSeenAt older than 60 seconds`

### 6.5 Multi-Signal Correlation Rule

Evaluates related abnormal conditions within a defined time window.

Example:

`high printer temperature AND high printer error rate within 5 minutes`

### 6.6 Trend Rule

Evaluates a deterministic change in measurements over time.

Example:

`printer temperature increases by at least 15°C within 5 minutes`

Trend rules MAY be introduced after threshold and persistence rules are operational.

---

## 7. Initial Detection Rule Catalog

The following values are proposed MVP defaults, not production-certified hardware limits.

| Rule ID | Condition | Persistence | Severity |
|---|---|---|---|
| `PRINTER_HIGH_TEMPERATURE` | Temperature ≥ 70°C | 3 consecutive samples | HIGH |
| `PRINTER_HIGH_ERROR_RATE` | Error rate > 0.10 | 3 consecutive samples | HIGH |
| `PRINTER_TEMPERATURE_RISING` | Increase ≥ 15°C | Within 5 minutes | WARNING |
| `PRINTER_PAPER_LOW` | Paper level ≤ 10% | 1 sample | WARNING |
| `APPLICATION_STOPPED` | Application STOPPED | 2 consecutive samples | CRITICAL |
| `DEVICE_HEARTBEAT_MISSING` | No valid heartbeat/communication | > 60 seconds | CRITICAL |
| `NETWORK_DISCONNECTED` | Trusted disconnect condition | > 30 seconds | HIGH |
| `PRINTER_COMBINED_DEGRADATION` | Temperature and error-rate alerts active | Within 5 minutes | CRITICAL |

All thresholds and persistence values SHALL be configurable.

### 7.1 Important Distinction

The primary printer demonstration in SPEC-004 uses a progression such as:

```text
Temperature:
51 → 58 → 65 → 72°C

Error Rate:
1 → 4 → 10 → 19%
```

This progression alone does not satisfy a three-consecutive-sample threshold rule.

The simulator SHALL continue publishing abnormal readings long enough for the configured persistence conditions to be satisfied.

For example:

```text
72°C → 73°C → 74°C
```

This SHALL trigger the high-temperature rule when all three readings are valid and consecutive.

---

## 8. Rule Configuration

Rules SHOULD be defined through configuration or persisted rule definitions.

### 8.1 Example Configuration

```yaml
detection:
  rules:
    - rule-id: PRINTER_HIGH_TEMPERATURE
      enabled: true
      device-types:
        - SELF_ORDER_KIOSK
        - POS_TERMINAL
      metric: printer.temperature
      operator: GREATER_THAN_OR_EQUAL
      threshold: 70.0
      consecutive-samples: 3
      severity: HIGH
      clear-threshold: 60.0
      clear-consecutive-samples: 3

    - rule-id: PRINTER_HIGH_ERROR_RATE
      enabled: true
      device-types:
        - SELF_ORDER_KIOSK
        - POS_TERMINAL
      metric: printer.errorRate
      operator: GREATER_THAN
      threshold: 0.10
      consecutive-samples: 3
      severity: HIGH
```

### 8.2 Configuration Requirements

**REQ-DET-008**  
Each rule SHALL have a unique ruleId.

**REQ-DET-009**  
Rules SHALL define applicable device types.

**REQ-DET-010**  
Threshold values SHALL use the metric units defined in SPEC-003.

**REQ-DET-011**  
Invalid rule configurations SHALL be rejected during configuration loading.

**REQ-DET-012**  
The MVP MAY require service restart to activate configuration changes.

Dynamic rule editing is not required.

---

## 9. Detection State Machine

Each device-rule pair SHALL maintain its own evaluation state.

```text
              +----------+
              |  NORMAL  |
              +----+-----+
                   |
           Condition violated
                   |
                   v
              +----------+
              | PENDING  |
              +----+-----+
                   |
           Persistence met
                   |
                   v
              +----------+
              |  FIRING  |
              +----+-----+
                   |
          Clear condition met
                   |
                   v
              +----------+
              | CLEARING |
              +----+-----+
                   |
        Clear persistence met
                   |
                   v
              +----------+
              |  NORMAL  |
              +----------+
```

### 9.1 NORMAL

No active abnormal condition exists.

### 9.2 PENDING

The abnormal condition has been observed but has not yet met its persistence requirement.

### 9.3 FIRING

The abnormal condition has met its persistence requirement.

An alert SHALL be active.

### 9.4 CLEARING

Recovery measurements are present, but the configured clearing persistence has not yet been satisfied.

### 9.5 State Transitions

If an abnormal condition disappears before persistence is satisfied, PENDING SHALL return to NORMAL.

If an abnormal condition returns during CLEARING, the condition SHALL return to FIRING.

A rule SHALL NOT repeatedly create new alerts while remaining in FIRING.

---

## 10. Hysteresis and Alert Flapping

The Detection Engine SHALL support separate activation and clearing conditions.

### 10.1 Example

Printer high-temperature rule:

```text
Activate:
Temperature >= 70°C
for 3 consecutive samples

Clear:
Temperature < 60°C
for 3 consecutive samples
```

This prevents repeated alert creation when temperature fluctuates near 70°C.

### 10.2 Requirements

**REQ-DET-013**  
Rules supporting recovery thresholds SHALL use hysteresis where appropriate.

**REQ-DET-014**  
Clearing persistence SHALL be independently configurable.

**REQ-DET-015**  
An alert SHALL remain active while its underlying rule remains FIRING or CLEARING.

---

## 11. Connectivity Detection

Connectivity monitoring SHALL operate independently of device-reported technical health.

### 11.1 Device Communication

The Operations Service SHALL maintain a lastSeenAt timestamp for accepted device communication.

### 11.2 Missing Heartbeat

The MVP default SHALL be:

`Current time - lastSeenAt > 60 seconds`

When this condition is satisfied, the device SHALL be classified as DISCONNECTED and its effective health SHALL become OFFLINE.

### 11.3 Initial Device State

A newly registered device that has never communicated SHALL initially have connectivity UNKNOWN.

A configurable registration grace period MAY be applied before raising an offline alert.

### 11.4 Reconnection

When valid device communication resumes:

1. Connectivity SHALL become CONNECTED.
2. Effective health SHALL be recalculated.
3. Underlying technical alerts SHALL remain until their clearing conditions are satisfied.
4. The missing-heartbeat alert MAY clear according to its recovery rule.

### 11.5 Trusted Disconnect Events

A broker Last Will message MAY provide an early disconnect indication.

The Operations Service SHALL still apply its configured connectivity rules.

### 11.6 Requirements

**REQ-DET-016**  
Connectivity timeouts SHALL be evaluated by a scheduled deterministic process.

**REQ-DET-017**  
Connectivity detection SHALL NOT depend solely on incoming telemetry.

**REQ-DET-018**  
An OFFLINE device SHALL NOT be counted as actively serving customers based on stale operational state.

**REQ-DET-019**  
Reconnection SHALL NOT automatically clear unrelated technical alerts.

---

## 12. Multi-Signal Correlation

The Detection Engine SHALL support correlation of related technical abnormalities.

### 12.1 Primary Printer Correlation

The MVP SHALL correlate:

- `PRINTER_HIGH_TEMPERATURE`
- `PRINTER_HIGH_ERROR_RATE`

when both conditions are active within the configured five-minute correlation window.

### 12.2 Correlation Outcome

The correlated condition SHALL indicate a likely printer subsystem problem.

It MAY:

- Raise a CRITICAL correlated alert.
- Increase incident severity.
- Add supporting evidence to an existing incident.

It SHALL NOT create an independent duplicate incident when an appropriate unresolved printer incident already exists.

### 12.3 Correlation Requirements

**REQ-DET-020**  
Correlation SHALL use deterministic rules.

**REQ-DET-021**  
Correlated alerts SHALL reference their contributing observations or alerts.

**REQ-DET-022**  
Correlation SHALL NOT require LLM inference.

**REQ-DET-023**  
A correlated condition SHALL NOT create uncontrolled duplicate incidents.

---

## 13. Alert Domain Model

An Alert represents a confirmed detection condition.

### 13.1 Attributes

| Attribute | Description |
|---|---|
| alertId | Unique alert identifier |
| deviceId | Affected device |
| ruleId | Triggering rule |
| severity | Alert severity |
| status | Alert lifecycle state |
| firstDetectedAt | Initial detection time |
| lastDetectedAt | Most recent abnormal observation |
| acknowledgedBy | Engineer identity, if acknowledged |
| acknowledgedAt | Acknowledgement timestamp |
| resolvedAt | Resolution timestamp |
| evidence | Supporting observations |
| incidentId | Associated incident, if any |

### 13.2 Alert Severity

The MVP SHALL use:

```java
public enum AlertSeverity {
    INFO,
    WARNING,
    HIGH,
    CRITICAL
}
```

### 13.3 Alert Status

```java
public enum AlertStatus {
    ACTIVE,
    ACKNOWLEDGED,
    RESOLVED
}
```

### 13.4 Alert Lifecycle

```text
ACTIVE
   |
   +----> ACKNOWLEDGED
   |            |
   |            v
   +--------> RESOLVED
```

An alert MAY resolve directly from ACTIVE when its clearing criteria are met.

Acknowledgement SHALL NOT clear the underlying technical condition.

---

## 14. Alert Deduplication

The system SHALL prevent repeated alert creation for the same continuing abnormality.

### 14.1 Logical Alert Identity

For the MVP, the logical active-alert key SHOULD be:

`deviceId + ruleId`

Only one unresolved alert SHALL exist for a given active device-rule condition.

### 14.2 Repeated Abnormal Observations

While an alert remains active:

- lastDetectedAt SHALL be updated.
- Relevant evidence MAY be appended.
- Occurrence count MAY be incremented.
- A new alert SHALL NOT be created.

### 14.3 Reoccurrence After Resolution

If the rule clears and later fires again, the system MAY create a new alert.

The new alert SHALL retain its own identity and timestamps.

### 14.4 Database Protection

The persistence layer SHOULD enforce the unresolved-alert uniqueness invariant using an appropriate database constraint or transactional locking strategy.

### 14.5 Requirements

**REQ-ALERT-001**  
An active condition SHALL not generate an alert per telemetry sample.

**REQ-ALERT-002**  
Duplicate Kafka delivery SHALL not create duplicate alerts.

**REQ-ALERT-003**  
Repeated abnormal observations SHALL update the existing alert.

**REQ-ALERT-004**  
A cleared condition that later reoccurs MAY create a new alert.

---

## 15. Alert Evidence

Alerts SHALL preserve evidence sufficient to explain why a rule fired.

### 15.1 Evidence Content

Candidate evidence:

- Metric name.
- Observed value.
- Threshold.
- Observation timestamp.
- Persistence count.
- Device event reference.
- Related alert reference.

### 15.2 Example

```json
{
  "alertId": "ALERT-30001",
  "deviceId": "KIOSK-TAM-003",
  "ruleId": "PRINTER_HIGH_TEMPERATURE",
  "severity": "HIGH",
  "evidence": {
    "metric": "printer.temperature",
    "threshold": 70.0,
    "operator": "GREATER_THAN_OR_EQUAL",
    "consecutiveSamples": 3,
    "observedValues": [72.0, 73.0, 74.0]
  }
}
```

### 15.3 Evidence Retention

The system SHOULD preserve references to source telemetry rather than duplicating large volumes of telemetry in every alert.

A bounded evidence snapshot MAY be stored for convenient incident review.

---

## 16. Incident Creation

An Incident SHALL be created when one or more qualifying alerts represent an actionable operational issue.

### 16.1 Incident Qualification

The MVP SHOULD create incidents for:

- HIGH alerts.
- CRITICAL alerts.
- Configured correlated alert groups.

WARNING alerts MAY remain standalone unless escalation rules are met.

### 16.2 Incident Creation Flow

```text
Alert FIRING
     |
     v
Evaluate Incident Policy
     |
     v
Find Related Open Incident
     |
  +--+--+
  |     |
Found  Not Found
  |     |
  v     v
Update  Create
Incident Incident
```

### 16.3 Requirements

**REQ-INC-001**  
Incident creation SHALL follow deterministic qualification rules.

**REQ-INC-002**  
An Incident SHALL reference its affected device.

**REQ-INC-003**  
An Incident SHALL preserve associated alerts.

**REQ-INC-004**  
Incident creation SHALL NOT depend on AI availability.

---

## 17. Incident Correlation and Grouping

The MVP SHALL support device-level incident grouping.

### 17.1 Grouping Key

The preferred grouping key is:

`deviceId + incidentCategory`

Candidate incident categories:

```java
public enum IncidentCategory {
    PRINTER,
    APPLICATION,
    CONNECTIVITY,
    PAYMENT_TERMINAL,
    GENERAL_DEVICE
}
```

### 17.2 Example

The following alerts:

```text
Device: KIOSK-TAM-003

PRINTER_HIGH_TEMPERATURE
PRINTER_HIGH_ERROR_RATE
PRINTER_COMBINED_DEGRADATION
```

SHALL be eligible for grouping into one printer-related incident.

### 17.3 Grouping Constraints

Alerts from unrelated devices SHALL NOT automatically be merged into one incident.

Cross-device or site-wide incident correlation is outside the initial MVP.

### 17.4 Incident Reuse

When a qualifying alert occurs, the Incident Manager SHALL search for an unresolved incident with the same grouping key.

If one exists, the alert SHALL be associated with that incident.

Otherwise, a new incident SHALL be created.

### 17.5 Concurrency

Incident creation SHALL be protected against concurrent processing that could create duplicate unresolved incidents.

---

## 18. Incident Severity

Incident severity SHALL reflect the highest relevant active severity and configured correlation policy.

### 18.1 Candidate Severity

```java
public enum IncidentSeverity {
    WARNING,
    HIGH,
    CRITICAL
}
```

### 18.2 Severity Escalation

An incident MAY escalate when:

- A new CRITICAL alert is associated.
- Multiple correlated HIGH alerts satisfy a CRITICAL correlation rule.
- A technical failure prevents customer operation.

### 18.3 Severity Reduction

Incident severity SHALL NOT be silently reduced merely because a new lower-severity observation arrives.

Severity reduction SHOULD occur through explicit deterministic recovery or reassessment rules.

---

## 19. Incident Domain Model

### 19.1 Attributes

| Attribute | Description |
|---|---|
| incidentId | Unique identifier |
| deviceId | Affected device |
| siteId | Associated site |
| category | Incident category |
| severity | Current severity |
| status | Incident lifecycle status |
| title | Human-readable summary |
| description | Incident description |
| openedAt | Creation timestamp |
| updatedAt | Last update |
| resolvedAt | Recovery confirmation timestamp |
| closedAt | Closure timestamp |
| alertIds | Associated alerts |
| investigationId | Latest investigation, if any |
| recommendationIds | Associated recommendations |
| commandIds | Remediation attempts |
| recoveryStatus | Verification outcome |

### 19.2 Incident Status

The MVP SHALL use the following canonical lifecycle states:

```java
public enum IncidentStatus {
    OPEN,
    INVESTIGATING,
    AWAITING_APPROVAL,
    REMEDIATING,
    VERIFYING_RECOVERY,
    RESOLVED,
    CLOSED
}
```

The canonical state names SHALL remain consistent with SPEC-003.

---

## 20. Incident Lifecycle

```text
             OPEN
               |
               v
         INVESTIGATING
               |
               v
       AWAITING_APPROVAL
          /    |     \
      Reject  Defer  Approve
        |       |       |
        v       v       v
       OPEN    HOLD  REMEDIATING
                        |
                        v
                VERIFYING_RECOVERY
                    /       \
                   /         \
             RECOVERED     NOT RECOVERED
                 |              |
                 v              v
              RESOLVED       OPEN /
                 |           INVESTIGATING
                 v
               CLOSED
```

### 20.1 OPEN

The incident has been created and requires attention.

### 20.2 INVESTIGATING

Evidence collection or AI/manual investigation is underway.

### 20.3 AWAITING_APPROVAL

A remediation recommendation is available for engineer review.

### 20.4 REMEDIATING

An authorized remediation command is being processed or executed.

### 20.5 VERIFYING_RECOVERY

The platform is evaluating post-remediation device evidence.

### 20.6 RESOLVED

The incident's resolution criteria have been satisfied.

### 20.7 CLOSED

The incident has completed the required operational closure process.

### 20.8 Deferred Recommendations

DEFERRED SHALL be a recommendation review status, not a separate canonical incident status.

The incident MAY remain OPEN or AWAITING_APPROVAL with an explicit deferred-review flag or timestamp.

### 20.9 Rejected Recommendations

A rejected recommendation SHALL NOT close the incident automatically.

The incident SHALL remain available for manual investigation or alternative remediation.

---

## 21. Incident State-Transition Rules

**REQ-INC-005**  
Incident transitions SHALL be validated by deterministic application logic.

**REQ-INC-006**  
AI SHALL NOT directly modify incident lifecycle state.

**REQ-INC-007**  
Engineer approval SHALL be required before device-changing remediation.

**REQ-INC-008**  
A rejected recommendation SHALL NOT trigger a command.

**REQ-INC-009**  
A deferred recommendation SHALL NOT trigger a command.

**REQ-INC-010**  
A command acknowledgement SHALL NOT transition an incident directly to RESOLVED.

**REQ-INC-011**  
An incident SHALL NOT transition to CLOSED before satisfying the defined closure policy.

---

## 22. AI Investigation Integration

The Incident Manager SHALL support asynchronous AI investigation.

### 22.1 Trigger

An incident MAY trigger investigation when:

- It is newly created.
- It meets a configured severity threshold.
- An engineer explicitly requests investigation.

### 22.2 Kafka Event

The Operations Service SHALL publish an `INCIDENT_CREATED` event using SPEC-006.

### 22.3 AIOps Service

The AIOps Service SHALL retrieve evidence through read-only APIs.

The AIOps Service SHALL NOT directly modify:

- Device state.
- Detection rules.
- Alert state.
- Incident authorization.
- Device commands.

### 22.4 Investigation Failure

If AI investigation fails:

- The incident SHALL remain open.
- Existing alerts SHALL remain active.
- Monitoring SHALL continue.
- Engineers SHALL retain manual investigation capability.

### 22.5 Duplicate Triggers

Repeated incident events SHALL NOT create uncontrolled duplicate investigations.

---

## 23. Remediation Recommendation Integration

A completed investigation MAY produce one or more remediation recommendations.

### 23.1 Recommendation Review

The Operations Service SHALL record:

- Recommendation ID.
- Incident ID.
- Proposed command type.
- Target device.
- Rationale.
- Supporting evidence.
- Risk.
- Review status.
- Reviewing engineer.
- Review timestamp.

### 23.2 Approval

Only an authorized engineer SHALL approve a device-changing action.

Approval SHALL be recorded before command authorization.

### 23.3 Rejection

Rejected recommendations SHALL remain in the incident history.

### 23.4 Deferral

Deferred recommendations SHALL remain reviewable.

### 23.5 Security

The AIOps Service SHALL NOT possess command-publishing credentials or device administration tools.

---

## 24. Remediation and Recovery Integration

The Incident Manager SHALL integrate with the authorized command workflow defined in SPEC-010.

### 24.1 Command Execution

Following engineer approval:

1. The Operations Service validates authorization.
2. The Operations Service persists the command.
3. An authorized command event is published.
4. The IoT Gateway publishes the MQTT command.
5. The device returns command results.
6. The Operations Service updates command status.

### 24.2 Recovery Verification

The Operations Service SHALL evaluate post-command telemetry.

Possible outcomes:

```java
public enum RecoveryStatus {
    PENDING,
    RECOVERED,
    NOT_RECOVERED,
    WORSENED,
    INCONCLUSIVE
}
```

### 24.3 Recovery Criteria

For printer degradation, recovery MAY require:

- Printer temperature below the clearing threshold.
- Printer error rate below its clearing threshold.
- Printer subsystem reporting normal status.
- Device connectivity restored or maintained.
- Required persistence period satisfied.

The exact verification window SHALL be defined in SPEC-010.

### 24.4 Resolution

An incident SHALL transition to RESOLVED only after its resolution policy is satisfied.

Successful command execution alone SHALL NOT satisfy this policy.

---

## 25. Alert and Incident Persistence

The Operations Service SHALL persist alerts and incidents in PostgreSQL.

### 25.1 Candidate Tables

```text
detection_rule
detection_state

alert
alert_evidence

incident
incident_alert
incident_history

incident_evidence
```

These names are conceptual and may be refined during database design.

### 25.2 Detection State

The MVP SHOULD persist sufficient detection state to avoid incorrect alert duplication after service restart.

At minimum, the design SHALL explicitly address whether persistence counters and active rule states survive restarts.

### 25.3 Incident History

Incident lifecycle transitions SHALL be auditable.

Candidate history fields:

- historyId.
- incidentId.
- previousStatus.
- newStatus.
- changedAt.
- changedBy.
- reason.

### 25.4 Event Publication

Alert and incident events requiring reliable delivery SHOULD use the transactional outbox described in SPEC-006.

---

## 26. Reliability and Idempotency

### 26.1 Duplicate Telemetry

Repeated delivery of the same logical telemetry message SHALL NOT advance persistence counters repeatedly.

### 26.2 Out-of-Order Telemetry

Older observations SHALL NOT incorrectly replace newer detection state.

### 26.3 Concurrent Alerts

Concurrent qualifying alerts SHALL NOT create duplicate unresolved incidents for the same grouping key.

### 26.4 Service Restart

Active alerts and incidents SHALL remain persisted.

### 26.5 AI Outage

The detection and incident workflow SHALL remain operational.

### 26.6 Database Failure

An incident SHALL NOT be reported as durably created unless its database transaction succeeds.

---

## 27. API Requirements

The Operations Service SHOULD expose REST endpoints for frontend and read-only investigation use.

### 27.1 Candidate Endpoints

```text
GET /api/alerts
GET /api/alerts/{alertId}

POST /api/alerts/{alertId}/acknowledge

GET /api/incidents
GET /api/incidents/{incidentId}

GET /api/incidents/{incidentId}/evidence
GET /api/incidents/{incidentId}/history

POST /api/incidents/{incidentId}/investigations

GET /api/devices/{deviceId}/alerts
GET /api/devices/{deviceId}/incidents
```

### 27.2 API Constraints

Read-only AI tools SHALL use authorized read endpoints.

Engineer-only actions SHALL require authentication and role authorization.

The AIOps Service SHALL NOT receive permission to invoke remediation approval or device-command endpoints.

---

## 28. Frontend Requirements

The React operations console SHALL display:

### 28.1 Alert List

- Device.
- Site.
- Rule.
- Severity.
- Status.
- Detection time.
- Associated incident.

### 28.2 Incident List

- Incident ID.
- Device.
- Site.
- Category.
- Severity.
- Status.
- Opened time.
- Last update.

### 28.3 Incident Details

- Incident summary.
- Related alerts.
- Supporting telemetry.
- Device events.
- Incident timeline.
- AI investigation status.
- AI diagnosis and evidence.
- Remediation recommendation.
- Engineer review controls.
- Command execution history.
- Recovery verification result.

### 28.4 Filtering

The MVP SHOULD support filtering by:

- Site.
- Device.
- Severity.
- Incident status.
- Alert status.

---

## 29. Observability

The Operations Service SHOULD expose metrics for:

- Observations processed.
- Rule evaluations.
- Rules currently firing.
- Active alerts.
- Alerts created.
- Alerts resolved.
- Open incidents.
- Incidents created.
- Incidents resolved.
- Incident correlation count.
- Detection processing failures.
- Detection processing latency.

Structured logs SHOULD include:

```text
deviceId
ruleId
alertId
incidentId
eventId
correlationId
```

Only applicable fields need to be present.

---

## 30. Testing Strategy

### 30.1 Rule Unit Tests

Tests SHALL verify:

- Threshold activation.
- Consecutive-sample persistence.
- Duration-based activation.
- Clearing thresholds.
- Clearing persistence.
- Rule-state transitions.
- Invalid configuration rejection.

### 30.2 Deduplication Tests

Tests SHALL verify:

- Duplicate telemetry does not advance counters twice.
- Repeated abnormal readings update one alert.
- A cleared and reoccurring condition creates the expected alert history.

### 30.3 Connectivity Tests

Tests SHALL verify:

- Initial UNKNOWN connectivity.
- Missing heartbeat timeout.
- OFFLINE health classification.
- Reconnection.
- Preservation of unrelated technical alerts.

### 30.4 Correlation Tests

Tests SHALL verify:

- Printer temperature and error-rate correlation.
- Severity escalation.
- Grouping into one printer incident.
- Prevention of duplicate incidents.

### 30.5 Incident Lifecycle Tests

Tests SHALL verify:

- Incident creation.
- Valid transitions.
- Invalid transition rejection.
- AI investigation trigger.
- AI failure isolation.
- Recommendation rejection.
- Recommendation approval.
- Command execution integration.
- Recovery verification.
- Incident resolution.

### 30.6 Integration Tests

Integration tests SHOULD use PostgreSQL and Kafka through Testcontainers or Docker Compose.

Tests SHOULD validate persistence and outbox publication.

---

## 31. Acceptance Criteria

**AC-007-01**  
A normal telemetry reading does not create an alert.

**AC-007-02**  
A single abnormal reading does not activate a rule requiring three consecutive samples.

**AC-007-03**  
Three consecutive high-temperature readings activate the printer-temperature rule.

**AC-007-04**  
Duplicate telemetry does not incorrectly satisfy persistence requirements.

**AC-007-05**  
Repeated abnormal readings do not create duplicate active alerts.

**AC-007-06**  
Three consecutive recovery readings below the clearing threshold resolve the high-temperature alert.

**AC-007-07**  
A device without accepted communication for more than the configured timeout becomes OFFLINE.

**AC-007-08**  
A reconnecting device returns to CONNECTED without automatically clearing unrelated technical alerts.

**AC-007-09**  
High printer temperature and high printer error rate can be correlated.

**AC-007-10**  
Related printer alerts are grouped into one unresolved printer incident.

**AC-007-11**  
Incident severity can escalate based on a correlated condition.

**AC-007-12**  
A qualifying incident is persisted without invoking AI.

**AC-007-13**  
Incident creation produces a Kafka incident event.

**AC-007-14**  
An incident event can initiate AI investigation.

**AC-007-15**  
Duplicate incident events do not create uncontrolled duplicate investigations.

**AC-007-16**  
AI unavailability does not interrupt detection, alerts, or incidents.

**AC-007-17**  
An engineer can review incident evidence.

**AC-007-18**  
An engineer can approve, reject, or defer a recommendation.

**AC-007-19**  
A rejected recommendation does not trigger a device command.

**AC-007-20**  
An approved recommendation can enter the authorized remediation workflow.

**AC-007-21**  
A successful device-command result does not automatically resolve the incident.

**AC-007-22**  
Subsequent healthy telemetry can satisfy recovery verification criteria.

**AC-007-23**  
The incident transitions to RESOLVED only after its resolution criteria are satisfied.

**AC-007-24**  
Alert and incident state remains available after an Operations Service restart.

**AC-007-25**  
The incident timeline records significant lifecycle changes and remediation outcomes.

---

## 32. Primary End-to-End Demonstration

The principal capstone demonstration SHALL use a simulated kiosk printer degradation scenario.

### Step 1 — Normal Operation

`KIOSK-TAM-003` operates normally.

The dashboard displays:

- Connectivity: CONNECTED.
- Health: HEALTHY.
- Operational state: IDLE or active ordering.
- No active printer incident.

### Step 2 — Progressive Degradation

The simulator publishes increasing printer temperature and error rate.

Example:

```text
Time     Temperature     Error Rate
T+00     51°C            1%
T+05     58°C            4%
T+10     65°C            10%
T+15     72°C            19%
T+20     73°C            20%
T+25     74°C            21%
```

### Step 3 — Deterministic Detection

The high-temperature rule reaches three consecutive abnormal readings.

The Detection Engine creates a HIGH alert.

The high-error-rate rule MAY also fire when its persistence requirement is met.

### Step 4 — Incident Correlation

The Incident Manager groups related printer alerts.

A printer incident is created or updated.

### Step 5 — AI Investigation

The incident triggers the AIOps Service.

The AI retrieves telemetry, device status, logs, historical incidents, and runbooks.

The AI produces a probable diagnosis and remediation recommendation.

### Step 6 — Engineer Review

An engineer reviews the evidence.

The engineer approves `RESTART_PRINTER_SUBSYSTEM`.

### Step 7 — Authorized Remediation

The Operations Service validates approval.

The command is published through Kafka, the IoT Gateway, and MQTT.

The simulated device processes the command.

### Step 8 — Recovery Verification

The simulator publishes improving telemetry.

Example:

```text
Temperature:
74 → 64 → 58 → 54°C

Error Rate:
21% → 8% → 3% → 1%
```

The Operations Service verifies the required recovery conditions.

### Step 9 — Incident Resolution

The incident transitions to RESOLVED.

The incident timeline records:

- Original abnormality.
- Alert creation.
- Correlation.
- AI investigation.
- Recommendation.
- Engineer approval.
- Command execution.
- Recovery verification.
- Final resolution.

---

## 33. Implementation Milestones

### Milestone 1 — Detection Engine

Implement:

- Detection rule definitions.
- Threshold evaluation.
- Consecutive-sample persistence.
- Detection state machine.
- Clearing rules.
- Unit tests.

**Deliverable:** Telemetry can activate and clear deterministic detection conditions.

### Milestone 2 — Alert Management

Implement:

- Alert persistence.
- Alert deduplication.
- Alert evidence.
- Alert acknowledgement.
- Alert resolution.
- Alert REST APIs.

**Deliverable:** Abnormal conditions produce traceable, non-duplicated alerts.

### Milestone 3 — Incident Management

Implement:

- Incident qualification.
- Incident grouping.
- Severity escalation.
- Incident persistence.
- Incident lifecycle.
- Incident history.
- Incident REST APIs.

**Deliverable:** Related alerts become actionable incidents.

### Milestone 4 — Kafka Integration

Implement:

- Alert events.
- Incident events.
- Transactional outbox.
- Duplicate-safe event publication.
- AI investigation triggers.

**Deliverable:** Incidents can initiate asynchronous AI investigations.

### Milestone 5 — Remediation Integration

Implement:

- Recommendation review integration.
- Approval status handling.
- Command lifecycle integration.
- Recovery verification integration.
- Resolution rules.

**Deliverable:** Incidents can progress through human-approved remediation and verified recovery.

### Milestone 6 — Dashboard and Demo

Implement:

- Alert list.
- Incident list.
- Incident details.
- Timeline.
- Evidence display.
- Primary printer degradation demonstration.

**Deliverable:** A complete operational incident workflow visible through the React console.

---

## 34. Open Design Decisions

The following details remain to be finalized:

1. Detection-state persistence implementation.
2. Handling of delayed telemetry and event-time ordering.
3. Exact heartbeat publication interval.
4. Initial device registration grace period.
5. Default alert acknowledgement policy.
6. Incident auto-creation severity threshold.
7. Correlation window evaluation details.
8. Incident severity downgrade policy.
9. Alert evidence retention limits.
10. Incident auto-resolution versus manual closure policy.
11. AI investigation retry and timeout policy.
12. Recovery verification window and required sample count.
13. Concurrent incident creation locking strategy.
14. Incident reopening behaviour after recurrence.
15. Detection-rule configuration storage and versioning.

These decisions SHALL be resolved before implementing the corresponding behaviour.

---

## 35. Final Detection and Incident Principle

Project Comanche SHALL use deterministic software to determine when a device is behaving abnormally.

Alerts SHALL represent confirmed detection conditions.

Incidents SHALL group actionable technical problems and preserve the evidence necessary for investigation.

AI SHALL assist with diagnosis and recommend remediation, but SHALL NOT determine basic alert thresholds or bypass engineer authorization.

A device command SHALL NOT be considered successful remediation until subsequent evidence demonstrates recovery.

**Architectural statement:**

> Deterministic rules detect abnormalities. Alerts record the conditions. Incidents organize the operational response. AI investigates and recommends. Engineers approve remediation. Comanche verifies recovery before resolving incidents.
