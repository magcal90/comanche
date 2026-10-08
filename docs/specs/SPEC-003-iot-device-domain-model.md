# SPEC-003 — IoT Device Domain Model

**Project:** Comanche  
**Description:** AI-Assisted AIOps and Operational Visibility Platform for IoT Devices  
**Reference Organization:** Davy Jones Burger  
**Status:** Draft  
**Version:** 0.2  
**Type:** Domain Model Specification  
**Depends On:** SPEC-001, SPEC-002

---

## 1. Purpose

This specification defines the core IoT device domain model for Project Comanche.

It establishes:

- Organization, site, and device relationships.
- Supported device types.
- Technical health, connectivity, and operational states.
- Device telemetry and operational events.
- Alert and incident domain concepts.
- Diagnostic evidence and remediation recommendations.
- Device commands and recovery outcomes.
- Device availability and utilization metrics.
- Domain invariants and state-transition rules.

The model SHALL support both simulated devices and future physical devices without requiring changes to the core domain.

---

## 2. Domain Boundaries

The Operations Service SHALL own the operational domain model.

The device simulator SHALL maintain its own simulation state and SHALL NOT share in-memory domain objects with the Operations Service.

Communication between the simulator and Comanche SHALL occur through versioned MQTT message contracts.

The domain model SHALL distinguish:

1. **Observed state:** What the device reports.
2. **Derived state:** What Comanche determines from telemetry and monitoring rules.
3. **Authorized action:** What an engineer permits the platform to execute.

AI-generated recommendations SHALL NOT be treated as observed facts or authorized actions.

---

## 3. Core Domain Hierarchy

```text
Organization
    |
    +-- Site
          |
          +-- Device
                |
                +-- Telemetry
                +-- DeviceEvent
                +-- Alert
                +-- Incident
                +-- DeviceCommand
```

An Organization owns multiple Sites.

A Site contains multiple Devices.

Each Device belongs to exactly one Site at a time.

Telemetry, device events, alerts, incidents, and commands SHALL reference their originating or target device.

---

## 4. Organization

An Organization represents the business operating the device estate.

### 4.1 Attributes

| Attribute | Type | Required | Description |
|---|---|---|---|
| organizationId | String | Yes | Unique organization identifier |
| name | String | Yes | Display name |
| status | OrganizationStatus | Yes | Organization lifecycle status |
| createdAt | Instant | Yes | Creation timestamp |
| updatedAt | Instant | Yes | Last modification timestamp |

### 4.2 OrganizationStatus

```java
public enum OrganizationStatus {
    ACTIVE,
    INACTIVE
}
```

### 4.3 Reference Organization

```text
organizationId = DAVY_JONES_BURGER
name = Davy Jones Burger
status = ACTIVE
```

The MVP MAY operate with one configured organization.

The domain SHALL NOT require multi-tenant infrastructure.

---

## 5. Site

A Site represents one restaurant outlet.

### 5.1 Attributes

| Attribute | Type | Required | Description |
|---|---|---|---|
| siteId | String | Yes | Unique site identifier |
| organizationId | String | Yes | Owning organization |
| name | String | Yes | Outlet name |
| timezone | String | Yes | IANA timezone |
| status | SiteStatus | Yes | Site lifecycle status |
| operatingHours | Configuration | Yes | Normal business hours |
| peakPeriods | Collection | No | Named business peak periods |
| createdAt | Instant | Yes | Creation timestamp |
| updatedAt | Instant | Yes | Last modification timestamp |

### 5.2 SiteStatus

```java
public enum SiteStatus {
    ACTIVE,
    TEMPORARILY_CLOSED,
    INACTIVE
}
```

### 5.3 PeakPeriod

A PeakPeriod represents a named operational period.

Candidate attributes:

- periodId
- name
- startLocalTime
- endLocalTime
- applicableDays

Examples:

```text
LUNCH_PEAK
11:30–14:00

DINNER_PEAK
18:00–20:30
```

These periods SHALL be configurable per site.

The system SHALL use the site's configured timezone when interpreting local operating periods.

### 5.4 Site Rules

**REQ-DOM-001**  
A Site SHALL belong to exactly one Organization.

**REQ-DOM-002**  
A Site SHALL have a unique identifier.

**REQ-DOM-003**  
Site operating hours and peak periods SHALL be configuration-driven.

**REQ-DOM-004**  
A temporarily closed Site SHALL NOT automatically be classified as experiencing an IT outage.

---

## 6. Device

A Device represents an individual self-ordering kiosk or POS terminal.

### 6.1 Attributes

| Attribute | Type | Required | Description |
|---|---|---|---|
| deviceId | String | Yes | Unique device identifier |
| siteId | String | Yes | Current site |
| deviceType | DeviceType | Yes | Device category |
| displayName | String | Yes | Human-readable name |
| model | String | No | Hardware/model identifier |
| firmwareVersion | String | No | Firmware version |
| applicationVersion | String | No | Application version |
| lifecycleStatus | DeviceLifecycleStatus | Yes | Administrative lifecycle |
| healthState | HealthState | Yes | Derived technical health |
| connectivityState | ConnectivityState | Yes | Derived connectivity |
| operationalState | OperationalState | Yes | Last known business activity |
| lastSeenAt | Instant | No | Last accepted device communication |
| stateObservedAt | Instant | No | Event time of last accepted state |
| createdAt | Instant | Yes | Registration timestamp |
| updatedAt | Instant | Yes | Last record modification |

### 6.2 DeviceType

```java
public enum DeviceType {
    SELF_ORDER_KIOSK,
    POS_TERMINAL
}
```

### 6.3 DeviceLifecycleStatus

```java
public enum DeviceLifecycleStatus {
    REGISTERED,
    ACTIVE,
    MAINTENANCE,
    RETIRED
}
```

Lifecycle status represents administrative management of the device.

It SHALL NOT be confused with current technical health.

### 6.4 Device Identity

A deviceId SHALL be unique within the Comanche deployment.

Example identifiers:

```text
KIOSK-TAM-001
KIOSK-TAM-002
POS-TAM-001

KIOSK-ORC-001
POS-ORC-001
```

Device identity SHALL remain stable across reconnects and application restarts.

---

## 7. Technical Health State

HealthState represents the technical condition of a device.

```java
public enum HealthState {
    HEALTHY,
    DEGRADED,
    FAILED,
    OFFLINE
}
```

### 7.1 HEALTHY

The device is communicating and has no active health condition that requires degradation or failure classification.

### 7.2 DEGRADED

The device is communicating but has a detected technical abnormality.

The device MAY continue serving customers.

Examples:

- Elevated printer temperature.
- Increasing printer error rate.
- Intermittent peripheral errors.

### 7.3 FAILED

The device is communicating, but a critical technical function is unavailable.

Examples:

- Printer subsystem failure.
- Application failure.
- Critical payment-terminal malfunction.

### 7.4 OFFLINE

The device is not communicating within the configured connectivity timeout.

OFFLINE SHALL describe loss of observability, not a confirmed hardware failure.

### 7.5 Health Derivation

The Operations Service SHALL derive health state using deterministic monitoring rules.

The simulator MAY publish diagnostic status, but SHALL NOT be authoritative for Comanche's final health classification.

The initial precedence SHOULD be:

```text
OFFLINE
   >
FAILED
   >
DEGRADED
   >
HEALTHY
```

OFFLINE takes precedence when connectivity is lost.

The underlying technical conditions SHALL be retained so that reconnecting does not automatically erase previously detected failures.

---

## 8. Connectivity State

ConnectivityState represents whether the device is currently observable.

```java
public enum ConnectivityState {
    CONNECTED,
    DISCONNECTED,
    UNKNOWN
}
```

### 8.1 CONNECTED

Comanche has received acceptable device communication within the configured timeout.

### 8.2 DISCONNECTED

Comanche has not received acceptable device communication within the configured timeout, or has received a trusted disconnection indication.

### 8.3 UNKNOWN

Connectivity has not yet been established or cannot be determined.

### 8.4 Connectivity Rules

**REQ-DOM-005**  
Connectivity SHALL be tracked independently from technical health.

**REQ-DOM-006**  
Missing telemetry or heartbeat SHALL be evaluated by a deterministic timeout rule.

**REQ-DOM-007**  
When connectivity becomes DISCONNECTED, the effective health state SHALL become OFFLINE.

**REQ-DOM-008**  
The last known operational state SHALL be retained for historical reference but SHALL NOT be presented as confirmed current activity while offline.

---

## 9. Operational State

OperationalState represents the business activity currently performed by a device.

```java
public enum OperationalState {
    IDLE,
    TAKING_ORDER,
    PROCESSING_PAYMENT,
    COMPLETING_ORDER,
    OUT_OF_SERVICE
}
```

### 9.1 IDLE

The device is available but is not currently serving a customer.

### 9.2 TAKING_ORDER

A customer is interacting with the device to create an order.

### 9.3 PROCESSING_PAYMENT

The device is processing a payment-related workflow.

No actual payment processing is required in the MVP.

### 9.4 COMPLETING_ORDER

The device is completing the ordering workflow, such as finalizing the order or printing a receipt.

### 9.5 OUT_OF_SERVICE

The device is unavailable for customer-facing operation.

Possible reasons include:

- Technical failure.
- Scheduled maintenance.
- Manual operational suspension.
- Application unavailable.

### 9.6 Normal Kiosk State Transitions

```text
IDLE
  |
  v
TAKING_ORDER
  |
  v
PROCESSING_PAYMENT
  |
  v
COMPLETING_ORDER
  |
  v
IDLE
```

### 9.7 Exceptional Transitions

A device MAY transition to OUT_OF_SERVICE from any active operational state when a critical condition prevents further customer activity.

A device MAY return from OUT_OF_SERVICE to IDLE after recovery or maintenance completion.

A degraded device MAY continue progressing through normal operational states.

### 9.8 State Constraints

**REQ-DOM-009**  
HealthState and OperationalState SHALL be independent concepts.

**REQ-DOM-010**  
A HEALTHY device MAY be IDLE.

**REQ-DOM-011**  
A DEGRADED device MAY continue TAKING_ORDER.

**REQ-DOM-012**  
An OUT_OF_SERVICE device MAY be technically HEALTHY if it is intentionally unavailable.

**REQ-DOM-013**  
An OFFLINE device SHALL NOT be counted as actively serving customers based solely on its stale last-known operational state.

---

## 10. Telemetry

Telemetry represents time-stamped measurements reported by a device.

### 10.1 Telemetry Attributes

| Attribute | Type | Required |
|---|---|---|
| messageId | String | Yes |
| deviceId | String | Yes |
| eventTime | Instant | Yes |
| receivedAt | Instant | Yes |
| schemaVersion | String | Yes |
| metrics | Map | Yes |

### 10.2 Common Metrics

Candidate common metrics:

```text
system.cpu.utilization
system.memory.utilization
system.temperature

network.status
application.status
```

### 10.3 Kiosk Metrics

```text
printer.temperature
printer.errorRate
printer.paperLevel
printer.status

touchscreen.status
```

### 10.4 POS Metrics

```text
paymentTerminal.status
printer.status
printer.paperLevel
application.status
network.status
```

### 10.5 Metric Definitions

Each metric SHALL have a defined:

- Name.
- Value type.
- Unit, where applicable.
- Valid range or enumeration.
- Device applicability.
- Sampling or reporting expectation.

For example:

| Metric | Type | Unit | Expected Range |
|---|---|---|---|
| printer.temperature | Decimal | °C | Device-specific |
| printer.errorRate | Decimal | Ratio | 0.0–1.0 |
| printer.paperLevel | Decimal | Percent | 0–100 |
| system.cpu.utilization | Decimal | Percent | 0–100 |
| system.memory.utilization | Decimal | Percent | 0–100 |

A printer error rate of 0.19 represents 19%.

### 10.6 Telemetry Rules

**REQ-DOM-014**  
Telemetry SHALL include device identity and event time.

**REQ-DOM-015**  
The platform SHALL distinguish event time from ingestion time.

**REQ-DOM-016**  
Duplicate messages SHALL NOT result in duplicate logical processing.

**REQ-DOM-017**  
Stale telemetry SHALL NOT overwrite newer accepted device state.

**REQ-DOM-018**  
Invalid telemetry SHALL be rejected or quarantined according to the ingestion contract.

---

## 11. Device Events

A DeviceEvent represents a meaningful occurrence reported by a device.

### 11.1 Attributes

- eventId
- deviceId
- eventType
- eventTime
- receivedAt
- severity
- attributes

### 11.2 Candidate Event Types

```java
public enum DeviceEventType {
    APPLICATION_STARTED,
    APPLICATION_STOPPED,

    PRINTER_ERROR,
    PRINTER_RECOVERED,
    PAPER_LOW,
    PAPER_EMPTY,

    NETWORK_DISCONNECTED,
    NETWORK_RESTORED,

    ORDER_SESSION_STARTED,
    ORDER_SESSION_COMPLETED,

    PAYMENT_TERMINAL_UNAVAILABLE
}
```

### 11.3 Event Semantics

A DeviceEvent is an observation.

It SHALL NOT automatically imply that an incident must be created.

The Operations Service SHALL evaluate relevant events through deterministic detection rules.

---

## 12. Detection Rules

A DetectionRule defines a deterministic condition used to identify abnormalities.

### 12.1 Candidate Attributes

- ruleId
- ruleType
- applicableDeviceTypes
- metricNames
- condition
- threshold
- persistenceRequirement
- severity
- enabled

### 12.2 Rule Types

Candidate types:

```java
public enum DetectionRuleType {
    THRESHOLD,
    PERSISTENCE,
    MULTI_SIGNAL,
    CONNECTIVITY_TIMEOUT,
    TREND
}
```

### 12.3 Example Rule

```text
Rule:
PRINTER_HIGH_TEMPERATURE

Condition:
printer.temperature >= 70°C

Persistence:
3 consecutive readings

Severity:
HIGH
```

### 12.4 Detection Rules

**REQ-DOM-019**  
Abnormality detection SHALL be deterministic.

**REQ-DOM-020**  
Detection rules SHALL NOT require LLM inference.

**REQ-DOM-021**  
Thresholds and persistence conditions SHALL be configurable.

**REQ-DOM-022**  
Repeated abnormal readings SHALL NOT create uncontrolled duplicate alerts.

Detailed detection semantics SHALL be defined in SPEC-007.

---

## 13. Alert

An Alert represents a detected abnormal condition.

### 13.1 Attributes

| Attribute | Type | Required |
|---|---|---|
| alertId | String | Yes |
| deviceId | String | Yes |
| ruleId | String | Yes |
| severity | AlertSeverity | Yes |
| status | AlertStatus | Yes |
| firstDetectedAt | Instant | Yes |
| lastDetectedAt | Instant | Yes |
| resolvedAt | Instant | No |
| evidence | Collection | Yes |

### 13.2 AlertSeverity

```java
public enum AlertSeverity {
    INFO,
    WARNING,
    HIGH,
    CRITICAL
}
```

### 13.3 AlertStatus

```java
public enum AlertStatus {
    ACTIVE,
    ACKNOWLEDGED,
    RESOLVED
}
```

### 13.4 Alert Rules

An alert SHALL reference its triggering detection rule.

An alert SHOULD preserve supporting evidence.

An unresolved alert SHOULD be updated rather than duplicated when the same condition remains active.

An alert MAY be associated with an incident.

---

## 14. Incident

An Incident represents an operational issue requiring investigation or remediation.

### 14.1 Attributes

- incidentId
- deviceId
- siteId
- severity
- status
- openedAt
- updatedAt
- resolvedAt
- alerts
- evidence
- investigationReport
- recommendations
- recoveryStatus

### 14.2 IncidentStatus

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

### 14.3 Incident Rules

**REQ-DOM-023**  
An Incident SHALL reference the affected device.

**REQ-DOM-024**  
An Incident MAY contain multiple related alerts.

**REQ-DOM-025**  
An Incident SHALL preserve relevant diagnostic evidence.

**REQ-DOM-026**  
An Incident SHALL NOT be resolved solely because a remediation command was acknowledged.

**REQ-DOM-027**  
Incident resolution SHALL require the defined resolution criteria to be satisfied.

---

## 15. Diagnostic Evidence

DiagnosticEvidence represents information used to investigate an incident.

### 15.1 Candidate Attributes

- evidenceId
- incidentId
- sourceType
- sourceReference
- collectedAt
- observedAt
- summary
- content

### 15.2 EvidenceSourceType

```java
public enum EvidenceSourceType {
    TELEMETRY,
    DEVICE_EVENT,
    DEVICE_LOG,
    DEVICE_CONFIGURATION,
    HISTORICAL_INCIDENT,
    RUNBOOK,
    ENGINEERING_ISSUE
}
```

### 15.3 Evidence Rules

Evidence SHALL retain sufficient source information for engineer review.

AI-generated explanations SHALL NOT replace the original evidence.

The system SHOULD distinguish observed evidence from AI interpretation.

---

## 16. AI Investigation Report

An InvestigationReport represents the output of an AI-assisted investigation.

### 16.1 Candidate Attributes

- investigationId
- incidentId
- investigatedAt
- probableCause
- reasoningSummary
- confidence
- evidenceReferences
- relatedIncidentReferences
- runbookReferences
- recommendedActions
- modelProvider
- modelIdentifier

### 16.2 Investigation Rules

**REQ-DOM-028**  
Investigation reports SHALL reference the incident being investigated.

**REQ-DOM-029**  
AI-generated diagnoses SHALL be presented as assessments, not verified facts.

**REQ-DOM-030**  
Recommendations SHALL reference supporting evidence where available.

**REQ-DOM-031**  
Model-generated confidence SHALL NOT be presented as a calibrated statistical probability unless independently validated.

---

## 17. Remediation Recommendation

A RemediationRecommendation represents a proposed corrective action.

It SHALL NOT represent authorization to execute the action.

### 17.1 Candidate Attributes

- recommendationId
- incidentId
- investigationId
- actionType
- targetDeviceId
- rationale
- evidenceReferences
- riskDescription
- reviewStatus
- reviewedBy
- reviewedAt

### 17.2 RecommendationReviewStatus

```java
public enum RecommendationReviewStatus {
    AWAITING_REVIEW,
    APPROVED,
    REJECTED,
    DEFERRED
}
```

### 17.3 Recommendation Rules

**REQ-DOM-032**  
A recommendation SHALL NOT directly initiate a device command.

**REQ-DOM-033**  
Only an authorized engineer MAY approve a device-changing recommendation.

**REQ-DOM-034**  
Approval SHALL be recorded with engineer identity and timestamp.

**REQ-DOM-035**  
The deterministic command subsystem SHALL validate an approved action before execution.

---

## 18. Device Command

A DeviceCommand represents a controlled action intended for a device.

### 18.1 Candidate Attributes

- commandId
- deviceId
- incidentId
- recommendationId
- actionType
- parameters
- authorizedBy
- authorizedAt
- createdAt
- dispatchedAt
- acknowledgedAt
- status

### 18.2 CommandStatus

```java
public enum CommandStatus {
    PENDING,
    AUTHORIZED,
    DISPATCHED,
    ACKNOWLEDGED,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELLED
}
```

### 18.3 Candidate Action Types

The MVP MAY support a limited allowlist:

```java
public enum DeviceCommandType {
    RESTART_PRINTER_SUBSYSTEM,
    RESTART_APPLICATION
}
```

Additional commands SHALL require explicit specification and authorization rules.

### 18.4 Command Rules

**REQ-DOM-036**  
Every device-changing command SHALL have a unique commandId.

**REQ-DOM-037**  
Every device-changing command SHALL have an auditable authorization record.

**REQ-DOM-038**  
Commands SHALL be restricted to an allowlist of supported actions.

**REQ-DOM-039**  
Command acknowledgements SHALL NOT automatically resolve incidents.

**REQ-DOM-040**  
Duplicate command delivery SHALL be handled safely using command identity and device-side deduplication where supported.

---

## 19. Recovery Verification

RecoveryVerification represents the platform's assessment after remediation.

### 19.1 RecoveryStatus

```java
public enum RecoveryStatus {
    PENDING,
    RECOVERED,
    NOT_RECOVERED,
    WORSENED,
    INCONCLUSIVE
}
```

### 19.2 Candidate Attributes

- verificationId
- incidentId
- commandId
- startedAt
- completedAt
- status
- evaluatedMetrics
- evidenceReferences
- explanation

### 19.3 Recovery Rules

**REQ-DOM-041**  
Recovery SHALL be evaluated using subsequent telemetry or trusted device-state observations.

**REQ-DOM-042**  
Successful command execution SHALL NOT automatically imply recovery.

**REQ-DOM-043**  
Missing post-remediation telemetry SHALL NOT be treated as successful recovery.

**REQ-DOM-044**  
The recovery outcome SHALL be recorded against the incident.

---

## 20. Service Ticket

A ServiceTicket represents a persistent operational record associated with an incident.

### 20.1 Candidate Attributes

- ticketId
- incidentId
- deviceId
- siteId
- title
- description
- severity
- status
- createdAt
- updatedAt
- closedAt
- investigationSummary
- recommendedRemediation
- actionsAttempted
- recoveryOutcome

### 20.2 TicketStatus

```java
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    PENDING_APPROVAL,
    RESOLVED,
    CLOSED
}
```

### 20.3 Ticket Rules

A ticket MAY reference multiple remediation attempts.

A ticket SHALL retain the outcome of each attempted remediation.

External Jira integration is optional.

---

## 21. Device Availability

Device availability represents the proportion of eligible operating time during which a device is available for intended use.

### 21.1 Availability Formula

```text
Availability (%) =
    Available Operating Duration
    ---------------------------- × 100
    Eligible Operating Duration
```

### 21.2 Eligibility

The denominator SHOULD include scheduled operating time.

Periods when the Site is closed SHOULD be excluded.

Scheduled maintenance MAY be excluded according to an explicit, consistent policy.

### 21.3 Availability Rules

**REQ-DOM-045**  
Availability SHALL be time-based rather than derived solely from a current device count.

**REQ-DOM-046**  
OFFLINE and OUT_OF_SERVICE devices SHALL NOT count as available during the affected interval.

**REQ-DOM-047**  
The handling of DEGRADED devices SHALL be configurable or explicitly documented.

**REQ-DOM-048**  
Availability calculations SHALL use a consistent denominator for comparison across sites.

---

## 22. Device Utilization

Device utilization represents the proportion of available operating time spent actively serving customers.

### 22.1 Active Operational States

For the MVP, the following states SHALL count as active:

```text
TAKING_ORDER
PROCESSING_PAYMENT
COMPLETING_ORDER
```

The following states SHALL NOT count as active:

```text
IDLE
OUT_OF_SERVICE
```

### 22.2 Utilization Formula

```text
Utilization (%) =
    Active Customer-Service Duration
    -------------------------------- × 100
    Available Operating Duration
```

### 22.3 Example

Suppose a kiosk has:

```text
Available operating duration: 120 minutes

TAKING_ORDER:       40 minutes
PROCESSING_PAYMENT: 10 minutes
COMPLETING_ORDER:   10 minutes
IDLE:               60 minutes
```

Then:

```text
Active duration = 60 minutes

Utilization = 60 / 120 × 100

Utilization = 50%
```

### 22.4 Utilization Rules

**REQ-DOM-049**  
Utilization SHALL be calculated from operational-state durations.

**REQ-DOM-050**  
A current snapshot of active versus idle devices SHALL NOT be represented as historical utilization.

**REQ-DOM-051**  
Offline or out-of-service intervals SHALL NOT be treated as active customer-service time.

**REQ-DOM-052**  
Utilization SHALL support aggregation by device, site, and configured peak period.

**REQ-DOM-053**  
When available operating duration is zero, utilization SHALL be reported as unavailable rather than dividing by zero.

### 22.5 Interpretation

High utilization does not necessarily indicate healthy operation.

Low utilization does not necessarily indicate a technical failure.

Management insights and technical alerts SHALL remain distinct.

---

## 23. Fleet and Site Aggregations

The Operations Service SHALL support aggregations for:

- Total registered devices.
- Active devices.
- Connected devices.
- Offline devices.
- Healthy devices.
- Degraded devices.
- Failed devices.
- Out-of-service devices.
- Kiosks taking orders.
- Idle kiosks.
- Site availability.
- Fleet availability.
- Site utilization.
- Peak-period utilization.
- Active incidents.

Aggregations SHALL distinguish current-state counts from time-based historical metrics.

---

## 24. Temporal Model

All persisted event timestamps SHALL use an unambiguous time representation, preferably UTC instants.

Site operating hours SHALL use the configured local timezone.

The platform SHALL distinguish:

- Event occurrence time.
- Message receipt time.
- Processing time.
- Incident detection time.
- Command authorization time.
- Command dispatch time.
- Recovery verification time.

The system SHALL NOT assume that all messages arrive in event-time order.

For the MVP, historical utilization SHOULD be derived from accepted operational-state transitions and their effective time intervals.

Detailed late-event correction behaviour SHALL be specified before implementation.

---

## 25. Domain Invariants

The following invariants SHALL hold:

1. Every Device belongs to exactly one Site at a time.
2. Every Site belongs to exactly one Organization.
3. Device identity remains stable across reconnects.
4. Technical health is independent of operational activity.
5. Connectivity is tracked independently.
6. Stale operational state is not counted as confirmed current activity.
7. Alerts reference detection rules.
8. Incidents reference affected devices.
9. Investigation reports reference incidents.
10. AI recommendations do not authorize commands.
11. Device-changing commands require engineer authorization.
12. Commands use allowlisted action types.
13. Recovery is verified independently of command acknowledgement.
14. Utilization is based on duration, not snapshot counts.
15. Operational insights are distinct from technical alerts.

---

## 26. Acceptance Criteria

**AC-003-01**  
A Device can be registered under a Site belonging to an Organization.

**AC-003-02**  
The platform supports SELF_ORDER_KIOSK and POS_TERMINAL.

**AC-003-03**  
A HEALTHY kiosk can be IDLE.

**AC-003-04**  
A DEGRADED kiosk can continue TAKING_ORDER.

**AC-003-05**  
An intentionally unavailable device can be OUT_OF_SERVICE without a confirmed technical failure.

**AC-003-06**  
A connectivity timeout can cause the effective health state to become OFFLINE.

**AC-003-07**  
Stale operational state is not counted as confirmed live customer activity.

**AC-003-08**  
Telemetry records contain device identity and event time.

**AC-003-09**  
Duplicate telemetry does not create duplicate logical state transitions.

**AC-003-10**  
A detection rule can produce an alert with supporting evidence.

**AC-003-11**  
Multiple related alerts can be associated with one incident.

**AC-003-12**  
An AI investigation report can reference telemetry, logs, incidents, and runbooks.

**AC-003-13**  
A remediation recommendation cannot directly issue a device command.

**AC-003-14**  
An approved recommendation can produce an authorized command record.

**AC-003-15**  
A command acknowledgement does not automatically mark the incident as recovered.

**AC-003-16**  
Recovery can be determined from subsequent telemetry.

**AC-003-17**  
Device utilization can be calculated from operational-state durations.

**AC-003-18**  
Site-level utilization can be calculated for lunch and dinner periods.

**AC-003-19**  
A period with zero available operating duration does not produce an invalid utilization value.

**AC-003-20**  
Management can distinguish current device counts from historical availability and utilization.

---

## 27. Open Design Decisions

The following details remain for subsequent specifications:

1. MQTT telemetry payload schemas — SPEC-005.
2. Event ordering and duplicate handling — SPEC-006.
3. Exact abnormality thresholds and persistence — SPEC-007.
4. Incident correlation and deduplication — SPEC-007.
5. AI investigation contracts — SPEC-008.
6. Remediation command execution and verification — SPEC-010.
7. Database schema and migration strategy — implementation design.
8. Telemetry retention policy — implementation design.
9. Historical utilization interval correction — metrics implementation design.
10. Authentication and authorization — security specification.

---

## 28. Final Domain Principle

Project Comanche SHALL maintain a clear distinction between:

**What a device is doing** — OperationalState.

**Whether the device is technically healthy** — HealthState.

**Whether the device is communicating** — ConnectivityState.

**What the platform detected** — Alert and Incident.

**What AI believes may be wrong** — InvestigationReport.

**What AI recommends** — RemediationRecommendation.

**What an engineer authorizes** — RemediationApproval.

**What the platform executes** — DeviceCommand.

**What subsequent evidence proves** — RecoveryVerification.

These concepts SHALL remain separate throughout the implementation.
